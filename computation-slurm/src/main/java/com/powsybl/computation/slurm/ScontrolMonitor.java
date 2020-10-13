/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *  A job monitor which uses "scontrol show job ID_OF_JOB" to get state of job,
 *  in case, the job itself can not finish completely.
 *  (For example, scancel on slurm directly or timeout)
 *
 *  @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class ScontrolMonitor extends AbstractSlurmJobMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScontrolMonitor.class);

    private final CommandExecutor commandRunner;

    private int counter;

    ScontrolMonitor(SlurmComputationManager manager) {
        super(() -> manager.getTaskStore().getPendingJobs());
        this.commandRunner = manager.getCommandRunner();
    }

    @Override
    public void detectJobsState(List<MonitoredJob> jobs) {
        boolean cleaned = false;
        Set<Long> checkedIds = new HashSet<>();
        LOGGER.info("Scontrol monitor starts {}...", counter);
        while (!cleaned) {
            List<MonitoredJob> sorted = jobs.stream()
                    .sorted(Comparator.comparing(MonitoredJob::getJobId))
                    .collect(Collectors.toList());
            // start from min id
            // find the first unmoral state
            // call job.interruptJob()
            // restart until tracingIds are all running or pending
            for (MonitoredJob job : sorted) {
                long id = job.getJobId();
                if (checkedIds.contains(id)) {
                    continue;
                }
                ScontrolCmd scontrolCmd = ScontrolCmdFactory.showJob(id);
                try {
                    ScontrolCmd.ScontrolResult scontrolResult = scontrolCmd.send(commandRunner);
                    SlurmConstants.JobState jobState = scontrolResult.getResult().getJobState();
                    boolean anormal = false;
                    switch (jobState) {
                        case RUNNING:
                        case PENDING:
                            checkedIds.add(id);
                            break;
                        case TIMEOUT:
                        case DEADLINE:
                        case CANCELLED:
                            anormal = true;
                            String msg = "JobId: " + id + " is " + jobState;
                            job.interrupted();
                            LOGGER.info(msg);
                            break;
                        case COMPLETED:
                            // this monitor found task finished before flagDirMonitor
                            // maybe store it and recheck in next run()
                            checkedIds.add(id);
                            break;
                        default:
                            LOGGER.warn("Not implemented yet {}", jobState);
                    }
                    if (anormal) {
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
