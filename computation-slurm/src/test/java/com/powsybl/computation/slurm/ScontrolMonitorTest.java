/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class ScontrolMonitorTest {

    private SlurmComputationManager slurm;
    private TaskStore ts;
    private List<MockJob> jobs;
    private CommandExecutor cm;

    private CommandResult runningResult = new CommandResult(0, "JobState=RUNNING", "");
    private CommandResult cancelledResult = new CommandResult(0, "JobState=CANCELLED", "");

    @Test
    public void testAllRunning() {
        when(cm.execute(anyString())).thenReturn(runningResult);
        ScontrolMonitor monitor = new ScontrolMonitor(slurm);
        assertFalse(ts.getPendingJobs().isEmpty());

        assertEquals(0, jobs.stream().filter(MockJob::isDone).count());
        assertEquals(0, jobs.stream().filter(MockJob::isFailed).count());

        monitor.run();

        assertEquals(0, jobs.stream().filter(MockJob::isDone).count());
        assertEquals(0, jobs.stream().filter(MockJob::isFailed).count());
    }

    @Test
    public void testUnormalFound() {

        // job 3 is scancelled while 1, 2 are running
        when(cm.execute(anyString())).thenReturn(runningResult);
        when(cm.execute(Matchers.endsWith("3"))).thenReturn(cancelledResult);

        ScontrolMonitor monitor = new ScontrolMonitor(slurm);

        assertEquals(0, jobs.stream().filter(MockJob::isDone).count());
        assertEquals(0, jobs.stream().filter(MockJob::isFailed).count());

        monitor.run();

        assertEquals(0, jobs.stream().filter(MockJob::isDone).count());
        assertTrue(jobs.get(2).isInterrupted());
    }

    @Before
    public void setup() {
        slurm = mock(SlurmComputationManager.class);
        ts = mock(TaskStore.class);
        cm = mock(CommandExecutor.class);
        jobs = mockJobs();
        when(slurm.getTaskStore()).thenReturn(ts);
        when(slurm.getCommandRunner()).thenReturn(cm);
    }

    private List<MockJob> mockJobs() {
        List<MockJob> jobs = LongStream.range(1, 7)
                .mapToObj(MockJob::new)
                .collect(Collectors.toList());
        when(ts.getPendingJobs()).thenReturn(ImmutableList.copyOf(jobs));
        return jobs;
    }

    private static final class MockJob implements MonitoredJob {

        private final long id;
        private boolean done = false;
        private boolean failed = false;
        private boolean interrupted = false;

        private MockJob(long id) {
            this.id = id;
        }

        /**
         * This job ID in slurm
         */
        @Override
        public long getJobId() {
            return id;
        }

        /**
         * To be called by a monitor when the job has ended successfully.
         */
        @Override
        public void done() {
            done = true;
        }

        /**
         * To be called by a monitor when the job has failed.
         */
        @Override
        public void failed() {
            failed = true;
        }

        /**
         * To be called if the job is detected to have been killed
         * before completing.
         */
        @Override
        public void interrupted() {
            interrupted = true;
        }

        public boolean isDone() {
            return done;
        }

        public boolean isFailed() {
            return failed;
        }

        public boolean isInterrupted() {
            return interrupted;
        }
    }
}
