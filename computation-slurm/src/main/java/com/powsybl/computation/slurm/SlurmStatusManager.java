/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.google.common.base.Strings;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
 */
class SlurmStatusManager {

    // TODO timezone??
    private static final String TIME_CMD = "date +\"%Y-%m-%d %H:%M:%S\"";
    private static final String INFO_CORES_CMD = "sinfo -h -o %C";
    private static final String QUEUE_CORES_PER_JOB = "squeue -h --format=\"%C %j\"";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss\n");

    private final CommandExecutor commandExecutor;

    SlurmStatusManager(CommandExecutor commandExecutor) {
        this.commandExecutor = Objects.requireNonNull(commandExecutor);
    }

    SlurmComputationResourcesStatus getResourcesStatus() {
        // date
        String dateOutput = commandExecutor.execute(TIME_CMD).getStdOut();
        DateTime time = DATE_TIME_FORMATTER.parseDateTime(dateOutput);

        // core
        String coresInfoOutput = commandExecutor.execute(INFO_CORES_CMD).getStdOut();
        String[] splits = coresInfoOutput.split("/");
        int availCores = Integer.parseInt(splits[1]);
        int busyCores = Integer.parseInt(splits[0]);

        // job
        Map<String, Integer> coresPerApp = new HashMap<>();
        String jobOutput = commandExecutor.execute(QUEUE_CORES_PER_JOB).getStdOut();
        if (!Strings.isNullOrEmpty(jobOutput)) {
            String[] jobLines = jobOutput.split("\n");
            for (String jobLine : jobLines) {
                int pos = jobLine.indexOf(' ');
                String name = jobLine.substring(pos + 1);
                int cores = Integer.parseInt(jobLine.substring(0, pos));
                coresPerApp.put(name, cores);
            }
        }

        return new SlurmComputationResourcesStatus(time, availCores, busyCores, coresPerApp);
    }
}
