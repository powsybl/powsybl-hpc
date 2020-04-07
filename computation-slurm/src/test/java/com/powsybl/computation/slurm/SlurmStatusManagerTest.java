/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.startsWith;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmStatusManagerTest {

    @Test
    public void test() {
        CommandResult r1 = CommandResultTestFactory.simpleOutput("2009-01-09 03:54:42");
        CommandResult r2 = CommandResultTestFactory.simpleOutput("0/32/0/32");
        CommandResult r3 = CommandResultTestFactory.multilineOutput(Arrays.asList("1 cmd2", "8 simpleCmdId"));
        CommandExecutor commandExecutor = Mockito.mock(CommandExecutor.class);
        Mockito.when(commandExecutor.execute(startsWith("date"))).thenReturn(r1);
        Mockito.when(commandExecutor.execute(startsWith("sinfo"))).thenReturn(r2);
        Mockito.when(commandExecutor.execute(startsWith("squeue"))).thenReturn(r3);
        SlurmStatusManager statusManager = new SlurmStatusManager(commandExecutor);
        SlurmComputationResourcesStatus resourcesStatus = statusManager.getResourcesStatus();
        DateTime date = statusManager.getResourcesStatus().getDate();
        assertEquals(9, date.getDayOfMonth());
        assertEquals(0, resourcesStatus.getBusyCores());
        assertEquals(32, resourcesStatus.getAvailableCores());
        Map<String, Integer> map = ImmutableMap.of("cmd2", 1, "simpleCmdId", 8);
        assertEquals(map, resourcesStatus.getBusyCoresPerApp());
    }
}
