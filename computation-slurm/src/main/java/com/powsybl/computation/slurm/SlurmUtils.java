/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.google.common.base.Preconditions;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
final class SlurmUtils {

    private static final Long MINUTE = 60L;
    private static final Long HOUR = 3600L;
    private static final Long DAY = 86400L;

    /**
     * Seconds to parse and return an acceptable time format in String.
     * Used in --time, --time-min
     * @see <a href="https://slurm.schedmd.com/sbatch.html">https://slurm.schedmd.com/sbatch.html</a>
     * @param seconds Should be greater than 0.
     * @return Acceptable time format.
     */
    static String toTime(long seconds) {
        Preconditions.checkArgument(seconds > 0 && seconds < DAY * 100, "Time limit (%s) Should be greater than 0 and less than 100 days", seconds);
        long days = seconds / DAY;
        long hours = (seconds - days * DAY) / HOUR;
        long minutes = (seconds % HOUR) / MINUTE;
        long secs = seconds % MINUTE;
        return String.format("%02d-%02d:%02d:%02d", days, hours, minutes, secs);
    }

    private SlurmUtils() {
    }
}
