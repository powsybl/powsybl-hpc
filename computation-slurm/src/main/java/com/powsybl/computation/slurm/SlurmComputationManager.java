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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static java.util.Objects.requireNonNull;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class SlurmComputationManager implements ComputationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlurmComputationManager.class);

    private static final String FLAGS_DIR_PREFIX = "myflags_"; // where flag files are created and be watched

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
        LOGGER.info("FlagMonitor={};ScontrolMonitor={};ArrayJob={}", config.getPollingInterval(), config.getScontrolInterval(), config.isJobArray());
    }

    /**
     * only for unit testing purposes
     */
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

        taskStore = new TaskStore();
    }

    private void startWatchServices() {
        startFlagFilesMonitor();
        startScontrolMonitor();
    }

    private void checkSlurmInstall() {
        for (String program : new String[]{"squeue", "sinfo", "srun", "sbatch", "scontrol"}) {
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

        CompletableFuture<R> result = new CompletableFuture<>();

        //We use a completable future bound to the actual task to be able to interrupt the underlying thread via cancel
        //so that for example client code in "before" can be interrupted
        CompletableFutureTask interruptible = new CompletableFutureTask<>(() -> {
            doExecute(result, environment, handler, parameters);
            return null;
        }).runAsync(executorService);

        //If the returned result is cancelled by the user,
        //interrupt the actual execution thread through the previous future
        result.exceptionally(exception -> {
            interruptible.cancel(true);
            return null;
        });

        return result;
    }

    /**
     * Clean task store after completion of the task,
     * and interrupt task if the future has been completed with an exception,
     * in particular when cancellation has been required.
     */
    private <R> void addFinalizer(CompletableFuture<R> result, SlurmTask task) {
        result.handle((res, exception) -> {
            taskStore.remove(task);
            if (exception != null) {
                task.interrupt();
            }
            return null;
        });
    }

    private <R> void doExecute(CompletableFuture<R> futureResult, ExecutionEnvironment environment, ExecutionHandler<R> handler, ComputationParameters parameters) {

        if (futureResult.isCancelled()) {
            LOGGER.info("Slurm task cancelled before working directory creation");
            return;
        }

        Path remoteWorkingDir;
        try (WorkingDirectory remoteWorkingDirectory = new RemoteWorkingDir(baseDir, environment.getWorkingDirPrefix(), environment.isDebug())) {
            remoteWorkingDir = remoteWorkingDirectory.toPath();

            List<CommandExecution> commandExecutions = handler.before(remoteWorkingDir);

            SlurmTask slurmTask = null;
            if (config.isJobArray()) {
                slurmTask = new JobArraySlurmTask(this, remoteWorkingDirectory, commandExecutions, parameters, environment);
            } else {
                slurmTask = new SlurmTaskImpl(this, remoteWorkingDirectory, commandExecutions, parameters, environment);
            }
            taskStore.add(slurmTask);

            //At this point we need to add the callback which will interrupt the underlying task,
            //and therefore jobs, in case of exception
            addFinalizer(futureResult, slurmTask);

            slurmTask.submit();

            LOGGER.debug("Waiting finish...");
            ExecutionReport report = slurmTask.await();

            R res = handler.after(remoteWorkingDir, report);
            LOGGER.debug("Normal exit");
            futureResult.complete(res);
        } catch (Throwable exception) {
            LOGGER.debug("An exception occurred during execution of commands on slurm.", exception);
            futureResult.completeExceptionally(exception);
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

    boolean isCloseStarted() {
        return closeStarted;
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
                getTaskStore().interruptAll();
            }
        });
    }

}
