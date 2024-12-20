/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.powsybl.commons.io.WorkingDirectory;
import com.powsybl.computation.ComputationParameters;
import com.powsybl.computation.ExecutionEnvironment;
import com.powsybl.computation.ExecutionError;
import com.powsybl.computation.ExecutionReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.powsybl.computation.slurm.CommandResultTestFactory.simpleOutput;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yichen TANG {@literal <yichen.tang at rte-france.com>}
 */
class SlurmTaskTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlurmTaskTest.class);

    private FileSystem fileSystem;
    private Path flagPath;
    private Path workingPath;

    @BeforeEach
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
        assertEquals(Set.of(1L, 2L, 3L, 4L, 5L, 6L), getPendingJobsIds(task));
        assertEquals(Set.of(1L, 2L, 3L, 4L, 5L, 6L), task.getAllJobIds());

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
    void testCommands() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        SlurmTaskImpl task = new SlurmTaskImpl(mockScm(commandExecutor), mockWd(), CommandExecutionsTestFactory.md5sumLargeFile(), ComputationParameters.empty(), ExecutionEnvironment.createDefault());
        assertEquals(2, task.getCommandExecutionSize());
        assertEquals(3, task.getCommandExecution(0).getExecutionCount());
        assertEquals(1, task.getCommandExecution(1).getExecutionCount());
    }

    @Test
    void test() {
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
            LOGGER.error(e.getMessage(), e);
            fail();
        }

        when(commandExecutor.execute(startsWith("scontrol"))).thenReturn(CommandResultTestFactory.simpleOutput("JobId=1\n ExitCode=127:0"));
        ExecutionReport report = task.generateReport();
        assertFalse(report.getErrors().isEmpty());
        ExecutionError executionError = report.getErrors().get(0);
        assertEquals("tLP", executionError.getCommand().getId());
        assertEquals(127, executionError.getExitCode());
        assertEquals(0, executionError.getIndex());

        // untracing
        getPendingJob(task, 1L).done();
        getPendingJob(task, 2L).done();
        assertEquals(Set.of(3L, 4L, 5L, 6L), getPendingJobsIds(task));

        //interrupt
        task.interrupt();
        assertTrue(task.getPendingJobs().isEmpty());
    }

    @Test
    void testSubmitCommonUnzipFile() {
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
            assertEquals(Set.of(1L, 2L, 3L, 4L, 5L, 6L), getPendingJobsIds(task2));
            assertEquals(Arrays.asList(1L, 2L, 5L, 6L), task2.getMasters());
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            fail();
        }

        getPendingJob(task2, 1L).failed();
        assertTrue(task2.getPendingJobs().isEmpty());
    }

    @Test
    void baseTest() {
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

    @AfterEach
    public void tearDown() throws IOException {
        fileSystem.close();
    }

}
