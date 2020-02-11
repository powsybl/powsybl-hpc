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
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmTaskTest {

    static SlurmTask mockSubmittedTask() {
        return mockSubmittedTask(mock(CommandExecutor.class));
    }

    static SlurmTask mockSubmittedTask(CommandExecutor commandExecutor) {
        // jimfs not support tmp dir
        WorkingDirectory workingDirectory = mock(WorkingDirectory.class);
        Path tmpPath = mock(Path.class);
        when(workingDirectory.toPath()).thenReturn(tmpPath);
        when(tmpPath.getFileName()).thenReturn(tmpPath);
        when(tmpPath.toString()).thenReturn("workingDir_1234");
        UUID uuid = UUID.randomUUID();
        SlurmTask task = new SlurmTask(uuid, commandExecutor, workingDirectory, Collections.emptyList());
        // 1←2
        // ↑
        // 3←4,5
        // ↑
        // 6
        task.newBatch(1L);
        task.newBatch(2L);
        task.setCurrentMasterNull();
        task.newBatch(3L);
        assertTrue(3L == task.getCurrentMaster());
        task.newBatch(4L);
        task.newBatch(5L);
        task.setCurrentMasterNull();
        task.newBatch(6L);
        return task;
    }

    @Test
    public void testIdsRelationship() {
        SlurmTask task = mockSubmittedTask();
        List<Long> masters = task.getMasters();
        assertTrue(1L == task.getFirstJobId());
        assertEquals(Arrays.asList(1L, 3L, 6L), masters);
        assertEquals(new HashSet<>(Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L)), task.getTracingIds());
        assertTrue(task.contains(3L));
        assertFalse(task.contains(0L));
        assertFalse(task.contains(33L));
        assertEquals(new HashSet<>(Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L)), task.getToCancelIds());

        // sub task
        assertEquals(Arrays.asList(4L, 5L), task.getBatches(3L));
        assertTrue(task.getBatches(6L).isEmpty());
    }

    @Test
    public void testCounterSum() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        UUID uuid = UUID.randomUUID();
        SlurmTask task = new SlurmTask(uuid, commandExecutor, mock(WorkingDirectory.class), CommandExecutionsTestFactory.md5sumLargeFile());
        assertEquals(2, task.getCommandExecutionSize());
        assertEquals(3, task.getCommandExecution(0).getExecutionCount());
        assertEquals(1, task.getCommandExecution(1).getExecutionCount());
        // 3 + 1, common unzip job is not accounted
        assertEquals(4, task.getJobCount());
    }

    @Test
    public void testCommonUnzipJob() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        UUID uuid = UUID.randomUUID();
        SlurmTask task = new SlurmTask(uuid, commandExecutor, mock(WorkingDirectory.class), Collections.emptyList());
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
        assertFalse(task.isCancel());
        task.error();
        assertTrue(task.isCancel());
    }

    @Test
    public void testSubmitting() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        UUID uuid = UUID.randomUUID();
        SlurmTask task = new SlurmTask(uuid, commandExecutor, mock(WorkingDirectory.class), Collections.emptyList());
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

        SlurmTask task2 = new SlurmTask(uuid, commandExecutor, mock(WorkingDirectory.class), Collections.emptyList());
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

    @Test
    public void testUntracing() {
        SlurmTask task = mockSubmittedTask();
        assertTrue(task.untracing(1L));
        assertTrue(task.untracing(2L));
        assertEquals(new HashSet<>(Arrays.asList(3L, 4L, 5L, 6L)), task.getTracingIds());
        assertFalse(task.untracing(1L));
    }

    @Test
    public void baseTest() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        UUID uuid = UUID.randomUUID();
        WorkingDirectory directory = mock(WorkingDirectory.class);
        Path path = mock(Path.class);
        when(directory.toPath()).thenReturn(path);
        SlurmTask task = new SlurmTask(uuid, commandExecutor, directory, Collections.emptyList());
        assertEquals(path, task.getWorkingDirPath());
    }

    @Test
    public void testCancel() {
        SlurmTask task = mockSubmittedTask();
        assertFalse(task.isCancel());
        task.cancel();
        assertTrue(task.isCancel());
    }

    @Test
    public void testError() {
        SlurmTask task = mockSubmittedTask();
        assertFalse(task.isCancel());
        task.error();
        assertTrue(task.isCancel());
    }

}
