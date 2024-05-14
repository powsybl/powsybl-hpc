/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

/**
 *
 * An individual job submitted to slurm, an which completion or failure
 * needs to be monitored.
 *
 * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
 */
public interface MonitoredJob {

    /**
     * This job ID in slurm
     */
    long getJobId();

    /**
     * To be called by a monitor when the job has completed successfully.
     */
    void done();

    /**
     * To be called by a monitor when the job has completed but
     * with an error (exit code != 0).
     */
    void failed();

    /**
     * To be called by a monitor if the job is detected to have been killed
     * before completing.
     */
    void interrupted();
}
