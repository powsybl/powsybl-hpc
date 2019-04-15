/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class TaskStoreTest {

    @Test
    public void testParallelInserts() {
        TaskStore taskStore = new TaskStore();
        long nbPerThread = 10000;
        int threadSize = 10;
        List<Thread> threadPools = new ArrayList<>(threadSize);
        for (int i = 0; i < threadSize; i++) {
            int k = i;
            Thread t = new Thread(() -> {
                for (long l = k * nbPerThread; l < (k + 1) * nbPerThread; l++) {
                    long v = l + 1;
                    taskStore.insertDependency(l, v);
                    taskStore.insertBatchIds(l, v);
                }
            });
            t.start();
            threadPools.add(t);
        }
        threadPools.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                assert false;
            }
        });
        long toTestId = 1L;
        assertEquals(nbPerThread * threadSize - toTestId, taskStore.getDependentJobs(toTestId).size());
    }

    // 1←2
    // ↑
    // 3←4,5
    // ↑
    // 6
    static TaskStore generateTaskStore(SlurmComputationManager.SlurmCompletableFuture future, boolean checkTracing) {
        String workingDir = "a_working_dir";
        TaskCounter counter = mock(TaskCounter.class);

        TaskStore taskStore = new TaskStore();
        taskStore.insert(workingDir, future);
        taskStore.insert(workingDir, counter, 1L);
        taskStore.insertBatchIds(1L, 1L);
        if (checkTracing) {
            assertEquals(Collections.singleton(1L), taskStore.getTracingIds());
        }
        taskStore.insertBatchIds(1L, 2L);
        if (checkTracing) {
            assertEquals(2, taskStore.getTracingIds().size());
        }
        taskStore.insertDependency(1L, 3L);
        taskStore.insertBatchIds(3L, 3L);
        taskStore.insertBatchIds(3L, 4L);
        if (checkTracing) {
            assertEquals(4, taskStore.getTracingIds().size());
        }
        taskStore.insertBatchIds(3L, 5L);
        if (checkTracing) {
            assertEquals(5, taskStore.getTracingIds().size());
        }
        taskStore.insertDependency(3L, 6L);
        taskStore.insertBatchIds(6L, 6L);
        if (checkTracing) {
            assertEquals(6, taskStore.getTracingIds().size());
        }
        return taskStore;
    }

    @Test
    public void test() {
        TaskStore taskStore = generateTaskStore(mock(SlurmComputationManager.SlurmCompletableFuture.class), false);
        assertEquals(Arrays.asList(3L, 6L), taskStore.getDependentJobs(1L));
        assertEquals(Collections.singletonList(6L), taskStore.getDependentJobs(3L));
        assertTrue(taskStore.getDependentJobs(6L).isEmpty());
        assertEquals(Collections.singletonList(2L), taskStore.getBatchIds(1L));
        assertEquals(Arrays.asList(4L, 5L), taskStore.getBatchIds(3L));
        assertTrue(taskStore.getBatchIds(6L).isEmpty());
    }

    @Test
    public void testRemove() {
        SlurmComputationManager.SlurmCompletableFuture future = mock(SlurmComputationManager.SlurmCompletableFuture.class);
        TaskStore taskStore = generateTaskStore(future, false);
        assertEquals(1L, taskStore.getFirstJobId(future).longValue());
        assertNotNull(taskStore.getTaskCounter(future));
        assertEquals(future, taskStore.getCompletableFuture("a_working_dir"));

        taskStore.clearBy(future);

        assertNull(taskStore.getTaskCounter(future));
        assertNull(taskStore.getFirstJobId(future));
        assertNull(taskStore.getCompletableFuture("a_working_dir"));
        assertTrue(taskStore.getDependentJobs(1L).isEmpty());
        assertTrue(taskStore.getBatchIds(3L).isEmpty());
        // tracing ids are cleaned by 1. mydone_ in flag monitor 2. scancel in scm
        assertFalse(taskStore.getTracingIds().isEmpty());
    }

    @Test
    public void testGetFutureFromJobId() {
        SlurmComputationManager.SlurmCompletableFuture future = mock(SlurmComputationManager.SlurmCompletableFuture.class);
        TaskStore taskStore = generateTaskStore(future, false);
        assertEquals(future, taskStore.getCompletableFutureByJobId(1L).orElse(null));
        assertEquals(future, taskStore.getCompletableFutureByJobId(2L).orElse(null));
        assertEquals(future, taskStore.getCompletableFutureByJobId(3L).orElse(null));
        assertEquals(future, taskStore.getCompletableFutureByJobId(4L).orElse(null));
        assertEquals(future, taskStore.getCompletableFutureByJobId(5L).orElse(null));
        assertEquals(future, taskStore.getCompletableFutureByJobId(6L).orElse(null));
    }

    @Test
    public void testTracing() {
        generateTaskStore(mock(SlurmComputationManager.SlurmCompletableFuture.class), true);
    }
}
