/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
class FlagFilesMonitor implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlagFilesMonitor.class);

    private final CommandExecutor commandRunner;
    private final Path flagDir;
    private final TaskStore taskStore;

    FlagFilesMonitor(SlurmComputationManager slurmComputationManager) {
        this(slurmComputationManager.getCommandRunner(), slurmComputationManager.getFlagDir(), slurmComputationManager.getTaskStore());
    }

    FlagFilesMonitor(CommandExecutor commandRunner, Path flagDir, TaskStore taskStore) {
        this.commandRunner = Objects.requireNonNull(commandRunner);
        this.flagDir = Objects.requireNonNull(flagDir);
        this.taskStore = Objects.requireNonNull(taskStore);
    }

    @Override
    public void run() {
        try {
            LOGGER.debug("polling in {}", flagDir);
            CommandResult execute = commandRunner.execute("ls -1 " + flagDir);
            String stdout = execute.getStdOut();
            String[] split = stdout.split("\n");
            for (String line : split) {
                int idx = line.indexOf('_');
                if (idx > 0) {
                    // ex: mydone_workingDirxxxxxx_taskid
                    int lastIdx = line.lastIndexOf('_');
                    String workingDirName = line.substring(idx + 1, lastIdx);
                    TaskCounter taskCounter = taskStore.getTaskCounter(workingDirName);
                    if (taskCounter != null) {
                        LOGGER.debug("{} found", line);
                        taskCounter.countDown();
                        commandRunner.execute("rm " + flagDir + "/" + line);
                        // cancel following jobs(which depends on this job) if there are errors
                        if (line.startsWith("myerror_")) {
                            taskStore.getCompletableFuture(workingDirName).cancel(true);
                        } else if (line.startsWith("mydone_")) {
                            String id = line.substring(lastIdx + 1);
                            taskStore.untracing(Long.parseLong(id));
                        } else {
                            LOGGER.warn("Unexcepted file found in flagDir:" + line);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn(t.toString());
            // scheduleAtFixedRate() API said: If any execution of the task encounters an exception, subsequent executions are suppressed.
        }
    }
}
