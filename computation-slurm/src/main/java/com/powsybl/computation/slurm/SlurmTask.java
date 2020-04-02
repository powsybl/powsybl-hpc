/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 *
 * Represents a user submitted tasks, which will probably required the execution
 * of multiple underlying individual jobs on the slurm infrastructure.
 *
 * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
 */
public interface SlurmTask {

    /**
     * Submits all jobs required to complete this task to the Slurm infrastructure.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    void submit() throws IOException, InterruptedException;

    /**
     * Waits for the whole task to be executed, and generates the execution report.
     *
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     */
    SlurmExecutionReport await() throws InterruptedException, ExecutionException;


    /**
     * Asks for interruption of the execution of this task,
     * in order to save the infrastructure computation resources.
     */
    void interrupt();

    /**
     * Provides the list of jobs for which completion status needs to be monitored.
     */
    List<MonitoredJob> getPendingJobs();

}
