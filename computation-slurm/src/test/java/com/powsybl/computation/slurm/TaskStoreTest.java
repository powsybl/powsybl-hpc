/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Yichen Tang {@literal <yichen.tang at rte-france.com>}
 */
class TaskStoreTest {

    @Test
    void test() {
        SlurmTask task = mock(SlurmTask.class);
        MonitoredJob job = Mockito.mock(MonitoredJob.class);
        when(task.getPendingJobs()).thenReturn(Collections.singletonList(job));

        TaskStore store = new TaskStore();

        assertTrue(store.isEmpty());
        assertTrue(store.getTasks().isEmpty());
        assertTrue(store.getPendingJobs().isEmpty());

        store.add(task);

        assertFalse(store.isEmpty());
        assertEquals(1, store.getTasks().size());
        assertSame(task, store.getTasks().get(0));
        assertEquals(1, store.getPendingJobs().size());
        assertSame(job, store.getPendingJobs().get(0));

        when(task.getPendingJobs()).thenReturn(Collections.emptyList());
        assertTrue(store.getPendingJobs().isEmpty());

        store.remove(task);
        assertTrue(store.getTasks().isEmpty());
        assertTrue(store.getPendingJobs().isEmpty());
    }

    @Test
    void testInterrupt() {
        AtomicInteger jobsInterrupted = new AtomicInteger(0);
        // Task and job
        SlurmTaskImpl task = mock(SlurmTaskImpl.class);
        MonitoredJob job = Mockito.mock(MonitoredJob.class);
        when(task.getPendingJobs()).thenReturn(Collections.singletonList(job));
        doAnswer(invocation -> {
            jobsInterrupted.incrementAndGet();
            return null;
        }).when(task).interrupt();

        // Store
        TaskStore store = new TaskStore();
        store.add(task);

        // Test
        assertFalse(store.isEmpty());
        store.interruptAll();
        assertEquals(1, jobsInterrupted.get());
    }
}
