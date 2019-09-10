/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SbatchCmdTest {

    @Test
    public void testDeadline() {
        SbatchCmd sbatchCmd = new SbatchCmd("foo.batch");
        assertEquals("sbatch foo.batch", sbatchCmd.toString());

        sbatchCmd.deadLine(10);
        assertEquals("sbatch --deadline=`date -d \"10 seconds\" \"+%Y-%m-%dT%H:%M:%S\"` foo.batch", sbatchCmd.toString());
    }
}
