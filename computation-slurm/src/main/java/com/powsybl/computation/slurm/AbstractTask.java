/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.commons.io.WorkingDirectory;
import com.powsybl.computation.*;
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
import java.util.stream.Collectors;

import static com.powsybl.computation.slurm.SlurmConstants.BATCH_EXT;

/**
 * @author Yichen TANG {@literal <yichen.tang at rte-france.com>}
 */
public abstract class AbstractTask implements SlurmTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTask.class);

    protected static final String UNZIP_INPUTS_COMMAND_ID = "unzip_inputs_command";
    private static final String CLOSE_START_NO_MORE_SEND_INFO = "SCM close started and no more send sbatch to slurm";

    protected final Path workingDir;
    protected final Path flagDir;
    protected final CommandExecutor commandExecutor;
    protected final List<CommandExecution> executions;
    protected final ComputationParameters parameters;
    protected final ExecutionEnvironment environment;

    protected final List<CompletableMonitoredJob> jobs = new ArrayList<>();
    protected final CompletableFuture<Void> taskCompletion = new CompletableFuture<>();
    protected Map<Long, Command> commandByJobId;

    private final SlurmComputationManager scm;

    AbstractTask(SlurmComputationManager scm, WorkingDirectory directory,
                        List<CommandExecution> executions, ComputationParameters parameters, ExecutionEnvironment environment) {
        this.scm = Objects.requireNonNull(scm);
        Objects.requireNonNull(directory);
        this.workingDir = Objects.requireNonNull(directory.toPath());
        this.commandExecutor = Objects.requireNonNull(scm.getCommandRunner());
        this.flagDir = Objects.requireNonNull(scm.getFlagDir());
        this.executions = Objects.requireNonNull(executions);
        this.parameters = Objects.requireNonNull(parameters);
        this.environment = Objects.requireNonNull(environment);
    }

    /**
     * Check if task has already been completed, or if computation manager is closing.
     */
    protected boolean cannotSubmit() {
        if (scm.isCloseStarted()) {
            LOGGER.info(CLOSE_START_NO_MORE_SEND_INFO);
            return true;
        }
        if (isCompleted()) {
            LOGGER.info("Stopping jobs submission for task in {}: task has been interrupted.", workingDir);
            return true;
        }
        return false;
    }

    protected long launchSbatch(SbatchCmd cmd) {
        try {
            SbatchCmdResult sbatchResult = cmd.send(commandExecutor);
            long submittedJobId = sbatchResult.getSubmittedJobId();
            LOGGER.debug("Submitted with jobId:{}", submittedJobId);
            return submittedJobId;
        } catch (SlurmCmdNonZeroException e) {
            throw new SlurmException(e);
        }
    }

    protected void copyShellToRemoteWorkingDir(List<String> shell, String batchName) throws IOException {
        StringBuilder sb = new StringBuilder();
        shell.forEach(line -> sb.append(line).append('\n'));
        String str = sb.toString();
        Path batch;
        batch = workingDir.resolve(batchName + BATCH_EXT);
        try (InputStream targetStream = new ByteArrayInputStream(str.getBytes())) {
            Files.copy(targetStream, batch);
        }
    }

    Path getWorkingDirPath() {
        return workingDir;
    }

    void addParameters(SbatchCmdBuilder builder, String commandId) {
        SlurmComputationParameters extension = parameters.getExtension(SlurmComputationParameters.class);
        if (extension != null) {
            extension.getQos().ifPresent(builder::qos);
            extension.getMem().ifPresent(builder::mem);
        }
        parameters.getDeadline(commandId).ifPresent(builder::deadline);
        parameters.getTimeout(commandId).ifPresent(builder::timeout);
    }

    final void aggregateMonitoredJobs() {
        CompletableFuture<?>[] monitoredJobsFutures = jobs.stream()
                .filter(CompletableMonitoredJob::isCompletionRequired)
                .map(CompletableMonitoredJob::getCompletableFuture)
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(monitoredJobsFutures)
                .thenRun(() -> {
                    LOGGER.debug("Slurm task completed in {}.", workingDir);
                    taskCompletion.complete(null);
                });
    }

    @Override
    public ExecutionReport await() throws InterruptedException, ExecutionException {
        taskCompletion.get();
        return generateReport();
    }

    ExecutionReport generateReport() {
        List<ExecutionError> errors = new ArrayList<>();
        try {
            for (Long id : getAllJobIds()) {
                final ScontrolCmd.ScontrolResult scontrolResult = ScontrolCmdFactory.showJob(id).send(commandExecutor);
                for (ScontrolCmd.ScontrolResultBean bean : scontrolResult.getResultBeanList()) {
                    if (bean.getExitCode() != 0) {
                        final ExecutionError error = convertScontrolResult2Error(bean);
                        errors.add(error);
                        LOGGER.debug("{} error added ", error);
                    }
                }
            }
        } catch (SlurmCmdNonZeroException e) {
            LOGGER.warn("Scontrol non zero:", e);
        }
        return new DefaultExecutionReport(workingDir, errors);
    }

    abstract Collection<Long> getAllJobIds();

    abstract ExecutionError convertScontrolResult2Error(ScontrolCmd.ScontrolResultBean scontrolResultBean);

    /**
     * The list of jobs for which status must be monitored.
     *
     */
    @Override
    public List<MonitoredJob> getPendingJobs() {
        return jobs.stream().filter(job -> !job.isCompleted())
                .collect(Collectors.toList());
    }

    @Override
    public void interrupt() {
        taskCompletion.cancel(true);
        cancelSubmittedJobs();
    }

    /**
     * Asks for cancellation of submitted jobs to Slurm infrastructure.
     */
    protected void cancelSubmittedJobs() {
        jobs.forEach(CompletableMonitoredJob::interruptJob);
    }

    /**
     * {@code true} if the task is already considered completed, be it through normal completion or interruption.
     */
    private boolean isCompleted() {
        return taskCompletion.isDone();
    }

    public class CompletableMonitoredJob implements MonitoredJob {

        private final long jobId;
        private final CompletableFuture<Void> completed;
        private final boolean completionRequired;
        private boolean interrupted = false;

        private int counter = 1;

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

        public void setCounter(int counter) {
            this.counter = counter;
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
            counter--;
            if (counter == 0) {
                LOGGER.debug("Slurm job {} done.", jobId);
                completed.complete(null);
            } else {
                LOGGER.debug("Slurm array job {} done. Rest: {}", jobId, counter);
            }
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

            completed.complete(null);
            LOGGER.debug("Scancel slurm job {}.", jobId);
            commandExecutor.execute("scancel " + jobId);
        }

        /**
         * To be called by a monitor when the job has failed.
         *
         * <p>The implementation asks for the interruption of all other jobs,
         * but the task will complete normally and generate an execution report.
         */
        @Override
        public void failed() {
            LOGGER.debug("Slurm job {} failed.", jobId);
            taskCompletion.complete(null);
            cancelSubmittedJobs();
        }

        /**
         * To be called if the job is detected to have been killed
         * before completing.
         * <p>
         * The implementation completes the task with an exception,
         * and asks for interruption of all jobs.
         * </p>
         */
        @Override
        public void interrupted() {
            taskCompletion.completeExceptionally(new SlurmException("Job " + jobId + " execution has been interrupted on slurm infrastructure."));
            cancelSubmittedJobs();
        }

    }
}
