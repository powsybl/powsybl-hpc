/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.commons.io.WorkingDirectory;
import com.powsybl.computation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.powsybl.computation.slurm.SlurmConstants.BATCH_EXT;

/**
 * This class contains those job ids relationship in Slurm platform for one task.
 * It has a correspondent working directory and the CompletableFuture as return value.
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmTaskImpl extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlurmTaskImpl.class);

    private Long firstJobId;
    private List<Long> masters;
    private Map<Long, SubTask> subTaskMap;
    private Long currentMaster;

    SlurmTaskImpl(SlurmComputationManager scm, WorkingDirectory directory,
                  List<CommandExecution> executions, ComputationParameters parameters, ExecutionEnvironment environment) {
        super(scm, directory, executions, parameters, environment);
    }

    @Override
    public void submit() throws IOException {
        if (!canSubmit()) {
            return;
        }

        commandByJobId = new HashMap<>();
        outerSendingLoop:
        for (int commandIdx = 0; commandIdx < executions.size(); commandIdx++) {
            CommandExecution commandExecution = executions.get(commandIdx);
            Command command = commandExecution.getCommand();
            SbatchCmd cmd;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Executing {} command {} in working directory {}", command.getType(), command, workingDir);
            }

            // a master job to copy NonExecutionDependent and PreProcess needed input files
            if (command.getInputFiles().stream()
                    .anyMatch(inputFile -> !inputFile.dependsOnExecutionNumber() && inputFile.getPreProcessor() != null)) {
                if (!canSubmit()) {
                    break outerSendingLoop;
                }
                SbatchScriptGenerator sbatchScriptGenerator = new SbatchScriptGenerator(flagDir);
                List<String> shell = sbatchScriptGenerator.unzipCommonInputFiles(command);
                copyShellToRemoteWorkingDir(shell, UNZIP_INPUTS_COMMAND_ID + "_" + commandIdx);
                cmd = buildSbatchCmd(UNZIP_INPUTS_COMMAND_ID, commandIdx, getPreJobIds(), parameters);
                long jobId = launchSbatch(cmd);
                newCommonUnzipJob(jobId);
            }

            // no job array --> commandId_index.batch
            for (int executionIndex = 0; executionIndex < commandExecution.getExecutionCount(); executionIndex++) {
                if (!canSubmit()) {
                    break outerSendingLoop;
                }
                prepareBatch(command, executionIndex, commandExecution);
                cmd = buildSbatchCmd(command.getId(), executionIndex, getPreJobIds(), parameters);
                long jobId = launchSbatch(cmd);
                newBatch(jobId);
            }

            commandByJobId.put(currentMaster, command);
            // finish binding batches
            setCurrentMasterNull();
        }

        binding();
    }

    private SbatchCmd buildSbatchCmd(String commandId, int executionIndex, List<Long> preJobIds, ComputationParameters baseParams) {
        // prepare sbatch cmd
        String baseFileName = workingDir.resolve(commandId).toAbsolutePath().toString();
        SbatchCmdBuilder builder = new SbatchCmdBuilder()
                .script(baseFileName + "_" + executionIndex + BATCH_EXT)
                .jobName(commandId)
                .workDir(workingDir)
                .nodes(1)
                .ntasks(1)
                .oversubscribe()
                .output(baseFileName + "_" + executionIndex + SlurmConstants.OUT_EXT)
                .error(baseFileName + "_" + executionIndex + SlurmConstants.ERR_EXT);
        if (!preJobIds.isEmpty()) {
            builder.aftercorr(preJobIds);
        }
        addParameters(builder, commandId);
        return builder.build();
    }

    private void prepareBatch(Command command, int executionIndex, CommandExecution commandExecution) throws IOException {
        // prepare sbatch script from command
        Map<String, String> executionVariables = CommandExecution.getExecutionVariables(environment.getVariables(), commandExecution);
        SbatchScriptGenerator scriptGenerator = new SbatchScriptGenerator(flagDir);
        List<String> shell = scriptGenerator.parser(command, executionIndex, workingDir, executionVariables);
        if (executionIndex == -1) {
            // array job not used yet
            copyShellToRemoteWorkingDir(shell, command.getId());
        } else {
            copyShellToRemoteWorkingDir(shell, command.getId() + "_" + executionIndex);
        }
    }

    SlurmExecutionReport generateReport() {
        List<ExecutionError> errors = new ArrayList<>();

        foo(getAllJobIds(), line -> {
            Matcher m = DIGITAL_PATTERN.matcher(line);
            m.find();
            long jobId = Long.parseLong(m.group());
            m.find();
            int exitCode = Integer.parseInt(m.group());
            long mid = jobId;
            int executionIdx = 0;
            if (!commandByJobId.containsKey(jobId)) {
                mid = getMasterId(jobId);
                executionIdx = (int) (jobId - mid);
            }
            // error message ???
            ExecutionError error = new ExecutionError(commandByJobId.get(mid), executionIdx, exitCode);
            errors.add(error);
            LOGGER.debug("{} error added ", error);
        });

        return new SlurmExecutionReport(errors, workingDir);
    }

    /**
     * Returns all job ids in slurm for this task. It contains batch ids if array_job is not used.
     * For array jobs can be cancelled just by calling on master jobId
     * but currently array_job in slurm is not used, so jobs should be cancelled one by one.
     */
    Set<Long> getAllJobIds() {
        if (masters == null || masters.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> set = new HashSet<>();
        set.addAll(masters);
        Set<Long> subIds = getMasters().stream().flatMap(mId -> subTaskMap.get(mId).getBatchStream())
                .collect(Collectors.toSet());
        set.addAll(subIds);
        return set;
    }

    Long getFirstJobId() {
        return firstJobId;
    }

    Long getMasterId(Long batchId) {
        for (Map.Entry<Long, SubTask> entry : subTaskMap.entrySet()) {
            boolean contains = entry.getValue().batchIds.contains(batchId);
            if (contains) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public SlurmExecutionReport await() throws InterruptedException, ExecutionException {
        taskCompletion.get();
        return generateReport();
    }

    /**
     * If first job id is null, this masterId would take account as first job id.
     * @param masterId
     */
    private void newMaster(Long masterId) {
        setFirstJobIfIsNull(masterId);
        currentMaster = masterId;
    }

    /**
     * A CommonUnzipJob is always a master job, but never be a current master.
     * @param masterId
     */
    private void newCommonUnzipJob(Long masterId) {
        Objects.requireNonNull(masterId);
        jobs.add(new CompletableMonitoredJob(masterId, false));
        LOGGER.debug("tracing common unzip job:{}", masterId);
        setFirstJobIfIsNull(masterId);
        currentMaster = null;
    }

    // TODO rename to dependent jobs
    List<Long> getPreJobIds() {
        if (masters == null) {
            return Collections.emptyList();
        }
        int preMasterIdOffset = currentMaster == null ? 1 : 2;
        if (masters.size() == 1 && preMasterIdOffset == 2) {
            return Collections.emptyList();
        }
        Long preMasterId = masters.get(masters.size() - preMasterIdOffset);
        List<Long> preJobIds = new ArrayList<>();
        preJobIds.add(preMasterId);
        preJobIds.addAll(subTaskMap.get(preMasterId).batchIds);
        return preJobIds;
    }

    private void setFirstJobIfIsNull(Long masterId) {
        Objects.requireNonNull(masterId);
        if (firstJobId == null) {
            initCollections(masterId);
            subTaskMap = new HashMap<>();
        } else {
            Long preMasterId = masters.get(masters.size() - 1);
            LOGGER.debug("DependencyId: {} -> {}", preMasterId, masterId);
        }
        newMasterInCollections(masterId);
    }

    private void initCollections(Long masterId) {
        firstJobId = masterId;
        masters = new ArrayList<>();
        LOGGER.debug("First jobId : {}", firstJobId);
    }

    private void newMasterInCollections(Long masterId) {
        masters.add(masterId);
        subTaskMap.put(masterId, new SubTask(masterId));
    }

    /**
     * The batchId could be a batchId if currentMaster is null.
     * @param batchId
     */
    private void newBatch(Long batchId) {
        Objects.requireNonNull(batchId);
        jobs.add(new CompletableMonitoredJob(batchId));
        LOGGER.debug("tracing job:{}", batchId);
        if (masters == null || currentMaster == null) {
            newMaster(batchId);
        } else {
            subTaskMap.get(currentMaster).add(batchId);
        }
    }

    private void setCurrentMasterNull() {
        // make current master to null, and wait to be set
        currentMaster = null;
    }

    @Override
    public void interrupt() {
        taskCompletion.cancel(true);
        cancelSubmittedJobs();
    }

    // ===============================
    // ==== for unit test methods ====
    // ===============================
    List<Long> getMasters() {
        return masters;
    }

    List<Long> getBatches(Long masterId) {
        return subTaskMap.get(masterId).batchIds;
    }

    int getCommandExecutionSize() {
        return executions.size();
    }

    CommandExecution getCommandExecution(int i) {
        return executions.get(i);
    }

    private static final class SubTask {

        private Long masterId;
        private List<Long> batchIds;

        private SubTask(Long masterId) {
            this.masterId = Objects.requireNonNull(masterId);
            batchIds = new ArrayList<>();
        }

        private boolean add(Long batchId) {
            LOGGER.debug("batchIds: {} -> {}", masterId, batchId);
            return batchIds.add(batchId);
        }

        private Stream<Long> getBatchStream() {
            return batchIds.stream();
        }

        @Override
        public String toString() {
            return "SubTask{" +
                    "masterId=" + masterId +
                    ", batchIds=" + batchIds +
                    '}';
        }
    }

}
