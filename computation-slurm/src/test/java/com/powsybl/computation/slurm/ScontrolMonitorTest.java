/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.stream.IntStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class ScontrolMonitorTest {

    private SlurmComputationManager slurm;
    private TaskStore ts;
    private CommandExecutor cm;

    private CommandResult runningResult = new CommandResult(0, "JobState=RUNNING", "");
    private CommandResult cancelledResult = new CommandResult(0, "JobState=CANCELLED", "");

    @Test
    public void testAllRunning() {
        when(cm.execute(anyString())).thenReturn(runningResult);
        ScontrolMonitor monitor = new ScontrolMonitor(slurm);
        monitor.run();
        assertFalse(ts.getTracingIds().isEmpty());
    }

    @Test
    public void testUnormalFound() {

        // job 3 is scancelled while 1, 2 are running
        when(cm.execute(anyString())).thenReturn(runningResult);
        when(cm.execute(Matchers.endsWith("3"))).thenReturn(cancelledResult);

        ScontrolMonitor monitor = new ScontrolMonitor(slurm);
        monitor.run();
        assertTrue(ts.getTracingIds().isEmpty());
        // check scancel all first jobs(1 and 2) only once
        verify(cm, times(1)).execute("scancel 1");
        verify(cm, times(1)).execute("scancel 2");
        // no need to send scancel on following jobs
        IntStream.range(3, 7).forEach(i -> verify(cm, never()).execute("scancel " + i));
    }

    @Before
    public void setup() {
        SlurmComputationManager.Mycf mycf;
        slurm = mock(SlurmComputationManager.class);
        mycf = new SlurmComputationManager.Mycf(slurm);
        mycf.setThread(new Thread());
        ts = new TaskStore(15);
        ts.add(SlurmTaskTest.mockSubmittedTask(mycf));
        Whitebox.setInternalState(slurm, "taskStore", ts);
        when(slurm.getTaskStore()).thenReturn(ts);
        cm = mock(CommandExecutor.class);
        Whitebox.setInternalState(slurm, "commandRunner", cm);
        when(slurm.getCommandRunner()).thenReturn(cm);
    }
}
