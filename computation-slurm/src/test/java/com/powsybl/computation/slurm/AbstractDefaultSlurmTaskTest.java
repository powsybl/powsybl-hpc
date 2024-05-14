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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yichen TANG {@literal <yichen.tang at rte-france.com>}
 */
public abstract class AbstractDefaultSlurmTaskTest {

    protected FileSystem fileSystem;
    protected Path flagPath;
    protected Path workingPath;

    @BeforeEach
    public void setUp() throws IOException {
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        flagPath = fileSystem.getPath("/tmp/flags");
        workingPath = fileSystem.getPath("/home/test/workingPath_12345");
        Files.createDirectories(workingPath);
        Files.createDirectories(flagPath);
    }

    @AfterEach
    public void tearDown() throws IOException {
        fileSystem.close();
    }

    final SlurmComputationManager mockScm(CommandExecutor runner) {
        SlurmComputationManager scm = mock(SlurmComputationManager.class);
        when(scm.getFlagDir()).thenReturn(flagPath);
        when(scm.getCommandRunner()).thenReturn(runner);
        return scm;
    }

    final WorkingDirectory mockWd() {
        WorkingDirectory workingDirectory = mock(WorkingDirectory.class);
        when(workingDirectory.toPath()).thenReturn(workingPath);
        return workingDirectory;
    }
}
