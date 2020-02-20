/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.computation.ComputationParameters;
import com.powsybl.computation.ExecutionEnvironment;
import com.powsybl.computation.ExecutionError;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static com.powsybl.computation.slurm.CommandExecutionsTestFactory.md5sumLargeFile;
import static com.powsybl.computation.slurm.CommandResultTestFactory.simpleOutput;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmArrayJobTaskTest extends DefaultSlurmTaskTest {

    @Test
    public void testCounterSum() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        UUID uuid = UUID.randomUUID();
        SlurmTask task = new SlurmArrayJobTask(uuid, mockScm(commandExecutor), mockWd(), md5sumLargeFile(), ComputationParameters.empty(), ExecutionEnvironment.createDefault());
        assertEquals(1, task.getJobCount());
    }

    @Test
    public void testSubmit231() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        UUID uuid = UUID.randomUUID();
        SlurmTask task = new SlurmArrayJobTask(uuid, mockScm(commandExecutor), mockWd(), CommandExecutionsTestFactory.longProgramInList(2, 3, 1), ComputationParameters.empty(), ExecutionEnvironment.createDefault());
        when(commandExecutor.execute(startsWith("sbatch")))
                .thenReturn(simpleOutput("Submitted batch job 1"))
                .thenReturn(simpleOutput("Submitted batch job 3"))
                .thenReturn(simpleOutput("Submitted batch job 6"))
                .thenThrow(new IllegalArgumentException("no more then 3 times."));
        try {
            task.submit();
            List<Long> masters = task.getMasters();
            assertEquals(1L, (long) task.getFirstJobId());
            assertEquals(Arrays.asList(1L, 3L, 6L), masters);
            assertEquals(new HashSet<>(Arrays.asList(1L, 3L, 6L)), task.getTracingIds());
            assertTrue(task.contains(3L));
            assertFalse(task.contains(0L));
            assertFalse(task.contains(33L));
            assertEquals(new HashSet<>(Arrays.asList(1L, 3L, 6L)), task.getAllJobIds());
        } catch (Exception e) {
            fail();
        }

        when(commandExecutor.execute(startsWith("sacct"))).thenReturn(simpleOutput("3_1  127:0"));
        SlurmExecutionReport report = task.generateReport();
        assertEquals(1, report.getErrors().size());
        ExecutionError error = report.getErrors().get(0);
        assertEquals("tLP2", error.getCommand().getId());
        assertEquals(127, error.getExitCode());
        assertEquals(1, error.getIndex());
    }
}
