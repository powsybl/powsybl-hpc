/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.powsybl.computation.Command;
import com.powsybl.computation.DefaultExecutionReport;
import com.powsybl.computation.ExecutionReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
class SlurmExecutionReportTest {

    private static final String STD_OUT_PATH = "/workdir/cmdId_0.out";

    private static final String MOCK_OUTPUT = "some std output";

    private FileSystem fileSystem;

    private Path workingDir;

    @BeforeEach
    public void setUp() throws IOException {
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        workingDir = fileSystem.getPath("/workdir/");
        Files.createDirectory(workingDir);
        Files.createFile(fileSystem.getPath(STD_OUT_PATH));
    }

    @Test
    void test() {
        ExecutionReport sut = new DefaultExecutionReport(workingDir, Collections.emptyList());
        Command command = mock(Command.class);
        when(command.getId()).thenReturn("cmdId");
        write();
        Optional<InputStream> stdOut = sut.getStdOut(command, 0);
        assertTrue(stdOut.isPresent());
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stdOut.get()))) {
            assertEquals(MOCK_OUTPUT, br.readLine());
        } catch (IOException e) {
            fail();
        }
    }

    private void write() {
        try (BufferedWriter bw = Files.newBufferedWriter(fileSystem.getPath(STD_OUT_PATH))) {
            bw.write(MOCK_OUTPUT);
        } catch (Exception e) {
            fail();
        }
    }

    @AfterEach
    public void tearDown() throws IOException {
        fileSystem.close();
    }
}
