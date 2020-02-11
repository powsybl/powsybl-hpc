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
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.powsybl.computation.slurm.SlurmConstants.BATCH_EXT;
import static java.util.Objects.requireNonNull;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class SlurmComputationManager implements ComputationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlurmComputationManager.class);

    private static final String SACCT_NONZERO_JOB = "sacct --jobs=%s -n --format=\"jobid,exitcode\" | grep -v \"0:0\" | grep -v \"\\.\"";
    private static final Pattern DIGITAL_PATTERN = Pattern.compile("\\d+");
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

    private final TaskStore taskStore;

    private final ScheduledExecutorService flagsDirMonitorService = Executors.newScheduledThreadPool(1);
    private ScheduledFuture flagsDirMonitorFuture;

    private final ScheduledExecutorService scontrolMonitorService = Executors.newScheduledThreadPool(1);
    private ScheduledFuture scontrolMonitorFuture;

    private volatile boolean isClosed = false; // avoid twice close in normal process
    private volatile boolean closeStarted = false; // To stop continuing send new jobs while closing

    public SlurmComputationManager(SlurmComputationConfig config) throws IOException {
        this.config = requireNonNull(config);
        this.taskStore = new TaskStore();

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

        localDir = Files.createDirectories(config.getLocalDir());
        baseDir = fileSystem.getPath(config.getWorkingDir());
        flagDir = initFlagDir();
        commonDir = initCommonDir();

        startWatchServices();

        Runtime.getRuntime().addShutdownHook(shutdownThread());

        LOGGER.debug("init scm");
        LOGGER.info("FlagMonitor={};ScontrolMonitor={}", config.getPollingInterval(), config.getScontrolInterval());
    }

    SlurmComputationManager(SlurmComputationConfig config, ExecutorService executorService, CommandExecutor commandRunner,
                            FileSystem fileSystem, Path localDir) throws IOException {
        this.config = config;
        this.executorService = executorService;
        this.commandRunner = commandRunner;

        this.fileSystem = fileSystem;
        this.localDir = localDir;
        checkSlurmInstall();
        baseDir = fileSystem.getPath(config.getWorkingDir());
        flagDir = initFlagDir();
        commonDir = initCommonDir();

        statusManager = new SlurmStatusManager(commandRunner);

        taskStore = new TaskStore(15);
    }

    private void startWatchServices() {
        startFlagFilesMonitor();
        startScontrolMonitor();
    }

    private void checkSlurmInstall() {
        for (String program : new String[]{"squeue", "sinfo", "srun", "sbatch", "scontrol", "sacct"}) {
            int exitCode = commandRunner.execute(program + " --help").getExitCode();
            if (exitCode != 0) {
                throw new SlurmException("Slurm is not installed. '" + program + " --help' failed with code " + exitCode);
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

    private Path initFlagDir() throws IOException {
        return Files.createTempDirectory(baseDir, FLAGS_DIR_PREFIX);
    }

    private WorkingDirectory initCommonDir() throws IOException {
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
        return commandRunner.execute("scontrol --version").getStdOut().trim();
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

        UUID callableId = UUID.randomUUID();
        Callable<R> callable = () -> doExecute(callableId, environment, handler, parameters);
        CompletableFutureTask<R> rCompletableFutureTask = new SlurmCompletableFutureTask<>(callable, callableId, taskStore).runAsync(executorService);
        rCompletableFutureTask.handleAsync((r, t) -> taskStore.finallyCleanCallable(callableId), executorService);
        return rCompletableFutureTask;
    }

    private <R> R doExecute(UUID callableId, ExecutionEnvironment environment, ExecutionHandler<R> handler, ComputationParameters parameters) throws IOException, InterruptedException, ExecutionException {
        Path remoteWorkingDir;
        try (WorkingDirectory remoteWorkingDirectory = new RemoteWorkingDir(baseDir, environment.getWorkingDirPrefix(), environment.isDebug())) {
            remoteWorkingDir = remoteWorkingDirectory.toPath();

            List<CommandExecution> commandExecutions = handler.before(remoteWorkingDir);
            SlurmExecutionReport report = null;
            Map<Long, Command> jobIdCommandMap;
            SlurmException slurmExceptionForReport = null;

            try {
                SlurmTask slurmTask = new SlurmTask(callableId, commandRunner, remoteWorkingDirectory, commandExecutions);
                taskStore.add(slurmTask);

                jobIdCommandMap = generateSbatchAndSubmit(slurmTask, parameters, environment);
                if (taskStore.isCancelled(callableId)) {
                    LOGGER.debug("cancelled after submitted");
                    throw new InterruptedException("cancelled after submitted");
                }
                LOGGER.debug("Waiting finish...");
                // waiting task finish
                // TODO check NPE
                slurmTask.await();

                Optional<SlurmException> optionalSlurmException = taskStore.getException(callableId);
                if (optionalSlurmException.isPresent()) {
                    throw optionalSlurmException.get();
                }
                report = generateReport(jobIdCommandMap, remoteWorkingDir);
            } catch (SlurmException e) {
                LOGGER.warn("slurm exception: {}", e.getCause().getMessage());
                LOGGER.debug("not throw exception and after() would be executed");
                slurmExceptionForReport = e;
            } catch (InterruptedException e) {
                LOGGER.debug("exit point 1: Interrupted " + e.getMessage());
                // TODO this msg is lost
                throw e;
            }

            try {
                R result = handler.after(remoteWorkingDir, report);
                LOGGER.debug("exit point 3: Normal");
                return result;
            } catch (Exception e) {
                if (slurmExceptionForReport != null) {
                    LOGGER.debug("exit point 2: Error by slurm --> continue to exit point 4");
                    e.addSuppressed(slurmExceptionForReport);
                }
                LOGGER.debug("exit point 4: other exception, ex: after throw powsybl exception");
                LOGGER.warn("exception:", e);
                throw e;
            }
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

    // We still need to check the cancel flag in task, because the interrupted exception is not re-thrown
    // in underlying jsch library.
    private void checkCancelledDuringSubmitting(UUID callableId) throws InterruptedException {
        if (taskStore.isCancelled(callableId)) {
            LOGGER.debug("cancelled during submitting");
            throw new InterruptedException("cancelled during submitting");
        }
    }

    private Map<Long, Command> generateSbatchAndSubmit(SlurmTask task, ComputationParameters parameters,
                                                       ExecutionEnvironment environment)
            throws IOException, InterruptedException {
        Map<Long, Command> jobIdCommandMap = new HashMap<>();
        Path workingDir = task.getWorkingDirPath();
        UUID callableId = task.getCallableId();
        outerSendingLoop:
        for (int commandIdx = 0; commandIdx < task.getCommandExecutionSize(); commandIdx++) {
            checkCancelledDuringSubmitting(callableId);
            CommandExecution commandExecution = task.getCommandExecution(commandIdx);
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
                checkCancelledDuringSubmitting(callableId);
                SbatchScriptGenerator sbatchScriptGenerator = new SbatchScriptGenerator(flagDir);
                List<String> shell = sbatchScriptGenerator.unzipCommonInputFiles(command);
                copyShellToRemoteWorkingDir(shell, UNZIP_INPUTS_COMMAND_ID + "_" + commandIdx, workingDir);
                cmd = buildSbatchCmd(workingDir, UNZIP_INPUTS_COMMAND_ID, commandIdx, task.getPreJobIds(), parameters);
                long jobId = launchSbatch(cmd);
                task.newCommonUnzipJob(jobId);
            }

            // no job array --> commandId_index.batch
            for (int executionIndex = 0; executionIndex < commandExecution.getExecutionCount(); executionIndex++) {
                if (closeStarted) {
                    LOGGER.info(CLOSE_START_NO_MORE_SEND_INFO);
                    break outerSendingLoop;
                }
                checkCancelledDuringSubmitting(callableId);
                prepareBatch(command, executionIndex, environment, commandExecution, workingDir);
                cmd = buildSbatchCmd(workingDir, command.getId(), executionIndex, task.getPreJobIds(), parameters);
                long jobId = launchSbatch(cmd);
                task.newBatch(jobId);
            }

            jobIdCommandMap.put(task.getCurrentMaster(), command);
            // finish binding batches
            task.setCurrentMasterNull();
        }
        return jobIdCommandMap;
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
        return executorService;
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
                getTaskStore().cancelAll();
            }
        });
    }

}
