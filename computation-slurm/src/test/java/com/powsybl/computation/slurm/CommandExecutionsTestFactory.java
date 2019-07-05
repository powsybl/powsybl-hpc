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
        return Collections.singletonList(new CommandExecution(command, 1));
    }

    static List<CommandExecution> longProgram(int seconds) {
        Command command = new SimpleCommandBuilder()
                .id("longProgram")
                .program("sleep")
                .args(seconds + "s")
                .build();
        return Collections.singletonList(new CommandExecution(command, 1));
    }

    static List<CommandExecution> makeSlurmBusy() {
        Command command = new SimpleCommandBuilder()
                .id("makeBusy")
                .program("sleep")
                .arg("60s")
                .build();
        return Collections.singletonList(new CommandExecution(command, 42));
    }
}
