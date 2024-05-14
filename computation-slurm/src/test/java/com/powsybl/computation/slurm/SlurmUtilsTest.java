/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Yichen TANG {@literal <yichen.tang at rte-france.com>}
 */
class SlurmUtilsTest {

    @Test
    void test() {
        assertEquals("00-00:00:01", SlurmUtils.toTime(1));
        assertEquals("00-00:01:00", SlurmUtils.toTime(60));
        assertEquals("00-00:01:01", SlurmUtils.toTime(61));
        assertEquals("00-01:00:00", SlurmUtils.toTime(3600));
        assertEquals("01-00:00:00", SlurmUtils.toTime(3600 * 24));
        assertEquals("99-23:59:59", SlurmUtils.toTime(3600 * 24 * 100 - 1));
        try {
            SlurmUtils.toTime(0);
            fail();
        } catch (Exception ignored) {
            // ignored
        }
        try {
            SlurmUtils.toTime(3600 * 24 * 100);
            fail();
        } catch (Exception ignored) {
            // ignored
        }
    }
}
