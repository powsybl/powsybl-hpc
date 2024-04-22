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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * A job monitor which relies on the creation of "flag" files at the end of submitted jobs.
 *
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
class FlagFilesMonitor extends AbstractSlurmJobMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlagFilesMonitor.class);

    private final CommandExecutor commandRunner;
    private final Path flagDir;

    FlagFilesMonitor(SlurmComputationManager slurmComputationManager) {
        super(() -> slurmComputationManager.getTaskStore().getPendingJobs());
        requireNonNull(slurmComputationManager);
        this.commandRunner = requireNonNull(slurmComputationManager.getCommandRunner());
        this.flagDir = requireNonNull(slurmComputationManager.getFlagDir());
    }

    @Override
    public void detectJobsState(List<MonitoredJob> jobs) {
        LOGGER.debug("polling in {}", flagDir);
        Map<Long, MonitoredJob> jobsById = jobs.stream()
                .collect(Collectors.toMap(MonitoredJob::getJobId, job -> job));
        CommandResult execute = commandRunner.execute("ls -1 " + flagDir);
        String stdout = execute.stdOut();
        String[] split = stdout.split("\n");
        for (String line : split) {
            long idx = line.indexOf('_');
            if (idx > 0) {
                // ex: mydone_workingDirxxxxxx_taskid
                int lastIdx = line.lastIndexOf('_');
                long id;
                String substring = line.substring(lastIdx + 1);
                if (substring.contains("-")) {
                    id = Long.parseLong(substring.split("-")[0]);
                } else {
                    id = Long.parseLong(substring);
                }
                MonitoredJob job = jobsById.get(id);
                if (job == null) {
                    continue;
                }
                LOGGER.debug("{} found", line);
                if (line.startsWith("myerror_")) {
                    job.failed();
                } else if (line.startsWith("mydone_")) {
                    job.done();
                } else {
                    LOGGER.warn("Unexpected file found in flagDir: {}", line);
                }

                commandRunner.execute("rm " + flagDir + "/" + line);
            }
        }
    }
}
