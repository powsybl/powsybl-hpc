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

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class ScontrolMonitorTest {

    private SlurmComputationManager slurm;
    private TaskStore ts;
    private SlurmTask task;
    private CommandExecutor cm;

    private CommandResult runningResult = new CommandResult(0, "JobState=RUNNING", "");
    private CommandResult cancelledResult = new CommandResult(0, "JobState=CANCELLED", "");

    @Test
    public void testAllRunning() {
        when(cm.execute(anyString())).thenReturn(runningResult);
        ScontrolMonitor monitor = new ScontrolMonitor(slurm);
        assertFalse(ts.getTracingIds().isEmpty());
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
        // check scancel only once
        verify(task, times(1)).cancel();
    }

    @Before
    public void setup() {
        slurm = mock(SlurmComputationManager.class);
        ts = new TaskStore();
        cm = mock(CommandExecutor.class);
        task = mockSlurmTask();
        ts.add(task);
        when(slurm.getTaskStore()).thenReturn(ts);
        when(slurm.getCommandRunner()).thenReturn(cm);
    }

    private SlurmTask mockSlurmTask() {
        SlurmTask task = mock(SlurmTask.class);
        Set<Long> tracingIds = LongStream.range(1L, 7L).boxed().collect(Collectors.toSet());
        when(task.getId()).thenReturn("workingDir_1234");
        UUID uuid = UUID.randomUUID();
        when(task.getCallableId()).thenReturn(uuid);
        when(task.getTracingIds()).thenReturn(tracingIds);
        when(task.contains(eq(3L))).thenReturn(true);
        return task;
    }
}
