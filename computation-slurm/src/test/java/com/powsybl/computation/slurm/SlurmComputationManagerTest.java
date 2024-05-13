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
import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.AbstractExecutionHandler;
import com.powsybl.computation.CommandExecution;
import com.powsybl.computation.ExecutionEnvironment;
import com.powsybl.computation.ExecutionReport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.powsybl.computation.slurm.CommandResultTestFactory.emptyResult;
import static com.powsybl.computation.slurm.CommandResultTestFactory.simpleOutput;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
class SlurmComputationManagerTest {

    private FileSystem fileSystem;
    private Path localDir;

    @Test
    void test() throws IOException {
        SlurmComputationConfig config = mock(SlurmComputationConfig.class);
        when(config.getWorkingDir()).thenReturn("/work");
        ExecutorService executorService = Executors.newCachedThreadPool();
        CommandExecutor runner = mock(CommandExecutor.class);
        when(runner.execute(anyString())).thenReturn(emptyResult());
        when(runner.execute("scontrol --version")).thenReturn(simpleOutput("slurm 19.05.0"));

        SlurmComputationManager sut = new SlurmComputationManager(config, executorService, runner, fileSystem, localDir);

        assertSame(executorService, sut.getExecutor());
        assertSame(runner, sut.getCommandRunner());
        assertSame(localDir, sut.getLocalDir());
        assertEquals("slurm 19.05.0", sut.getVersion());
        assertNotNull(sut.getTaskStore());

        Path flagDir = sut.getFlagDir();
        Assertions.assertThat(flagDir).exists();

        CompletableFuture<String> normal = sut.execute(ExecutionEnvironment.createDefault(), new AbstractExecutionHandler<String>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return Collections.emptyList();
            }

            @Override
            public String after(Path workingDir, ExecutionReport report) {
                return "OK";
            }
        });
        String join = normal.join();
        assertEquals("OK", join);

        CompletableFuture<String> afterException = sut.execute(ExecutionEnvironment.createDefault(), new AbstractExecutionHandler<String>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return Collections.emptyList();
            }

            @Override
            public String after(Path workingDir, ExecutionReport report) {
                throw new PowsyblException("test");
            }
        });
        try {
            afterException.join();
            fail();
        } catch (Exception e) {
            assertInstanceOf(CompletionException.class, e);
        }
    }

    @Test
    void testNotInstalled() {
        SlurmComputationConfig config = mock(SlurmComputationConfig.class);
        when(config.getWorkingDir()).thenReturn("/work");
        ExecutorService executorService = mock(ExecutorService.class);
        CommandExecutor runner = mock(CommandExecutor.class);
        when(runner.execute(anyString())).thenReturn(emptyResult());
        when(runner.execute("squeue --help")).thenReturn(new CommandResult(42, "not found", "error"));
        Assertions.assertThatThrownBy(() -> new SlurmComputationManager(config, executorService, runner, fileSystem, localDir))
                .isInstanceOf(SlurmException.class)
                .hasMessage("Slurm is not installed. 'squeue --help' failed with code 42");
    }

    @BeforeEach
    public void setUp() {
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        localDir = fileSystem.getPath("/local");
    }

    @AfterEach
    public void tearDown() throws IOException {
        fileSystem.close();
    }
}
