/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.pastdev.jsch.DefaultSessionFactory;
import com.pastdev.jsch.SessionFactory;
import com.pastdev.jsch.command.CommandRunner;
import com.pastdev.jsch.nio.file.UnixSshFileSystem;
import com.pastdev.jsch.nio.file.UnixSshFileSystemProvider;
import com.powsybl.commons.io.WorkingDirectory;
import com.powsybl.computation.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class SlurmComputationManager implements ComputationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlurmComputationManager.class);

    private static final String SACCT_NONZERO_JOB = "sacct --jobs=%s -n --format=\"jobid,exitcode\" | grep -v \"0:0\" | grep -v \"\\.\"";
    private static final Pattern DIGITAL_PATTERN = Pattern.compile("\\d+");
    private static final String BATCH_EXT = ".batch";
    private static final String FLAGS_DIR_PREFIX = "myflags_"; // where flag files are created and be watched
    private static final String UNZIP_INPUTS_COMMAND_ID = "unzip_inputs_command";

    private static final String CLOSE_START_NO_MORE_SEND_INFO = "SCM close started and no more send sbatch to slurm";

    private final SlurmComputationConfig config;

    private final ExecutorService executorService;

    private final CommandExecutor commandRunner;

    private final FileSystem fileSystem;
    private final Path baseDir;
    private final Path localDir;
    private final Path flagDir;
    private final WorkingDirectory commonDir;

    private final SlurmStatusManager statusManager;

    private final TaskStore taskStore = new TaskStore();

    private final ScheduledExecutorService flagsDirMonitorService = Executors.newScheduledThreadPool(1);
    private ScheduledFuture flagsDirMonitorFuture;

    private final ScheduledExecutorService scontrolMonitorService = Executors.newScheduledThreadPool(1);
    private ScheduledFuture scontrolMonitorFuture;

    private volatile boolean isClosed = false; // avoid twice close in normal process
    private volatile boolean closeStarted = false; // To stop continuing send new jobs while closing

    public SlurmComputationManager(SlurmComputationConfig config) throws IOException {
        this.config = requireNonNull(config);

        executorService = Executors.newCachedThreadPool();

        if (config.isRemote()) {
            SlurmComputationConfig.SshConfig sshConfig = config.getSshConfig();
            LOGGER.debug("Initializing remote slurm computation manager");
            SessionFactory sessionFactory = createSshSessionFactory(sshConfig);
            CommandRunner runner = new ConcurrentSshCommandRunner(sessionFactory, sshConfig.getMaxSshConnection(), sshConfig.getMaxRetry());
            commandRunner = new SshCommandExecutor(runner);
            fileSystem = initRemoteFileSystem(config, sessionFactory, runner);
        } else {
            LOGGER.debug("Initializing local slurm computation manager");
            commandRunner = new LocalCommandExecutor();
            fileSystem = FileSystems.getDefault();
        }

        statusManager = new SlurmStatusManager(commandRunner);

        checkSlurmInstall();

        baseDir = fileSystem.getPath(config.getWorkingDir());
        localDir = Files.createDirectories(config.getLocalDir());
        flagDir = initFlagDir(baseDir);
        commonDir = initCommonDir(baseDir);

        startWatchServices();

        Runtime.getRuntime().addShutdownHook(shutdownThread());

        LOGGER.debug("init scm");
        LOGGER.info("FlagMonitor={};ScontrolMonitor={}", config.getPollingInterval(), config.getScontrolInterval());
    }

    private void startWatchServices() {
        startFlagFilesMonitor();
        startScontrolMonitor();
    }

    private void checkSlurmInstall() {
        for (String program : new String[]{"squeue", "sinfo", "srun", "sbatch", "scontrol", "sacct"}) {
            if (commandRunner.execute(program + " --help").getExitCode() != 0) {
                throw new SlurmException("Slurm is not installed");
            }
        }
    }

    private static SessionFactory createSshSessionFactory(SlurmComputationConfig.SshConfig sshConfig) {
        DefaultSessionFactory sessionFactory = new DefaultSessionFactory(sshConfig.getUsername(), sshConfig.getHostname(), sshConfig.getPort());
        sessionFactory.setConfig("StrictHostKeyChecking", "no");
        sessionFactory.setConfig("PreferredAuthentications", "password");
        sessionFactory.setPassword(sshConfig.getPassword());
        return sessionFactory;
    }

    private static FileSystem initRemoteFileSystem(SlurmComputationConfig config, SessionFactory sessionFactory, CommandRunner commandRunner) throws IOException {
        SlurmComputationConfig.SshConfig sshConfig = config.getSshConfig();
        Map<String, Object> environment = new HashMap<>();
        environment.put("defaultSessionFactory", sessionFactory);
        URI uri;
        try {
            uri = new URI("ssh.unix://" + sshConfig.getUsername() + "@" + sshConfig.getHostname() + ":" + sshConfig.getPort() + config.getWorkingDir());
        } catch (URISyntaxException e) {
            LOGGER.error(e.toString(), e);
            throw new SlurmException(e);
        }

        UnixSshFileSystem fs = new UnixSshFileSystem(new UnixSshFileSystemProvider(), uri, environment);
        fs.setCommandRunner(commandRunner);
        return fs;
    }

    private static Path initFlagDir(Path baseDir) throws IOException {
        Path p;
        long n = new SecureRandom().nextLong();
        n = n == -9223372036854775808L ? 0L : Math.abs(n);
        p = baseDir.resolve(FLAGS_DIR_PREFIX + Long.toString(n));
        return Files.createDirectories(p);
    }

    private WorkingDirectory initCommonDir(Path baseDir) throws IOException {
        return new RemoteWorkingDir(baseDir, "itools_common_", false);
    }

    private void startFlagFilesMonitor() {
        FlagFilesMonitor flagFilesMonitor = new FlagFilesMonitor(this);
        scontrolMonitorFuture = flagsDirMonitorService.scheduleAtFixedRate(flagFilesMonitor, config.getPollingInterval(), config.getPollingInterval(), TimeUnit.SECONDS);
    }

    private void startScontrolMonitor() {
        ScontrolMonitor scontrolMonitor = new ScontrolMonitor(this);
        flagsDirMonitorFuture = scontrolMonitorService.scheduleAtFixedRate(scontrolMonitor, config.getScontrolInterval(), config.getScontrolInterval(), TimeUnit.MINUTES);
    }

    CommandExecutor getCommandRunner() {
        return commandRunner;
    }

    Path getFlagDir() {
        return flagDir;
    }

    TaskStore getTaskStore() {
        return taskStore;
    }

    @Override
    public String getVersion() {
        // get slurm version
        return commandRunner.execute("scontrol --version").getStdOut();
    }

    @Override
    public OutputStream newCommonFile(String fileName) throws IOException {
        return Files.newOutputStream(commonDir.toPath().resolve(fileName));
    }

    @Override
    public <R> CompletableFuture<R> execute(ExecutionEnvironment environment, ExecutionHandler<R> handler) {
        return execute(environment, handler, ComputationParameters.empty());
    }

    @Override
    public <R> CompletableFuture<R> execute(ExecutionEnvironment environment, ExecutionHandler<R> handler, ComputationParameters parameters) {
        requireNonNull(environment);
        requireNonNull(handler);

        Mycf<R> f = new Mycf<>(this);
        executorService.submit(() -> {
            f.setThread(Thread.currentThread());
            try {
                remoteExecute(environment, handler, parameters, f);
            } catch (Exception e) {
                LOGGER.error(e.toString(), e);
                f.completeExceptionally(e);
            }
            taskStore.remove(f);
        });
        return f;
    }

    static class Mycf<R> extends CompletableFuture<R> {

        Thread thread;
        SlurmComputationManager mgr;
        volatile boolean cancel = false;

        Mycf(SlurmComputationManager manager) {
            mgr = manager;
        }

        @Override
        public boolean isCancelled() {
            return cancel;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancel = true;

            if (this.isDone() || this.isCompletedExceptionally()) {
                LOGGER.debug("Can not cancel");
                return false;
            }

            while (thread == null) {
                try {
                    LOGGER.debug("Waiting submittedFuture to be set...");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    LOGGER.warn(e.toString(), e);
                    Thread.currentThread().interrupt();
                }
            }
            LOGGER.debug("trying cancel");
            // countDown submitted task to avoid interruptedException
            TaskCounter taskCounter = mgr.taskStore.getTaskCounter(this);
            taskCounter.cancel();

            //No thread.interrupt() here:
            // it makes no sense to both interrupt the thread and cancel the taskCounter,
            // it's one or the other.
            // The interrupt will throw InterruptedExceptions or ClosedByInterruptException,
            // in subsequent method calls such as Files.read in executionHandler.after.
            LOGGER.debug("Canceled thread");

            Long jobId = mgr.taskStore.getFirstJobId(this);
            if (jobId == null) {
                LOGGER.warn("job not be submitted yet");
                return true;
            }

            mgr.scancelCascading(jobId);
            mgr.taskStore.remove(this);
            return true;
        }

        void setThread(Thread t) {
            this.thread = t;
        }
    }

    private void scancelCascading(Long jobId) {
        // For array jobs can be cancelled just by calling on master jobId
        // but currently array_job in slurm is not used, so jobs should be cancelled one by one
        // For dependency jobs will not be cancelled automatically and they will be pending in queue infinitely
        LOGGER.debug("cancel cascading: {}", jobId);
        scancel(jobId);
        taskStore.getBatchIds(jobId).forEach(this::scancel);
        taskStore.getDependentJobs(jobId).forEach(this::scancelWithMasterId);
    }

    private void scancelWithMasterId(Long masterid) {
        scancel(masterid);
        taskStore.getBatchIds(masterid).forEach(this::scancel);
    }

    private void scancel(Long jobId) {
        LOGGER.debug("scancel {}", jobId);
        taskStore.untracing(jobId);
        commandRunner.execute("scancel " + jobId);
    }

    private <R> void remoteExecute(ExecutionEnvironment environment, ExecutionHandler<R> handler, ComputationParameters parameters, CompletableFuture<R> f) {
        Path remoteWorkingDir;
        try (WorkingDirectory remoteWorkingDirectory = new RemoteWorkingDir(baseDir, environment.getWorkingDirPrefix(), environment.isDebug())) {
            remoteWorkingDir = remoteWorkingDirectory.toPath();

            taskStore.insert(remoteWorkingDir.getFileName().toString(), f);
            List<CommandExecution> commandExecutions = handler.before(remoteWorkingDir);

            int sum = commandExecutions.stream().mapToInt(CommandExecution::getExecutionCount).sum();
            TaskCounter taskCounter = new TaskCounter(sum);

            Map<Long, Command> jobIdCommandMap;
            jobIdCommandMap = generateSbatchAndSubmit(commandExecutions, parameters, remoteWorkingDir, environment, taskCounter, f);

            // waiting task finish
            try {
                taskCounter.await();
            } catch (InterruptedException e) {
                LOGGER.warn(e.toString(), e);
                // Not sure we should restore the interrupt here...
                // it will throw InterruptedException or ClosedByInterruptException,
                // in subsequent method calls such as Files.read in executionHandler.after.

                // We should either return right now, or let the following methods handle correctly
                // the result of the interrupted task without being bothered by unexpected exceptions.
                Thread.currentThread().interrupt();
            }

            if (closeStarted) {
                return;
            }

            SlurmExecutionReport report = generateReport(jobIdCommandMap, remoteWorkingDir);

            R result = handler.after(remoteWorkingDir, report);
            f.complete(result);
        } catch (IOException e) {
            LOGGER.error(e.toString(), e);
            f.completeExceptionally(e);
        }
    }

    private SlurmExecutionReport generateReport(Map<Long, Command> jobIdCommandMap, Path workingDir) {
        List<ExecutionError> errors = new ArrayList<>();

        Set<Long> jobIds = jobIdCommandMap.keySet();
        String jobIdsStr = StringUtils.join(jobIds, ",");
        String sacct = String.format(SACCT_NONZERO_JOB, jobIdsStr);
        CommandResult sacctResult = commandRunner.execute(sacct);
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
                ExecutionError error = new ExecutionError(jobIdCommandMap.get(jobId), executionIdx, exitCode);
                errors.add(error);
                LOGGER.debug("{} error added ", jobId);
            }
        }

        return new SlurmExecutionReport(errors, workingDir);
    }

    private Map<Long, Command> generateSbatchAndSubmit(List<CommandExecution> commandExecutions, ComputationParameters parameters, Path workingDir,
                                                       ExecutionEnvironment environment, TaskCounter taskCounter, CompletableFuture<?> future)
            throws IOException {
        Map<Long, Command> jobIdCommandMap = new HashMap<>();
        Long firstJobId = null; // the first jobId submitted of List<CommandExecution>
        List<Long> preJobIds = new ArrayList<>();
        Long preMasterJobId = null; // the masterJobId is the first id of each CommandExecution
                                    // it could be also job id for unzip common inputs

        outerSendingLoop:
        for (int commandIdx = 0; commandIdx < commandExecutions.size(); commandIdx++) {
            CommandExecution commandExecution = commandExecutions.get(commandIdx);
            Command command = commandExecution.getCommand();
            SbatchCmd cmd;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Executing {} command {} in working directory {}", command.getType(), command, workingDir);
            }

            // a master job to copy NonExecutionDependent and PreProcess needed input files
            if (command.getInputFiles().stream()
                    .anyMatch(inputFile -> !inputFile.dependsOnExecutionNumber() && inputFile.getPreProcessor() != null)) {
                if (closeStarted) {
                    LOGGER.info(CLOSE_START_NO_MORE_SEND_INFO);
                    break outerSendingLoop;
                }
                SbatchScriptGenerator sbatchScriptGenerator = new SbatchScriptGenerator(flagDir);
                List<String> shell = sbatchScriptGenerator.unzipCommonInputFiles(command);
                copyShellToRemoteWorkingDir(shell, UNZIP_INPUTS_COMMAND_ID + "_" + commandIdx, workingDir);
                cmd = buildSbatchCmd(workingDir, UNZIP_INPUTS_COMMAND_ID, commandIdx, preJobIds, parameters);
                if (isSendAllowed(future)) {
                    long jobId = launchSbatch(cmd);
                    if (firstJobId == null) {
                        firstJobId = jobId;
                        taskStore.insert(workingDir.getFileName().toString(), taskCounter, firstJobId);
                        LOGGER.debug("First jobid : {}", firstJobId);
                    }
                    if (preMasterJobId != null) {
                        taskStore.insertDependency(preMasterJobId, jobId);
                    }
                    preJobIds = Collections.singletonList(jobId);
                    preMasterJobId = jobId;
                } else {
                    logNotSendReason(future);
                    break;
                }
            }

            // no job array --> commandId_index.batch
            Long masterJobId = null;
            for (int executionIndex = 0; executionIndex < commandExecution.getExecutionCount(); executionIndex++) {
                if (closeStarted) {
                    LOGGER.info(CLOSE_START_NO_MORE_SEND_INFO);
                    break outerSendingLoop;
                }
                prepareBatch(command, executionIndex, environment, commandExecution, workingDir);
                cmd = buildSbatchCmd(workingDir, command.getId(), executionIndex, preJobIds, parameters);
                if (isSendAllowed(future)) {
                    long jobId = launchSbatch(cmd);
                    if (firstJobId == null) {
                        firstJobId = jobId;
                        taskStore.insert(workingDir.getFileName().toString(), taskCounter, firstJobId);
                        LOGGER.debug("First jobid : {}", firstJobId);
                    }
                    if (executionIndex == 0) {
                        masterJobId = jobId;
                        if (preMasterJobId != null) {
                            taskStore.insertDependency(preMasterJobId, masterJobId);
                        }
                    }
                    taskStore.insertBatchIds(masterJobId, jobId);
                } else {
                    logNotSendReason(future);
                    break;
                }
            }

            jobIdCommandMap.put(masterJobId, command);
            List<Long> jobIds = new ArrayList<>(taskStore.getBatchIds(masterJobId));
            jobIds.add(masterJobId);

            preMasterJobId = masterJobId;
            preJobIds = jobIds;
        }
        return jobIdCommandMap;
    }

    private boolean isSendAllowed(CompletableFuture future) {
        return !future.isCancelled() && !closeStarted;
    }

    private void logNotSendReason(CompletableFuture future) {
        if (closeStarted) {
            LOGGER.info(CLOSE_START_NO_MORE_SEND_INFO);
        }
        if (future.isCancelled()) {
            LOGGER.warn("Future cancelled. {}", future);
        }
    }

    private void prepareBatch(Command command, int executionIndex, ExecutionEnvironment environment, CommandExecution commandExecution, Path remoteWorkingDir) throws IOException {
        // prepare sbatch script from command
        Map<String, String> executionVariables = CommandExecution.getExecutionVariables(environment.getVariables(), commandExecution);
        SbatchScriptGenerator scriptGenerator = new SbatchScriptGenerator(flagDir);
        List<String> shell = scriptGenerator.parser(command, executionIndex, remoteWorkingDir, executionVariables);
        if (executionIndex == -1) {
            // array job not used yet
            copyShellToRemoteWorkingDir(shell, command.getId(), remoteWorkingDir);
        } else {
            copyShellToRemoteWorkingDir(shell, command.getId() + "_" + executionIndex, remoteWorkingDir);
        }
    }

    private static void copyShellToRemoteWorkingDir(List<String> shell, String batchName, Path remoteWorkingDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        shell.forEach(line -> sb.append(line).append('\n'));
        String str = sb.toString();
        Path batch;
        batch = remoteWorkingDir.resolve(batchName + BATCH_EXT);
        try (InputStream targetStream = new ByteArrayInputStream(str.getBytes())) {
            Files.copy(targetStream, batch);
        }
    }

    private SbatchCmd buildSbatchCmd(Path workingDir, String commandId, int executionIndex, List<Long> preJobIds, ComputationParameters baseParams) {
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
            SbatchCmdResult sbatchResult = cmd.send(commandRunner);
            long submittedJobId = sbatchResult.getSubmittedJobId();
            LOGGER.debug("Submitted: {}, with jobId:{}", cmd, submittedJobId);
            return submittedJobId;
        } catch (SlurmCmdNonZeroException e) {
            throw new SlurmException(e);
        }
    }

    @Override
    public ComputationResourcesStatus getResourcesStatus() {
        return statusManager.getResourcesStatus();
    }

    @Override
    public Executor getExecutor() {
        return null;
    }

    @Override
    public Path getLocalDir() {
        return localDir;
    }

    @Override
    public void close() {
        baseClose();
        isClosed = true;
    }

    private void baseClose() {
        LOGGER.debug("Closing SCM.");
        closeStarted = true;

        stopWatchServices();

        // delete flags
        try {
            commandRunner.execute("rm -rf " + flagDir.toAbsolutePath().toString());
        } catch (Exception e) {
            LOGGER.warn(e.toString(), e);
            throw new SlurmException(e);
        }

        try {
            commonDir.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try {
            shutdownAndAwaitTermination(); // wait for closing working directories
        } finally {
            try {
                commandRunner.close();
                try {
                    fileSystem.close();
                } catch (UnsupportedOperationException e) {
                    //Filesystem closing may not be supported.
                    LOGGER.info(e.toString(), e);
                }
            } catch (IOException e) {
                LOGGER.error(e.toString(), e);
            }
        }

        LOGGER.debug("Slurm Computation Manager closed");
    }

    private void stopWatchServices() {
        flagsDirMonitorFuture.cancel(true);
        flagsDirMonitorService.shutdown();
        scontrolMonitorFuture.cancel(true);
        scontrolMonitorService.shutdown();
    }

    private void shutdownAndAwaitTermination() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(20, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(20, TimeUnit.SECONDS)) {
                    LOGGER.error("Thread pool did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private class RemoteWorkingDir extends WorkingDirectory {

        RemoteWorkingDir(Path parentDir, String prefix, boolean debug) throws IOException {
            super(parentDir, prefix, debug);
        }

        @Override
        public void close() {
            if (!isDebug()) {
                commandRunner.execute("rm -rf " + toPath().toAbsolutePath());
            }
        }
    }

    private Thread shutdownThread() {
        return new Thread(() -> {
            closeStarted = true;
            if (!isClosed) {
                LOGGER.info("Shutdown slurm...");
                LOGGER.info("Cancel current subbmited jobs");
                Set<Long> tracingIds = new HashSet<>(getTaskStore().getTracingIds());
                tracingIds.forEach(this::scancel);
                // count down task to avoid InterruptedException
                getTaskStore().getTaskCounters().forEach(TaskCounter::cancel);
                baseClose();
            }
        });
    }

}
