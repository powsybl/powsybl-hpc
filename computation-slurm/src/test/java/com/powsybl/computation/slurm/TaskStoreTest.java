/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class TaskStoreTest {

    @Test
    public void test() {
        TaskStore store = new TaskStore();
        UUID uuid = UUID.randomUUID();
        SlurmTask task = mockTask();
        when(task.getCallableId()).thenReturn(uuid);
        store.add(task);
        assertSame(task, store.getTask("workingDir_1234").orElseThrow(RuntimeException::new));
        assertFalse(store.isEmpty());
        // store is cleanup in SlurmComputationManager

        assertTrue(store.untracing(1L));
        assertFalse(store.untracing(2L));
        verify(task, times(1)).untracing(1L);

        assertFalse(store.isCancelled(uuid));
        when(task.isCancel()).thenReturn(true);
        assertTrue(store.isCancelled(uuid));
        verify(task, times(1)).cancel();
    }

    @Test
    public void testException() {
        TaskStore store = new TaskStore();
        UUID uuid = UUID.randomUUID();
        SlurmTask task = mockTask();
        when(task.getCallableId()).thenReturn(uuid);
        store.add(task);

        SlurmException slurmException = mock(SlurmException.class);
        store.cancelCallable(1L, slurmException);
        assertSame(slurmException, store.getException(uuid).orElse(mock(SlurmException.class)));
    }

    private SlurmTask mockTask() {
        SlurmTask task = mock(SlurmTask.class);
        when(task.isCancel()).thenReturn(false);
        when(task.getId()).thenReturn("workingDir_1234");
        when(task.contains(1L)).thenReturn(true);
        when(task.contains(2L)).thenReturn(false);
        when(task.untracing(1L)).thenReturn(true);
        when(task.getCounter()).thenReturn(new TaskCounter(3));
        return task;
    }
}
