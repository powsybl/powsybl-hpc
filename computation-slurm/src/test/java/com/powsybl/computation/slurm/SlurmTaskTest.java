/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.commons.io.WorkingDirectory;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmTaskTest {

    static SlurmTask mockSubmittedTask(CompletableFuture cf) {
        // jimfs not support tmp dir
        WorkingDirectory workingDirectory = mock(WorkingDirectory.class);
        Path tmpPath = mock(Path.class);
        when(workingDirectory.toPath()).thenReturn(tmpPath);
        when(tmpPath.getFileName()).thenReturn(tmpPath);
        when(tmpPath.toString()).thenReturn("workingDir_1234");
        SlurmTask task = new SlurmTask(workingDirectory, Collections.emptyList(), cf);
        // 1←2
        // ↑
        // 3←4,5
        // ↑
        // 6
        task.newBatch(1L);
        task.newBatch(2L);
        task.setCurrentMasterNull();
        task.newBatch(3L);
        task.newBatch(4L);
        task.newBatch(5L);
        task.setCurrentMasterNull();
        task.newBatch(6L);
        return task;
    }

    @Test
    public void testIdsRelationship() {
        SlurmTask task = mockSubmittedTask(mock(CompletableFuture.class));
        List<Long> masters = task.getMasters();
        assertTrue(1L == task.getFirstJobId());
        assertEquals(Arrays.asList(1L, 3L, 6L), masters);
        assertEquals(new HashSet<>(Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L)), task.getTracingIds());
        assertTrue(task.contains(3L));
        assertFalse(task.contains(33L));
        assertEquals(new HashSet<>(Arrays.asList(1L, 2L)), task.getToCancelIds());

        // sub task
        assertEquals(Arrays.asList(4L, 5L), task.getBatches(3L));
        assertTrue(task.getBatches(6L).isEmpty());
    }

    @Test
    public void testCounterSum() {
        SlurmTask task = new SlurmTask(mock(WorkingDirectory.class), CommandExecutionsTestFactory.md5sumLargeFile(), mock(CompletableFuture.class));
        assertEquals(2, task.getCommandCount());
        assertEquals(3, task.getCommand(0).getExecutionCount());
        assertEquals(1, task.getCommand(1).getExecutionCount());
        // 3 + 1, common unzip job is not accounted
        assertEquals(4, task.getCounter().getJobCount());
    }

    @Test
    public void testCommonUnzipJob() {
        SlurmTask task = new SlurmTask(mock(WorkingDirectory.class), Collections.emptyList(), mock(CompletableFuture.class));
        // 1←2
        // ↑
        // 3 (common unzip job)
        // ↑
        // 4←5
        // ↑
        // 6
        task.newBatch(1L);
        task.newBatch(2L);
        task.setCurrentMasterNull();
        task.newCommonUnzipJob(3L);
        task.newBatch(4L);
        task.newBatch(5L);
        task.setCurrentMasterNull();
        task.newBatch(6L);
        List<Long> masters = task.getMasters();
        assertEquals(Arrays.asList(1L, 3L, 4L, 6L), masters);
    }

    @Test
    public void testSubmitting() {
        SlurmTask task = new SlurmTask(mock(WorkingDirectory.class), Collections.emptyList(), mock(CompletableFuture.class));
        // 1←2
        // ↑
        // 3←4,5
        // ↑
        // 6
        assertTrue(task.getPreJobIds().isEmpty());
        task.newBatch(1L);
        assertTrue(task.getPreJobIds().isEmpty());
        task.newBatch(2L);
        task.setCurrentMasterNull();
        assertEquals(Arrays.asList(1L, 2L), task.getPreJobIds());
        task.newBatch(3L);
        assertEquals(Arrays.asList(1L, 2L), task.getPreJobIds());
        task.newBatch(4L);
        assertEquals(Arrays.asList(1L, 2L), task.getPreJobIds());
        task.newBatch(5L);
        task.setCurrentMasterNull();
        assertEquals(Arrays.asList(3L, 4L, 5L), task.getPreJobIds());
        task.newBatch(6L);

        SlurmTask task2 = new SlurmTask(mock(WorkingDirectory.class), Collections.emptyList(), mock(CompletableFuture.class));
        // 1←2
        // ↑
        // 3 (common unzip job)
        // ↑
        // 4←5
        // ↑
        // 6
        assertTrue(task2.getPreJobIds().isEmpty());
        task2.newBatch(1L);
        assertTrue(task2.getPreJobIds().isEmpty());
        task2.newBatch(2L);
        task2.setCurrentMasterNull();
        assertEquals(Arrays.asList(1L, 2L), task2.getPreJobIds());
        task2.newCommonUnzipJob(3L);
        assertEquals(Collections.singletonList(3L), task2.getPreJobIds());
        task2.newBatch(4L);
        assertEquals(Collections.singletonList(3L), task2.getPreJobIds());
        task2.newBatch(5L);
        task2.setCurrentMasterNull();
        assertEquals(Arrays.asList(4L, 5L), task2.getPreJobIds());
        task2.newBatch(6L);
        List<Long> masters = task2.getMasters();
        assertEquals(Arrays.asList(1L, 3L, 4L, 6L), masters);
    }
}
