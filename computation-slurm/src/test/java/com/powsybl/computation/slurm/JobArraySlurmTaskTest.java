/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import com.powsybl.computation.ComputationParameters;
import com.powsybl.computation.ExecutionEnvironment;
import com.powsybl.computation.ExecutionError;
import com.powsybl.computation.ExecutionReport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static com.powsybl.computation.slurm.CommandExecutionsTestFactory.md5sumLargeFile;
import static com.powsybl.computation.slurm.CommandResultTestFactory.simpleOutput;
import static com.powsybl.computation.slurm.SlurmTaskTest.getPendingJob;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yichen TANG {@literal <yichen.tang at rte-france.com>}
 */
class JobArraySlurmTaskTest extends AbstractDefaultSlurmTaskTest {

    @Test
    void testCounterSum() throws IOException {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        when(commandExecutor.execute(startsWith("sbatch")))
                .thenReturn(simpleOutput("Submitted batch job 1"))
                .thenReturn(simpleOutput("Submitted batch job 2"))
                .thenReturn(simpleOutput("Submitted batch job 5"))
                .thenReturn(simpleOutput("Submitted batch job 6"));
        SlurmTask task = new JobArraySlurmTask(mockScm(commandExecutor), mockWd(), md5sumLargeFile(), ComputationParameters.empty(), ExecutionEnvironment.createDefault());
        task.submit();
        assertEquals(4, task.getPendingJobs().size());
    }

    @Test
    void testSubmit231() throws ExecutionException, InterruptedException {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        SlurmTask task = new JobArraySlurmTask(mockScm(commandExecutor), mockWd(), CommandExecutionsTestFactory.longProgramInList(2, 3, 1), ComputationParameters.empty(), ExecutionEnvironment.createDefault());
        when(commandExecutor.execute(startsWith("sbatch")))
                .thenReturn(simpleOutput("Submitted batch job 1"))
                .thenReturn(simpleOutput("Submitted batch job 3"))
                .thenReturn(simpleOutput("Submitted batch job 6"))
                .thenThrow(new IllegalArgumentException("no more then 3 times."));
        try {
            task.submit();
            assertEquals(3, task.getPendingJobs().size());
        } catch (Exception e) {
            fail();
        }

        when(commandExecutor.execute(startsWith("scontrol show job 1"))).thenReturn(simpleOutput("JobId=1 0:0"));
        when(commandExecutor.execute(startsWith("scontrol show job 3"))).thenReturn(simpleOutput("JobId=3 ArrayTaskId=1 ExitCode=127:0"));
        when(commandExecutor.execute(startsWith("scontrol show job 6"))).thenReturn(simpleOutput("JobId=6 0:0"));
        getPendingJob(task, 3).failed();
        ExecutionReport report = task.await();
        assertEquals(1, report.getErrors().size());
        ExecutionError error = report.getErrors().get(0);
        assertEquals("tLP2", error.getCommand().getId());
        assertEquals(127, error.getExitCode());
        assertEquals(1, error.getIndex());
    }

    @Test
    void testCannotSubmitTask() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        SlurmComputationManager slurmComputationManager = mockScm(commandExecutor);
        when(slurmComputationManager.isCloseStarted()).thenReturn(true);
        SlurmTask task = new JobArraySlurmTask(slurmComputationManager, mockWd(), CommandExecutionsTestFactory.longProgramInList(2, 3, 1), ComputationParameters.empty(), ExecutionEnvironment.createDefault());
        when(commandExecutor.execute(startsWith("sbatch")))
            .thenReturn(simpleOutput("Submitted batch job 1"))
            .thenReturn(simpleOutput("Submitted batch job 3"))
            .thenReturn(simpleOutput("Submitted batch job 6"))
            .thenThrow(new IllegalArgumentException("no more then 3 times."));

        try {
            task.submit();
            assertEquals(0, task.getPendingJobs().size());
        } catch (Exception e) {
            fail();
        }
    }
}
