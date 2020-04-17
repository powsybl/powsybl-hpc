/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.computation.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public final class CommandExecutionsTestFactory {

    private CommandExecutionsTestFactory() {
    }

    static List<CommandExecution> simpleCmd() {
        Command command = new SimpleCommandBuilder()
                .id("simpleCmdId")
                .program("echo")
                .args("test")
                .build();
        CommandExecution commandExecution = new CommandExecution(command, 1);
        return Collections.singletonList(commandExecution);
    }

    static List<CommandExecution> simpleCmdWithCount(int executionCount) {
        Command command = new SimpleCommandBuilder()
                .id("cmdId")
                .program("echo")
                .args(i -> Collections.singletonList("te1st" + i))
                .timeout(60)
                .build();
        CommandExecution commandExecution = new CommandExecution(command, executionCount);
        return Collections.singletonList(commandExecution);
    }

    /**
     * The 4th batch would fail.
     * @param executionCount
     * @return
     */
    static List<CommandExecution> myEchoSimpleCmdWithUnzipZip(int executionCount) {
        Command command = new SimpleCommandBuilder()
                .id("myEcho")
                .program("/home/dev-itesla/myapps/myecho.sh")
                .inputFiles(new InputFile(integer -> "in" + integer + ".zip", FilePreProcessor.ARCHIVE_UNZIP))
                .outputFiles(new OutputFile(integer -> "out" + integer, FilePostProcessor.FILE_GZIP))
                .args(i -> Arrays.asList("in" + i, "out" + i))
                .build();
        CommandExecution commandExecution = new CommandExecution(command, executionCount);
        return Collections.singletonList(commandExecution);
    }

    static List<CommandExecution> commandFiles(int executionCount) {
        InputFile stringInput = new InputFile("foo.zip", FilePreProcessor.ARCHIVE_UNZIP);
        InputFile functionsInput = new InputFile(integer -> "in" + integer + ".zip", FilePreProcessor.ARCHIVE_UNZIP);
        OutputFile out1 = new OutputFile(integer -> "tozip" + integer, FilePostProcessor.FILE_GZIP);
        OutputFile out2 = new OutputFile(integer -> "raw" + integer, null);
        Command command = new SimpleCommandBuilder()
                .id("myEcho")
                .program("/home/dev-itesla/myapps/myecho.sh")
                .inputFiles(stringInput, functionsInput)
                .outputFiles(out1, out2)
                .args(i -> Arrays.asList("in" + i, "out" + i))
                .build();
        CommandExecution commandExecution = new CommandExecution(command, executionCount);
        return Collections.singletonList(commandExecution);
    }

    static List<CommandExecution> groupCmd() {
        return groupCmd(1);
    }

    static List<CommandExecution> groupCmd(int executionCount) {
        Command command = new GroupCommandBuilder()
                .id("groupCmdId")
                .subCommand()
                .program("sleep")
                .args("5s")
                .add()
                .subCommand()
                .program("echo")
                .args("sub2")
                .add()
                .build();
        return Collections.singletonList(new CommandExecution(command, 3));
    }

    static List<CommandExecution> longProgram(int seconds) {
        Command command = new SimpleCommandBuilder()
                .id("longProgram")
                .program("sleep")
                .args(seconds + "s")
                .build();
        return Collections.singletonList(new CommandExecution(command, 1));
    }

    static List<CommandExecution> makeSlurmBusy(int count) {
        Command command = new SimpleCommandBuilder()
                .id("makeBusy")
                .program("sleep")
                .arg("60s")
                .build();
        return Collections.singletonList(new CommandExecution(command, count));
    }

    static List<CommandExecution> twoSimpleCmd() {
        Command command1 = new SimpleCommandBuilder()
                .id("simpleCmdId")
                .program("sleep")
                .args("10s")
                .build();
        Command command2 = new SimpleCommandBuilder()
                .id("cmd2")
                .program("echo")
                .args("hello", ">", "output")
                .build();
        return Arrays.asList(new CommandExecution(command1, 1), new CommandExecution(command2, 1));
    }

    static List<CommandExecution> md5sumLargeFile() {
        Command command1 = new SimpleCommandBuilder()
                .id("c1")
                .program("md5sum")
                .inputFiles(new InputFile("2GFile.gz", FilePreProcessor.FILE_GUNZIP))
                .args("2GFile")
                .build();
        Command command2 = new SimpleCommandBuilder()
                .id("c2")
                .inputFiles(new InputFile("4GFile.gz", FilePreProcessor.FILE_GUNZIP))
                .program("md5sum")
                .args("4GFile")
                .build();
        return Arrays.asList(new CommandExecution(command1, 3),
                new CommandExecution(command2, 1));
    }

    static List<CommandExecution> invalidProgram() {
        Command cmd = new SimpleCommandBuilder()
                .id("invalidProgram")
                .program("echoo")
                .args("hello")
                .build();
        return Collections.singletonList(new CommandExecution(cmd, 1));
    }

    static List<CommandExecution> invalidProgramInGroup() {
        Command command = new GroupCommandBuilder()
                .id("groupCmdId")
                .subCommand()
                .program("echoo")
                .args("hello")
                .add()
                .subCommand()
                .program("echo")
                .args("sub2")
                .add()
                .build();
        return Collections.singletonList(new CommandExecution(command, 1));
    }

    static List<CommandExecution> invalidProgramInList() {
        return Arrays.asList(invalidProgram().get(0), CommandExecutionsTestFactory.simpleCmd().get(0));
    }

    static List<CommandExecution> longProgramInList(int c1, int c2, int c3) {
        Command command1 = new SimpleCommandBuilder()
                .id("tLP")
                .program("sleep")
                .args("10s")
                .build();
        Command command2 = new SimpleCommandBuilder()
                .id("tLP2")
                .program("sleep")
                .args("10s")
                .build();
        Command command3 = new SimpleCommandBuilder()
                .id("tLP3")
                .program("sleep")
                .args("10s")
                .build();
        return Arrays.asList(new CommandExecution(command1, c1),
                new CommandExecution(command2, c2),
                new CommandExecution(command3, c3));
    }

    static List<CommandExecution> longProgramInList() {
        return longProgramInList(1, 1, 1);
    }

    static List<CommandExecution> mixedPrograms() {
        return longProgramInList(1, 5, 2);
    }

}
