/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

import static com.powsybl.computation.slurm.CommandResultTestFactory.emptyResult;
import static com.powsybl.computation.slurm.CommandResultTestFactory.simpleOutput;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmComputationManagerTest {

    private FileSystem fileSystem;
    private Path localDir;

    @Test
    public void test() throws IOException {
        SlurmComputationConfig config = mock(SlurmComputationConfig.class);
        when(config.getWorkingDir()).thenReturn("/work");
        ExecutorService executorService = mock(ExecutorService.class);
        CommandExecutor runner = mock(CommandExecutor.class);
        when(runner.execute(anyString())).thenReturn(emptyResult());
        when(runner.execute(eq("scontrol --version"))).thenReturn(simpleOutput("slurm 19.05.0"));

        SlurmComputationManager sut = new SlurmComputationManager(config, executorService, runner, fileSystem, localDir);

        assertSame(executorService, sut.getExecutor());
        assertSame(runner, sut.getCommandRunner());
        assertSame(localDir, sut.getLocalDir());
        assertEquals("slurm 19.05.0", sut.getVersion());
        assertNotNull(sut.getTaskStore());

        Path flagDir = sut.getFlagDir();
        Assertions.assertThat(flagDir).exists();
    }

    @Test
    public void testNotInstalled() {
        SlurmComputationConfig config = mock(SlurmComputationConfig.class);
        when(config.getWorkingDir()).thenReturn("/work");
        ExecutorService executorService = mock(ExecutorService.class);
        CommandExecutor runner = mock(CommandExecutor.class);
        when(runner.execute(anyString())).thenReturn(emptyResult());
        when(runner.execute(eq("sacct --help"))).thenReturn(new CommandResult(42, "not found", "error"));
        Assertions.assertThatThrownBy(() -> new SlurmComputationManager(config, executorService, runner, fileSystem, localDir))
                .isInstanceOf(SlurmException.class)
                .hasMessage("Slurm is not installed. 'sacct --help' failed with code 42");
    }

    @Before
    public void setUp() {
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        localDir = fileSystem.getPath("/local");
    }

    @After
    public void tearDown() throws IOException {
        fileSystem.close();
    }
}
