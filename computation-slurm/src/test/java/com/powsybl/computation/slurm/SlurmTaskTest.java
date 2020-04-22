/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.powsybl.commons.io.WorkingDirectory;
import com.powsybl.computation.ComputationParameters;
import com.powsybl.computation.ExecutionEnvironment;
import com.powsybl.computation.ExecutionError;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.computation.slurm.CommandResultTestFactory.simpleOutput;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmTaskTest {

    private FileSystem fileSystem;
    private Path flagPath;
    private Path workingPath;

    @Before
    public void setUp() throws IOException {
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        flagPath = fileSystem.getPath("/tmp/flags");
        workingPath = fileSystem.getPath("/home/test/workingPath_12345");
        Files.createDirectories(workingPath);
        Files.createDirectories(flagPath);
    }

    private void testIdsRelationship(SlurmTaskImpl task) {
        List<Long> masters = task.getMasters();
        assertEquals(1L, (long) task.getFirstJobId());
        assertEquals(Arrays.asList(1L, 3L, 6L), masters);
        assertEquals(ImmutableSet.of(1L, 2L, 3L, 4L, 5L, 6L), getPendingJobsIds(task));
        assertEquals(ImmutableSet.of(1L, 2L, 3L, 4L, 5L, 6L), task.getAllJobIds());

        // sub task
        assertEquals(Arrays.asList(4L, 5L), task.getBatches(3L));
        assertTrue(task.getBatches(6L).isEmpty());

        assertEquals(3L, (long) task.getMasterId(4L));
    }

    private static Set<Long> getPendingJobsIds(SlurmTask task) {
        return task.getPendingJobs().stream()
                .map(MonitoredJob::getJobId)
                .collect(Collectors.toSet());
    }

    static MonitoredJob getPendingJob(SlurmTask task, long id) {
        return task.getPendingJobs().stream()
                .filter(job -> job.getJobId() == id)
                .findFirst()
                .orElseThrow(AssertionError::new);
    }

    @Test
    public void testCommands() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        SlurmTaskImpl task = new SlurmTaskImpl(mockScm(commandExecutor), mockWd(), CommandExecutionsTestFactory.md5sumLargeFile(), ComputationParameters.empty(), ExecutionEnvironment.createDefault());
        assertEquals(2, task.getCommandExecutionSize());
        assertEquals(3, task.getCommandExecution(0).getExecutionCount());
        assertEquals(1, task.getCommandExecution(1).getExecutionCount());
    }

    @Test
    public void test() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        SlurmTaskImpl task = new SlurmTaskImpl(mockScm(commandExecutor), mockWd(), CommandExecutionsTestFactory.longProgramInList(2, 3, 1), ComputationParameters.empty(), ExecutionEnvironment.createDefault());
        when(commandExecutor.execute(startsWith("sbatch")))
                .thenReturn(simpleOutput("Submitted batch job 1"))
                .thenReturn(simpleOutput("Submitted batch job 2"))
                .thenReturn(simpleOutput("Submitted batch job 3"))
                .thenReturn(simpleOutput("Submitted batch job 4"))
                .thenReturn(simpleOutput("Submitted batch job 5"))
                .thenReturn(simpleOutput("Submitted batch job 6"));
        // 1←2
        // ↑
        // 3←4,5
        // ↑
        // 6
        try {
            task.submit();
            testIdsRelationship(task);
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }

        when(commandExecutor.execute(startsWith("sacct"))).thenReturn(CommandResultTestFactory.simpleOutput("1 127:0"));
        SlurmExecutionReport report = task.generateReport();
        assertFalse(report.getErrors().isEmpty());
        ExecutionError executionError = report.getErrors().get(0);
        assertEquals("tLP", executionError.getCommand().getId());
        assertEquals(127, executionError.getExitCode());
        assertEquals(0, executionError.getIndex());

        // untracing
        getPendingJob(task, 1L).done();
        getPendingJob(task, 2L).done();
        assertEquals(ImmutableSet.of(3L, 4L, 5L, 6L), getPendingJobsIds(task));

        //interrupt
        task.interrupt();
        assertTrue(task.getPendingJobs().isEmpty());
    }

    @Test
    public void testSubmitCommonUnzipFile() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        SlurmTaskImpl task2 = new SlurmTaskImpl(mockScm(commandExecutor), mockWd(), CommandExecutionsTestFactory.md5sumLargeFile(), ComputationParameters.empty(), ExecutionEnvironment.createDefault());
        when(commandExecutor.execute(startsWith("sbatch")))
                .thenReturn(simpleOutput("Submitted batch job 1"))
                .thenReturn(simpleOutput("Submitted batch job 2"))
                .thenReturn(simpleOutput("Submitted batch job 3"))
                .thenReturn(simpleOutput("Submitted batch job 4"))
                .thenReturn(simpleOutput("Submitted batch job 5"))
                .thenReturn(simpleOutput("Submitted batch job 6"));
        // 1 (common unzip job)
        // ↑
        // 2←3,4
        // ↑
        // 5 (common unzip job)
        // ↑
        // 6
        try {
            task2.submit();
            assertEquals(ImmutableSet.of(1L, 2L, 3L, 4L, 5L, 6L), getPendingJobsIds(task2));
            assertEquals(Arrays.asList(1L, 2L, 5L, 6L), task2.getMasters());
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }

        getPendingJob(task2, 1L).failed();
        assertTrue(task2.getPendingJobs().isEmpty());
    }

    @Test
    public void baseTest() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        WorkingDirectory directory = mock(WorkingDirectory.class);
        Path path = mock(Path.class);
        when(directory.toPath()).thenReturn(path);
        SlurmTaskImpl task = new SlurmTaskImpl(mockScm(commandExecutor), directory, Collections.emptyList(), ComputationParameters.empty(), ExecutionEnvironment.createDefault());
        assertEquals(path, task.getWorkingDirPath());
    }

    private SlurmComputationManager mockScm(CommandExecutor runner) {
        SlurmComputationManager scm = mock(SlurmComputationManager.class);
        when(scm.getFlagDir()).thenReturn(flagPath);
        when(scm.getCommandRunner()).thenReturn(runner);
        return scm;
    }

    private WorkingDirectory mockWd() {
        WorkingDirectory workingDirectory = mock(WorkingDirectory.class);
        when(workingDirectory.toPath()).thenReturn(workingPath);
        return workingDirectory;
    }

    @After
    public void tearDown() throws IOException {
        fileSystem.close();
    }

}
