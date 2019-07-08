/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 *  Use "scontrol show job ID_OF_JOB" to get state of job
 *  in case, the job itself can not finish completely.(For example, scancel on slurm directly or timeout)
 *
 *  @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class ScontrolMonitor implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScontrolMonitor.class);

    private final CommandExecutor commandRunner;
    private final TaskStore taskStore;

    private int counter;

    ScontrolMonitor(SlurmComputationManager manager) {
        this.commandRunner = manager.getCommandRunner();
        this.taskStore = manager.getTaskStore();
    }

    @Override
    public void run() {
        boolean cleaned = false;
        Set<Long> checkedIds = new HashSet<>();
        LOGGER.info("Scontrol monitor starts {}...", counter);
        while (!cleaned) {
            Set<Long> tracingIds = taskStore.getTracingIds();
            List<Long> sorted = tracingIds.stream().sorted().collect(Collectors.toList());
            // start from min id
            // find the first unmoral state
            // call future.cancel()
            // restart until tracingIds are all running or pending
            for (Long id : sorted) {
                if (checkedIds.contains(id)) {
                    continue;
                }
                ScontrolCmd scontrolCmd = ScontrolCmdFactory.showJob(id);
                ScontrolCmd.ScontrolResult scontrolResult = null;
                try {
                    scontrolResult = scontrolCmd.send(commandRunner);
                    SlurmConstants.JobState jobState = scontrolResult.getJobState();
                    boolean unmoral = false;
                    switch (jobState) {
                        case RUNNING:
                        case PENDING:
                            checkedIds.add(id);
                            break;
                        case TIMEOUT:
                        case DEADLINE:
                        case CANCELLED:
                            unmoral = true;
                            LOGGER.info("JobId: {} is {}", id, jobState);
                            Optional<CompletableFuture> unormalFuture = taskStore.getCompletableFutureByJobId(id);
                            unormalFuture.ifPresent(completableFuture -> completableFuture.cancel(true));
                            break;
                        case COMPLETE:
                            // this monitor found task finished before flagDirMonitor
                            // maybe store it and recheck in next run()
                            checkedIds.add(id);
                            break;
                        default:
                            LOGGER.warn("Not implemented yet {}", jobState);
                    }
                    if (unmoral) {
                        break; // restart
                    }
                } catch (SlurmCmdNonZeroException e) {
                    LOGGER.warn("Scontrol not work", e);
                }
            }
            cleaned = true;
        }
        LOGGER.info("Scontrol monitor ends {}...", counter);
        counter++;
    }

}
