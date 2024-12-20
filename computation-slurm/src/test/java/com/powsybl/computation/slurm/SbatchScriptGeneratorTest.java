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
import com.powsybl.computation.CommandExecution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Yichen Tang {@literal <yichen.tang at rte-france.com>}
 */
class SbatchScriptGeneratorTest {

    private FileSystem fileSystem;
    private Path flagPath;
    private Path workingPath;

    private final int commandIdx = 0;

    @BeforeEach
    public void setUp() {
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        flagPath = fileSystem.getPath("/tmp/flags");
        workingPath = fileSystem.getPath("/home/test/workingPath_12345");
    }

    @AfterEach
    public void tearDown() throws Exception {
        fileSystem.close();
    }

    @Test
    void testEnv() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.simpleCmd();
        CommandExecution commandExecution = commandExecutions.get(commandIdx);
        Command command = commandExecution.getCommand();
        Map<String, String> env = new HashMap<>();
        env.put("foo", "bar");
        env.put("FOO_PATH", "/home/foo/java");

        List<String> shell = new SbatchScriptGenerator(flagPath).parser(command, 0, workingPath, env);
        assertEquals(List.of("#!/bin/sh",
                "export FOO_PATH=/home/foo/java:$FOO_PATH; export foo=bar; ",
                "echo \"test\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi",
                "touch /tmp/flags/mydone_workingPath_12345_$SLURM_JOBID"), shell);
    }

    @Test
    void testSimpleCmd() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.simpleCmd();
        CommandExecution commandExecution = commandExecutions.get(commandIdx);
        Command command = commandExecution.getCommand();
        List<String> shell = new SbatchScriptGenerator(flagPath).parser(command, 0, workingPath, Collections.emptyMap());
        assertEquals(List.of("#!/bin/sh",
                "echo \"test\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi",
                "touch /tmp/flags/mydone_workingPath_12345_$SLURM_JOBID"), shell);
    }

    @Test
    void testArgsWithSpaces() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.argsWithSpaces(3);
        CommandExecution commandExecution = commandExecutions.get(commandIdx);
        Command command = commandExecution.getCommand();
        List<String> shell = new SbatchScriptGenerator(flagPath).parser(command, 0, workingPath, Collections.emptyMap());
        // not array job
        assertEquals(List.of("#!/bin/sh",
                "touch \"line 1,line 2\" \"v2\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi",
                "touch /tmp/flags/mydone_workingPath_12345_$SLURM_JOBID"), shell);
        assertCommandExecutionToShell(commandExecutions.get(0), "argsWithSpaces.batch");
    }

    @Test
    void testSimpleCmdWithCount() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.simpleCmdWithCount(3);
        CommandExecution commandExecution = commandExecutions.get(commandIdx);
        Command command = commandExecution.getCommand();
        List<String> shell = new SbatchScriptGenerator(flagPath).parser(command, 0, workingPath, Collections.emptyMap());
        assertEquals(List.of("#!/bin/sh",
                "echo \"te1st0\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi",
                "touch /tmp/flags/mydone_workingPath_12345_$SLURM_JOBID"), shell);

        assertCommandExecutionToShell(commandExecutions.get(0), "simpleCmdWithCount3.batch");
    }

    @Test
    void testMyEchoSimpleCmd() {
        String program = "/home/test/myecho.sh";

        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.myEchoSimpleCmdWithUnzipZip(3, program);
        CommandExecution commandExecution = commandExecutions.get(commandIdx);
        Command command = commandExecution.getCommand();
        List<String> shell = new SbatchScriptGenerator(flagPath).parser(command, 0, workingPath, Collections.emptyMap());
        assertEquals(List.of("#!/bin/sh",
                "unzip -o -q 'in0.zip'",
                "/home/test/myecho.sh \"in0\" \"out0\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi",
                "gzip 'out0'",
                "touch /tmp/flags/mydone_workingPath_12345_$SLURM_JOBID"), shell);

        assertCommandExecutionToShell(commandExecutions.get(0), "myEchoSimpleCmdWithUnzipZip.batch");
    }

    @Test
    void testCommandFiles() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.commandFiles(3);
        Command command = commandExecutions.get(0).getCommand();
        List<String> shell = new SbatchScriptGenerator(flagPath).parser(command, 2, workingPath, Collections.emptyMap());
        assertEquals(expectedTestCommandFilesBatch(), shell);
    }

    private static List<String> expectedTestCommandFilesBatch() {
        List<String> shell = new ArrayList<>();
        shell.add("#!/bin/sh");
        shell.add("unzip -o -q 'in2.zip'");
        shell.add("/home/dev-itesla/myapps/myecho.sh \"in2\" \"out2\"");
        shell.add("rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi");
        shell.add("gzip 'tozip2'");
        shell.add("touch /tmp/flags/mydone_workingPath_12345_$SLURM_JOBID");
        return shell;
    }

    @Test
    void testOnlyUnzipBatch() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.commandFiles(3);
        Command command = commandExecutions.get(0).getCommand();
        List<String> shell = new SbatchScriptGenerator(flagPath).unzipCommonInputFiles(command);
        assertEquals(expectedTestOnlyUnzipBatch(), shell);
    }

    private static List<String> expectedTestOnlyUnzipBatch() {
        List<String> shell = new ArrayList<>();
        shell.add("#!/bin/sh");
        shell.add("unzip -o -q 'foo.zip'");
        return shell;
    }

    @Test
    void testGroupCmd() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.groupCmd();
        CommandExecution commandExecution = commandExecutions.get(0);
        Command command = commandExecution.getCommand();
        List<String> shell = new SbatchScriptGenerator(flagPath).parser(command, 0, workingPath, Collections.emptyMap());
        assertEquals(List.of("#!/bin/sh",
                "sleep \"5s\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi",
                "echo \"sub2\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi",
                "touch /tmp/flags/mydone_workingPath_12345_$SLURM_JOBID"), shell);
    }

    @Test
    void testGroupCmdArray() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.groupCmd();
        CommandExecution commandExecution = commandExecutions.get(0);
        assertCommandExecutionToShell(commandExecution, "group3.batch");
    }

    private void assertCommandExecutionToShell(CommandExecution commandExecution, String expected) {
        SbatchScriptGenerator shellGenerator = new SbatchScriptGenerator(flagPath);
        List<String> shell = shellGenerator.parser(commandExecution, workingPath, Collections.emptyMap(), true);
        List<String> expectedShell;
        try {
            expectedShell = Files.readAllLines(Paths.get(Objects.requireNonNull(this.getClass().getResource("/expectedShell/" + expected)).toURI()), StandardCharsets.UTF_8);
            assertEquals(expectedShell, shell);
        } catch (IOException | URISyntaxException e) {
            fail();
        }
    }

}
