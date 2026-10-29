/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import com.powsybl.computation.ComputationParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * @author Yichen TANG {@literal <yichen.tang at rte-france.com>}
 */
class SlurmComputationParametersTest {

    @Test
    void test() {
        ComputationParameters base = mock(ComputationParameters.class);
        SlurmComputationParameters sut = new SlurmComputationParameters(base, "aQos");
        assertSame(base, sut.getExtendable());
        assertEquals("aQos", sut.getQos().orElse("fail"));
        SlurmComputationParameters empty = new SlurmComputationParameters(base, null);
        assertFalse(empty.getQos().isPresent());
        SlurmComputationParameters empty2 = new SlurmComputationParameters(base, "   ");
        assertFalse(empty2.getQos().isPresent());
        SlurmComputationParameters sut2 = new SlurmComputationParameters(base, "aQos", 50);
        assertSame(base, sut2.getExtendable());
        assertEquals(50, sut2.getMem().orElse(-1));
        assertEquals(-1, new SlurmComputationParameters(base, "aQos", -5).getMem().orElse(-1));
        sut.setTimemin("foo", 3L);
        assertEquals(3L, sut.getTimemin("foo").orElse(4L));
    }

    @Test
    void testPriority() {
        ComputationParameters base = mock(ComputationParameters.class);
        SlurmComputationParameters sut = new SlurmComputationParameters(base, "aQos");
        String cmdId = "foo";
        sut.setNice(cmdId, 1);
        assertEquals(1, sut.getNice(cmdId).orElse(3));
        sut.setPriority(cmdId, 2);
        assertEquals(2, sut.getPriority(cmdId).orElse(3));
        assertThrows(IllegalArgumentException.class, () -> sut.setNice(cmdId, -99));
        assertThrows(IllegalArgumentException.class, () -> sut.setPriority(cmdId, -99));
    }
}
