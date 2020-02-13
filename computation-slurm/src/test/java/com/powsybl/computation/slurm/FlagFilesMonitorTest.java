/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.junit.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import static com.powsybl.computation.slurm.CommandResultTestFactory.multilineOutput;
import static com.powsybl.computation.slurm.SlurmTaskTest.mockSubmittedTask;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class FlagFilesMonitorTest {

    @Test
    public void test() {
        String wdName = "workingDir_1234";
        CommandExecutor runner = mock(CommandExecutor.class);
        when(runner.execute(eq("ls -1 /flagdir")))
                .thenReturn(multilineOutput(Arrays.asList("mydone_" + wdName + "_1",
                        "myerror_" + wdName + "_2")));

        TaskStore taskStore = mock(TaskStore.class);
        SlurmTask task = mockSubmittedTask(runner);
        when(taskStore.getTask(eq(wdName))).thenReturn(Optional.of(task));

        Path flagDir = mock(Path.class);
        when(flagDir.toString()).thenReturn("/flagdir");
        SlurmComputationManager cm = mock(SlurmComputationManager.class);
        when(cm.getTaskStore()).thenReturn(taskStore);
        when(cm.getCommandRunner()).thenReturn(runner);
        when(cm.getFlagDir()).thenReturn(flagDir);

        FlagFilesMonitor sut = new FlagFilesMonitor(cm);
        sut.run();

        verify(runner, times(1)).execute(eq("rm /flagdir/mydone_workingDir_1234_1"));
        verify(runner, times(1)).execute(eq("rm /flagdir/myerror_workingDir_1234_2"));
        verify(runner, times(1)).execute(eq("scancel 1"));
        verify(runner, times(1)).execute(eq("scancel 2"));
        verify(runner, times(1)).execute(eq("scancel 3"));
        verify(runner, times(1)).execute(eq("scancel 4"));
        verify(runner, times(1)).execute(eq("scancel 5"));
        verify(runner, times(1)).execute(eq("scancel 6"));

        assertTrue(task.getTracingIds().isEmpty());
    }
}
