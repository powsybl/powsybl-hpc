/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.commons.io.WorkingDirectory;
import com.powsybl.computation.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.powsybl.computation.slurm.SlurmConstants.BATCH_EXT;

/**
 * This class contains those job ids relationship in Slurm platform for one task.
 * It has a correspondent working directory and the CompletableFuture as return value.
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlurmTask.class);

    private static final String UNZIP_INPUTS_COMMAND_ID = "unzip_inputs_command";
    private static final String CLOSE_START_NO_MORE_SEND_INFO = "SCM close started and no more send sbatch to slurm";
    private static final String SACCT_NONZERO_JOB = "sacct --jobs=%s -n --format=\"jobid,exitcode\" | grep -v \"0:0\" | grep -v \"\\.\"";
    private static final Pattern DIGITAL_PATTERN = Pattern.compile("\\d+");

    private UUID callableId;
    private WorkingDirectory directory;
    private Path workingDir;
    private SlurmComputationManager scm;
    private Path flagDir;
    private CommandExecutor commandExecutor;
    private List<CommandExecution> executions;
    private ComputationParameters parameters;
    private ExecutionEnvironment environment;
    private TaskCounter counter;

    private Map<Long, Command> commandByJobId;

    private Long firstJobId;
    private List<Long> masters;
    private Map<Long, SubTask> subTaskMap;
    private Long currentMaster;

    private Set<Long> tracingIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private volatile boolean cancel = false;

    SlurmTask(UUID callableId, SlurmComputationManager scm, WorkingDirectory directory,
              List<CommandExecution> executions, ComputationParameters parameters, ExecutionEnvironment environment) {
        this.callableId = Objects.requireNonNull(callableId);
        this.scm = Objects.requireNonNull(scm);
        this.commandExecutor = Objects.requireNonNull(scm.getCommandRunner());
        this.directory = Objects.requireNonNull(directory);
        this.workingDir = Objects.requireNonNull(directory.toPath());
        this.flagDir = Objects.requireNonNull(scm.getFlagDir());
        this.executions = Objects.requireNonNull(executions);
        this.parameters = Objects.requireNonNull(parameters);
        this.environment = Objects.requireNonNull(environment);
        int sum = executions.stream().mapToInt(CommandExecution::getExecutionCount).sum();
        this.counter = new TaskCounter(sum);
    }

    void submit() throws IOException, InterruptedException {
        commandByJobId = new HashMap<>();
        outerSendingLoop:
        for (int commandIdx = 0; commandIdx < executions.size(); commandIdx++) {
            checkCancelledDuringSubmitting();
            CommandExecution commandExecution = executions.get(commandIdx);
            Command command = commandExecution.getCommand();
            SbatchCmd cmd;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Executing {} command {} in working directory {}", command.getType(), command, workingDir);
            }

            // a master job to copy NonExecutionDependent and PreProcess needed input files
            if (command.getInputFiles().stream()
                    .anyMatch(inputFile -> !inputFile.dependsOnExecutionNumber() && inputFile.getPreProcessor() != null)) {
                if (scm.isCloseStarted()) {
                    LOGGER.info(CLOSE_START_NO_MORE_SEND_INFO);
                    break outerSendingLoop;
                }
                checkCancelledDuringSubmitting();
                SbatchScriptGenerator sbatchScriptGenerator = new SbatchScriptGenerator(flagDir);
                List<String> shell = sbatchScriptGenerator.unzipCommonInputFiles(command);
                copyShellToRemoteWorkingDir(shell, UNZIP_INPUTS_COMMAND_ID + "_" + commandIdx);
                cmd = buildSbatchCmd(UNZIP_INPUTS_COMMAND_ID, commandIdx, getPreJobIds(), parameters);
                long jobId = launchSbatch(cmd);
                newCommonUnzipJob(jobId);
            }

            // no job array --> commandId_index.batch
            for (int executionIndex = 0; executionIndex < commandExecution.getExecutionCount(); executionIndex++) {
                if (scm.isCloseStarted()) {
                    LOGGER.info(CLOSE_START_NO_MORE_SEND_INFO);
                    break outerSendingLoop;
                }
                checkCancelledDuringSubmitting();
                prepareBatch(command, executionIndex, commandExecution);
                cmd = buildSbatchCmd(command.getId(), executionIndex, getPreJobIds(), parameters);
                long jobId = launchSbatch(cmd);
                newBatch(jobId);
            }

            commandByJobId.put(currentMaster, command);
            // finish binding batches
            setCurrentMasterNull();
        }
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
        SlurmComputationParameters extension = baseParams.getExtension(SlurmComputationParameters.class);
        if (extension != null) {
            extension.getQos().ifPresent(builder::qos);
        }
        baseParams.getDeadline(commandId).ifPresent(builder::deadline);
        baseParams.getTimeout(commandId).ifPresent(builder::timeout);
        return builder.build();
    }

    private long launchSbatch(SbatchCmd cmd) {
        try {
            SbatchCmdResult sbatchResult = cmd.send(commandExecutor);
            long submittedJobId = sbatchResult.getSubmittedJobId();
            LOGGER.debug("Submitted: {}, with jobId:{}", cmd, submittedJobId);
            return submittedJobId;
        } catch (SlurmCmdNonZeroException e) {
            throw new SlurmException(e);
        }
    }

    // We still need to check the cancel flag in task, because the interrupted exception is not re-thrown
    // in underlying jsch library.
    private void checkCancelledDuringSubmitting() throws InterruptedException {
        if (isCancel()) {
            LOGGER.debug("cancelled during submitting");
            throw new InterruptedException("cancelled during submitting");
        }
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

    private void copyShellToRemoteWorkingDir(List<String> shell, String batchName) throws IOException {
        StringBuilder sb = new StringBuilder();
        shell.forEach(line -> sb.append(line).append('\n'));
        String str = sb.toString();
        Path batch;
        batch = workingDir.resolve(batchName + BATCH_EXT);
        try (InputStream targetStream = new ByteArrayInputStream(str.getBytes())) {
            Files.copy(targetStream, batch);
        }
    }

    SlurmExecutionReport generateReport() {
        List<ExecutionError> errors = new ArrayList<>();

        Set<Long> jobIds = commandByJobId.keySet();
        String jobIdsStr = StringUtils.join(jobIds, ",");
        String sacct = String.format(SACCT_NONZERO_JOB, jobIdsStr);
        CommandResult sacctResult = commandExecutor.execute(sacct);
        String sacctOutput = sacctResult.getStdOut();
        if (sacctOutput.length() > 0) {
            String[] lines = sacctOutput.split("\n");
            for (String line : lines) {
                Matcher m = DIGITAL_PATTERN.matcher(line);
                m.find();
                long jobId = Long.parseLong(m.group());
                m.find();
                int executionIdx = Integer.parseInt(m.group());
                m.find();
                int exitCode = Integer.parseInt(m.group());
                // error message ???
                ExecutionError error = new ExecutionError(commandByJobId.get(jobId), executionIdx, exitCode);
                errors.add(error);
                LOGGER.debug("{} error added ", jobId);
            }
        }

        return new SlurmExecutionReport(errors, workingDir);
    }

    /**
     * The working directory and task is a one-to-one relationship.
     * So it returns the directory name as ID.
     * @return Returns working directory name as ID.
     */
    String getId() {
        return directory.toPath().getFileName().toString();
    }

    UUID getCallableId() {
        return callableId;
    }

    Path getWorkingDirPath() {
        return directory.toPath();
    }

    /**
     * Returns the all ids.
     * For array jobs can be cancelled just by calling on master jobId
     * but currently array_job in slurm is not used, so jobs should be cancelled one by one.
     */
    Set<Long> getToCancelIds() {
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

    TaskCounter getCounter() {
        return counter;
    }

    void await() throws InterruptedException {
        counter.await();
    }

    int getJobCount() {
        return counter.getJobCount();
    }

    boolean contains(Long id) {
        if (firstJobId > id) {
            return false;
        }
        boolean containsInMaster = masters.stream().anyMatch(l -> l.equals(id));
        if (containsInMaster) {
            return true;
        }
        return subTaskMap.values().stream().flatMap(SubTask::getBatchStream).anyMatch(l -> l.equals(id));
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
        tracingIds.add(masterId);
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
        tracingIds.add(batchId);
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

    // methods used in task store
    boolean untracing(long id) {
        return tracingIds.remove(id);
    }

    Set<Long> getTracingIds() {
        return tracingIds;
    }

    void error() {
        LOGGER.debug("Error detected.");
        cancel();
        getCounter().cancel();
    }

    void cancel() {
        cancel = true;
        if (!getToCancelIds().isEmpty()) {
            LOGGER.debug("Cancel first batch ids");
            getToCancelIds().forEach(this::scancel);
        } else {
            LOGGER.warn("Nothing to cancel.");
        }
    }

    boolean isCancel() {
        return cancel;
    }

    private void scancel(Long jobId) {
        LOGGER.debug("Scancel {}", jobId);
        tracingIds.remove(jobId);
        commandExecutor.execute("scancel " + jobId);
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
