/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

/**
 *  @author Yichen Tang <yichen.tang at rte-france.com>
 */
final class SlurmConstants {

    static final String OUT_EXT = ".out";
    static final String ERR_EXT = ".err";

    enum JobState {
        PENDING,
        RUNNING,
        TIMEOUT,
        DEADLINE,
        CANCELLED,
        COMPLETE
    }

    private SlurmConstants() {
    }

}
