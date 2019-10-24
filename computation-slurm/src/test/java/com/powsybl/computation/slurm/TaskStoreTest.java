/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class TaskStoreTest {

    private static final String DIR_NAME = "a_working_dir";

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
    static TaskStore generateTaskStore(String workingDir, boolean checkTracing) {
        TaskCounter counter = mock(TaskCounter.class);

        TaskStore taskStore = new TaskStore();
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
        TaskStore taskStore = generateTaskStore(DIR_NAME, false);
        assertEquals(Arrays.asList(3L, 6L), taskStore.getDependentJobs(1L));
        assertEquals(Collections.singletonList(6L), taskStore.getDependentJobs(3L));
        assertTrue(taskStore.getDependentJobs(6L).isEmpty());
        assertEquals(Collections.singletonList(2L), taskStore.getBatchIds(1L));
        assertEquals(Arrays.asList(4L, 5L), taskStore.getBatchIds(3L));
        assertTrue(taskStore.getBatchIds(6L).isEmpty());
    }

    @Test
    public void testGetDirNameFromJobId() {
        TaskStore taskStore = generateTaskStore(DIR_NAME, false);
        assertEquals(DIR_NAME, taskStore.getDirNameByJobId(1L).orElse(null));
        assertEquals(DIR_NAME, taskStore.getDirNameByJobId(2L).orElse(null));
        assertEquals(DIR_NAME, taskStore.getDirNameByJobId(3L).orElse(null));
        assertEquals(DIR_NAME, taskStore.getDirNameByJobId(4L).orElse(null));
        assertEquals(DIR_NAME, taskStore.getDirNameByJobId(5L).orElse(null));
        assertEquals(DIR_NAME, taskStore.getDirNameByJobId(6L).orElse(null));
    }

    @Test
    public void testTracing() {
        generateTaskStore(DIR_NAME, true);
    }

    @Test
    public void testCleanIdMaps() {
        TaskStore taskStore = generateTaskStore(DIR_NAME, false);
        taskStore.cleanIdMaps(1L);
    }

    @Test
    public void testExceptionMap() {
        TaskStore taskStore = new TaskStore();
        SlurmException e = new SlurmException("mock");
        taskStore.insertException(DIR_NAME, e);
        assertSame(e, taskStore.getExceptionAndClean(DIR_NAME).orElseGet(() -> new SlurmException("another")));
        assertFalse(taskStore.getExceptionAndClean(DIR_NAME).isPresent());
    }

    @Test
    public void testCancellingID() {
        TaskStore taskStore = new TaskStore();
        UUID id = UUID.randomUUID();
        taskStore.cancelCallable(id);
        assertTrue(taskStore.isCancelledThenClean(id));
        assertFalse(taskStore.isCancelledThenClean(id));
    }
}
