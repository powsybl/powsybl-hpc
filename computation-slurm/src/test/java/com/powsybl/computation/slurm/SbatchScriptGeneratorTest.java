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

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class SbatchScriptGeneratorTest {

    private FileSystem fileSystem;
    private Path flagPath;
    private Path workingPath;

    private final int commandIdx = 0;

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
        CommandExecution commandExecution = commandExecutions.get(commandIdx);
        Command command = commandExecution.getCommand();
        List<String> shell = new SbatchScriptGenerator(flagPath).parser(command, 0, workingPath, Collections.emptyMap());
        assertEquals(ImmutableList.of("#!/bin/sh",
                "echo \"test\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi",
                "touch /tmp/flags/mydone_workingPath_12345_$SLURM_JOBID"), shell);
    }

    @Test
    public void testSimpleCmdWithCount() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.simpleCmdWithCount(3);
        CommandExecution commandExecution = commandExecutions.get(commandIdx);
        Command command = commandExecution.getCommand();
        List<String> shell = new SbatchScriptGenerator(flagPath).parser(command, 0, workingPath, Collections.emptyMap());
        assertEquals(ImmutableList.of("#!/bin/sh",
                "echo \"te1st0\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi",
                "touch /tmp/flags/mydone_workingPath_12345_$SLURM_JOBID"), shell);
    }

    @Test
    public void testMyEchoSimpleCmd() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.myEchoSimpleCmdWithUnzipZip(3);
        CommandExecution commandExecution = commandExecutions.get(commandIdx);
        Command command = commandExecution.getCommand();
        List<String> shell = new SbatchScriptGenerator(flagPath).parser(command, 0, workingPath, Collections.emptyMap());
        assertEquals(ImmutableList.of("#!/bin/sh",
                "unzip -o -q in0.zip",
                "/home/dev-itesla/myapps/myecho.sh \"in0\" \"out0\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi",
                "gzip out0",
                "touch /tmp/flags/mydone_workingPath_12345_$SLURM_JOBID"), shell);
    }

    @Test
    public void testCommandFiles() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.commandFiles(3);
        Command command = commandExecutions.get(0).getCommand();
        List<String> shell = new SbatchScriptGenerator(flagPath).parser(command, 2, workingPath, Collections.emptyMap());
        assertEquals(expectedTestCommandFilesBatch(), shell);
    }

    private static List<String> expectedTestCommandFilesBatch() {
        List<String> shell = new ArrayList<>();
        shell.add("#!/bin/sh");
        shell.add("unzip -o -q in2.zip");
        shell.add("/home/dev-itesla/myapps/myecho.sh \"in2\" \"out2\"");
        shell.add("rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi");
        shell.add("gzip tozip2");
        shell.add("touch /tmp/flags/mydone_workingPath_12345_$SLURM_JOBID");
        return shell;
    }

    @Test
    public void testOnlyUnzipBatch() {
        List<CommandExecution> commandExecutions = CommandExecutionsTestFactory.commandFiles(3);
        Command command = commandExecutions.get(0).getCommand();
        List<String> shell = new SbatchScriptGenerator(flagPath).unzipCommonInputFiles(command);
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
        CommandExecution commandExecution = commandExecutions.get(0);
        Command command = commandExecution.getCommand();
        List<String> shell = new SbatchScriptGenerator(flagPath).parser(command, 0, workingPath, Collections.emptyMap());
        assertEquals(ImmutableList.of("#!/bin/sh",
                "sleep \"5s\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi",
                "echo \"sub2\"",
                "rc=$?; if [[ $rc != 0 ]]; then touch /tmp/flags/myerror_workingPath_12345_$SLURM_JOBID; exit $rc; fi",
                "touch /tmp/flags/mydone_workingPath_12345_$SLURM_JOBID"), shell);
    }

}
