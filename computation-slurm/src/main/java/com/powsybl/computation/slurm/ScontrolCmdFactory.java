/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

/**
 * @see <a href="https://slurm.schedmd.com/scontrol.html">Scontrol</a>
 *
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public final class ScontrolCmdFactory {

    private static final String SCONTROL = "scontrol ";
    private static final String SHOW_JOB = "show job ";

    static ScontrolCmd showJob(long jobId) {
        return new ScontrolCmd(SCONTROL + SHOW_JOB + jobId, ScontrolCmd.ScontrolType.SHOW_JOB);
    }

    private ScontrolCmdFactory() {
    }
}
