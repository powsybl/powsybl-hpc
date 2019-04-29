/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.powsybl.computation.Command;
import com.powsybl.computation.CommandExecution;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.powsybl.computation.slurm.CommandExecutionsTestFactory.oddEvenCmd;
import static org.junit.Assert.assertEquals;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class SbatchScriptGeneratorTest {

    private FileSystem fileSystem;
    private Path flagPath;
    private Path workingPath;

    @Before
    public void setUp() {
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        flagPath = fileSystem.getPath("/tmp/flags");
        workingPath = fileSystem.getPath("/home/test/workingPath_12345");
    }

    @After
    public void tearDown() throws Exception {
        fileSystem.close();
    }

    @Test
    public void testSimpleCmd() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.simpleCmd();
        List<String> shell = new SbatchScriptGenerator(flagPath, commandExecutions.get(0), workingPath, Collections.emptyMap()).parse();
        assertEquals(ImmutableList.of("#!/bin/sh",
                "echo \"test\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi"), shell);
    }

    @Test
    public void testLastSimpleCmd() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.simpleCmd();
        List<String> shell = new SbatchScriptGenerator(flagPath, commandExecutions.get(0), workingPath, Collections.emptyMap())
                .setIsLast(true).parse();
        assertEquals(ImmutableList.of("#!/bin/sh",
                "echo \"test\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi",
                "touch /tmp/flags/mydone_workingPath_12345_$SLURM_JOBID"), shell);
    }

    @Test
    public void testSimpleCmdWithCount() throws IOException, URISyntaxException {
        assertCommandExecutionToShell(CommandExecutionsTestFactory.simpleEchoWithCount(3).get(0), "simpleCmdWithCount3.batch");
    }

    @Test
    public void testMyEchoSimpleCmd() throws IOException, URISyntaxException {
        assertCommandExecutionToShell(CommandExecutionsTestFactory.myEchoSimpleCmdWithUnzipZip(3).get(0), "myEchoSimpleCmdWithUnzipZip.batch");
    }

    @Test
    public void testCommandFiles() throws IOException, URISyntaxException {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.commandFiles(3);
        assertCommandExecutionToShell(commandExecutions.get(0), "commandFiles.batch");
    }

    @Test
    public void testOnlyUnzipBatch() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.commandFiles(3);
        Command command = commandExecutions.get(0).getCommand();
        List<String> shell = SbatchScriptGenerator.unzipCommonInputFiles(command);
        assertEquals(expectedtestOnlyUnzipBatch(), shell);
    }

    private static List<String> expectedtestOnlyUnzipBatch() {
        List<String> shell = new ArrayList<>();
        shell.add("#!/bin/sh");
        shell.add("unzip -o -q foo.zip");
        return shell;
    }

    @Test
    public void testGroupCmd() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.groupCmd();
        List<String> shell = new SbatchScriptGenerator(flagPath, commandExecutions.get(0), workingPath, Collections.emptyMap()).parse();
        assertEquals(ImmutableList.of("#!/bin/sh",
                "sleep \"5s\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi",
                "echo \"sub2\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi"), shell);
    }

    @Test
    public void testGroupCmdWithArgWithCount() throws IOException, URISyntaxException {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.groupCmdWithArgs(3);
        CommandExecution commandExecution = commandExecutions.get(0);
        assertCommandExecutionToShell(commandExecution, "groupCmdWithArgsCount3.batch");
    }

    @Test
    public void testSbatchGenerator() throws URISyntaxException, IOException {
        assertCommandExecutionToShell(oddEvenCmd(3).get(0), "simpleArrayJobWithArgu.batch");
    }

    private void assertCommandExecutionToShell(CommandExecution commandExecution, String expected) throws URISyntaxException, IOException {
        SbatchScriptGenerator shellGenerator = new SbatchScriptGenerator(flagPath, commandExecution, workingPath, Collections.emptyMap());
        List<String> shell = shellGenerator.parse();
        List<String> expectedShell = Files.readAllLines(Paths.get(this.getClass().getResource("/expectedShell/" + expected).toURI()));
        assertEquals(expectedShell, shell);
    }
}
