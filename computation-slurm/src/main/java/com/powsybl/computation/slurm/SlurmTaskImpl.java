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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
public class SlurmTaskImpl implements SlurmTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlurmTaskImpl.class);

    private static final String UNZIP_INPUTS_COMMAND_ID = "unzip_inputs_command";
    private static final String CLOSE_START_NO_MORE_SEND_INFO = "SCM close started and no more send sbatch to slurm";
    private static final String SACCT_NONZERO_JOB = "sacct --jobs=%s -n --format=\"jobid,exitcode\" | grep -v \"0:0\" | grep -v \"\\.\"";
    private static final Pattern DIGITAL_PATTERN = Pattern.compile("\\d+");

    private WorkingDirectory directory;
    private Path workingDir;
    private SlurmComputationManager scm;
    private Path flagDir;
    private CommandExecutor commandExecutor;
    private List<CommandExecution> executions;
    private ComputationParameters parameters;
    private ExecutionEnvironment environment;

    private Map<Long, Command> commandByJobId;

    private Long firstJobId;
    private List<Long> masters;
    private Map<Long, SubTask> subTaskMap;
    private Long currentMaster;

    private final List<CompletableMonitoredJob> jobs = new ArrayList<>();
    private final CompletableFuture<Void> taskCompletion = new CompletableFuture<>();

    SlurmTaskImpl(SlurmComputationManager scm, WorkingDirectory directory,
                  List<CommandExecution> executions, ComputationParameters parameters, ExecutionEnvironment environment) {
        this.scm = Objects.requireNonNull(scm);
        this.commandExecutor = Objects.requireNonNull(scm.getCommandRunner());
        this.directory = Objects.requireNonNull(directory);
        this.workingDir = Objects.requireNonNull(directory.toPath());
        this.flagDir = Objects.requireNonNull(scm.getFlagDir());
        this.executions = Objects.requireNonNull(executions);
        this.parameters = Objects.requireNonNull(parameters);
        this.environment = Objects.requireNonNull(environment);
    }

    @Override
    public void submit() throws IOException {
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
                if (scm.isCloseStarted()) {
                    LOGGER.info(CLOSE_START_NO_MORE_SEND_INFO);
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
                if (scm.isCloseStarted()) {
                    LOGGER.info(CLOSE_START_NO_MORE_SEND_INFO);
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

        CompletableFuture[] monitoredJobsFutures = jobs.stream()
                .filter(CompletableMonitoredJob::isCompletionRequired)
                .map(CompletableMonitoredJob::getCompletableFuture)
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(monitoredJobsFutures)
                .thenRun(() ->  {
                    LOGGER.debug("Slurm task completed in {}.", workingDir);
                    taskCompletion.complete(null);
                });
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
            extension.getMem().ifPresent(builder::mem);
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

        Set<Long> jobIds = getAllJobIds();
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
            }
        }

        return new SlurmExecutionReport(errors, workingDir);
    }

    Path getWorkingDirPath() {
        return directory.toPath();
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
        taskCompletion.complete(null);
        cancelSubmittedJobs();
    }

    /**
     * The list of jobs for which status must be monitored.
     *
     * @return
     */
    @Override
    public List<MonitoredJob> getPendingJobs() {
        return jobs.stream().filter(job -> !job.isCompleted())
                .collect(Collectors.toList());
    }

    /**
     * Asks for cancellation of submitted jobs to Slurm infrastructure.
     */
    private void cancelSubmittedJobs() {
        jobs.forEach(CompletableMonitoredJob::interruptJob);
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

    private class CompletableMonitoredJob implements MonitoredJob {

        private final long jobId;
        private final CompletableFuture<Void> completed;
        private final boolean completionRequired;
        private boolean interrupted = false;

        CompletableMonitoredJob(long jobId) {
            this(jobId, true);
        }

        /**
         * Some jobs (see unzip) are not monitored for completion because we already monitor dependent jobs.
         * However if they fail, we still want to interrupt the task.
         */
        CompletableMonitoredJob(long jobId, boolean completionRequired) {
            this.jobId = jobId;
            this.completed = new CompletableFuture<>();
            this.completionRequired = completionRequired;
        }

        boolean isCompleted() {
            return completed.isDone();
        }

        boolean isCompletionRequired() {
            return completionRequired;
        }

        CompletableFuture<Void> getCompletableFuture() {
            return this.completed;
        }

        /**
         * This job ID in slurm
         */
        @Override
        public long getJobId() {
            return jobId;
        }

        /**
         * To be called by a monitor when the job has ended successfully.
         */
        @Override
        public void done() {
            LOGGER.debug("Slurm job {} done.", jobId);
            completed.complete(null);
        }


        /**
         * Asks for cancellation of this job to Slurm infrastructure,
         * if not already interrupted.
         */
        void interruptJob() {
            synchronized (this) {
                if (interrupted) {
                    return;
                }
                interrupted = true;
            }

            done();
            LOGGER.debug("Scancel slurm job {}.", jobId);
            commandExecutor.execute("scancel " + jobId);
        }

        /**
         * To be called by a monitor when the job has failed.
         *
         * The implementation asks for the interruption of all jobs.
         */
        @Override
        public void failed() {
            LOGGER.debug("Slurm job {} failed.", jobId);
            interrupt();
        }

        /**
         * To be called if the job is detected to have been killed
         * before completing.
         *
         * The implementation completes the task with an exception,
         * and asks for interruption of all jobs.
         */
        @Override
        public void interrupted() {
            taskCompletion.completeExceptionally(new SlurmException("Job " + jobId + " execution has been interrupted on slurm infrastructure."));
            interrupt();
        }

    }
}
