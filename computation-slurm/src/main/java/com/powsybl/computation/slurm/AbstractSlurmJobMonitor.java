/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

/**
 *
 * Abstract base class for jobs monitors, in charge of monitoring the status
 * of submitted jobs.
 *
 * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
 */
public abstract class AbstractSlurmJobMonitor implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSlurmJobMonitor.class);

    private final Supplier<List<MonitoredJob>> jobsSupplier;

    protected AbstractSlurmJobMonitor(Supplier<List<MonitoredJob>> jobsSupplier) {
        this.jobsSupplier = jobsSupplier;
    }

    @Override
    public void run() {
        try {
            detectJobsState(jobsSupplier.get());
        } catch (Throwable t) {
            LOGGER.warn("Exception in job state detection", t);
            // scheduleAtFixedRate() API said: If any execution of the task encounters an exception, subsequent executions are suppressed.
        }
    }

    protected abstract void detectJobsState(List<MonitoredJob> jobs);

}
