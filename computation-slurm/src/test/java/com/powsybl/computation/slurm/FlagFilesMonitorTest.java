/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.powsybl.computation.slurm.CommandResultTestFactory.multilineOutput;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

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

        List<MonitoredJob> jobs = new ArrayList<>();
        for (long i = 1; i < 7; i++) {
            MonitoredJob job = mock(MonitoredJob.class);
            when(job.getJobId()).thenReturn(i);
            jobs.add(job);
        }
        when(taskStore.getPendingJobs()).thenReturn(jobs);

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
        verify(jobs.get(0), times(1)).done();
        verify(jobs.get(1), times(1)).failed();
    }
}
