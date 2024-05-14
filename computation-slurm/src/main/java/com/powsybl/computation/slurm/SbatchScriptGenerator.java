/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import com.powsybl.computation.*;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import static com.powsybl.computation.CommandType.SIMPLE;
import static com.powsybl.computation.FilePreProcessor.FILE_GUNZIP;

/**
 * Generates the content of the sbatch script, to be submitted using and {@link SbatchCmd}.
 *
 * @author Yichen Tang {@literal <yichen.tang at rte-france.com>}
 * @author Sylvain Leclerc {@literal <sylvain.leclerc at rte-france.com>}
 */
class SbatchScriptGenerator {

    private static final String SHEBANG = "#!/bin/sh";
    private static final String SH_GUNZIP = "gunzip ";
    private static final String SH_GZIP = "gzip ";
    private static final String SH_EXPORT = "export ";
    private static final String SH_UNZIP = "unzip -o -q "; // overwrite/quiet

    private static final String CHECK_EXITCODE = "rc=$?; if [[ $rc != 0 ]]; then touch %s/myerror_%s_$SLURM_JOBID; exit $rc; fi";
    private static final String CHECK_EXITCODE_ARRAY = "rc=$?; if [[ $rc != 0 ]]; then touch %s/myerror_%s_$SLURM_ARRAY_JOB_ID-$SLURM_ARRAY_TASK_ID; exit $rc; fi";
    private static final String TOUCH_MYDONE = "touch %s/mydone_%s_$SLURM_JOBID";
    private static final String TOUCH_MYDONE_ARRAY = "touch %s/mydone_%s_$SLURM_ARRAY_JOB_ID-$SLURM_ARRAY_TASK_ID";

    private static final String INDENTATION_4 = "    ";
    private static final String INDENTATION_6 = "      ";
    private static final String SH_CASE_BREAK = INDENTATION_4 + ";;";
    private static final String SPL_CMD_ARGS = "ARGS";
    private static final String CALL_SPL_CMD_ARGS = " \"${ARGS[@]}\"";
    private static final String SUB_CMD_ARGS = "ARGS_";
    private static final String PRE_FILE = "PRE";
    private static final String POST_FILE = "POST";

    private static final UnaryOperator<String> WRAP_FILENAME = str -> "'" + str + "'";
    private static final UnaryOperator<String> VAR2ARG = str -> "\"$" + str + "\"";

    private final Path flagDir;

    SbatchScriptGenerator(Path flagDir) {
        this.flagDir = Objects.requireNonNull(flagDir);
    }

    List<String> unzipCommonInputFiles(Command command) {
        List<String> shell = new ArrayList<>();
        shell.add(SHEBANG);
        command.getInputFiles()
                .stream().filter(inputFile -> !inputFile.dependsOnExecutionNumber()) // common
                .filter(inputFile -> inputFile.getPreProcessor() != null) // to unzip
                .forEach(inputFile -> addUnzipFilename(shell, inputFile.getName(0), inputFile.getPreProcessor()));
        return shell;
    }

    /**
     * Returns the list of commands which constitute the sbatch script.
     */
    List<String> parser(Command command, int executionIndex, Path workingDir, Map<String, String> env) {
        return parser(command, executionIndex, workingDir, env, true);
    }

    private List<String> parser(Command command, int executionIndex, Path workingDir, Map<String, String> env, boolean touchMydone) {
        List<String> list = new ArrayList<>();
        list.add(SHEBANG);
        preProcess(list, command, executionIndex);
        export(list, env);
        cmdWithArgu(list, command, executionIndex, workingDir);
        postProcess(list, command, executionIndex);
        if (touchMydone) {
            touchMydone(list, workingDir);
        }
        return list;
    }

    List<String> parser(CommandExecution commandExecution, Path workingDir, Map<String, String> env, boolean touchMydone) {
        if (commandExecution.getExecutionCount() == 1) {
            return parser(commandExecution.getCommand(), 0, workingDir, env, touchMydone);
        }
        List<String> shell = new ArrayList<>();
        shell.add(SHEBANG);
        arrayJobCase(shell, commandExecution);
        preProcess(shell, commandExecution.getCommand());
        export(shell, env);
        cmd(shell, commandExecution.getCommand(), workingDir);
        postProcess(shell, commandExecution.getCommand());
        if (touchMydone) {
            touchMydoneArray(shell, workingDir);
        }
        return shell;
    }

    // only preprocess input file which dependent on executionIdx
    private void preProcess(List<String> list, Command command, int executionIndex) {
        command.getInputFiles().stream()
                .filter(InputFile::dependsOnExecutionNumber)
                .forEach(file -> addUnzipFilename(list, file.getName(executionIndex), file.getPreProcessor()));
    }

    private void preProcess(List<String> shell, Command command) {
        List<InputFile> inputFiles = command.getInputFiles();
        for (int i = 0; i < inputFiles.size(); i++) {
            InputFile inputFile = inputFiles.get(i);
            if (!inputFile.dependsOnExecutionNumber()) {
                // skip because this file is already unzip in a previous batch
                continue;
            }
            addUnzipVariable(shell, PRE_FILE + i, inputFile.getPreProcessor());
        }
    }

    // used in batch mode
    private static void addUnzipFilename(List<String> shell, String filename, FilePreProcessor preProcessor) {
        addUnzipCmd(shell, WRAP_FILENAME.apply(filename), preProcessor);
    }

    // used in array mode
    private static void addUnzipVariable(List<String> shell, String variableName, FilePreProcessor preProcessor) {
        addUnzipCmd(shell, VAR2ARG.apply(variableName), preProcessor);
    }

    private static void addUnzipCmd(List<String> shell, String unzipArg, FilePreProcessor preProcessor) {
        // Only two values in the ENUM so an if is used
        if (preProcessor == FILE_GUNZIP) {
            shell.add(SH_GUNZIP + unzipArg);
        } else {
            shell.add(SH_UNZIP + unzipArg);
        }
    }

    private void export(List<String> list, Map<String, String> env) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            sb.append(SH_EXPORT).append(name).append("=").append(value);
            if (name.endsWith("PATH")) {
                sb.append(":").append("$").append(name);
            }
            sb.append("; ");
        }
        if (!StringUtils.isEmpty(sb.toString())) {
            list.add(sb.toString());
        }
    }

    private void cmdWithArgu(List<String> list, Command command, int executionIndex, Path workingDir) {
        // Only two values in the ENUM so an if is used
        if (command.getType() == SIMPLE) {
            SimpleCommand simpleCmd = (SimpleCommand) command;
            list.add(CommandUtils.commandToString(simpleCmd.getProgram(), simpleCmd.getArgs(executionIndex)));
            list.add(String.format(CHECK_EXITCODE, flagDir.toAbsolutePath(), workingDir.getFileName()));
        } else {
            GroupCommand groupCommand = (GroupCommand) command;
            for (GroupCommand.SubCommand subCommand : groupCommand.getSubCommands()) {
                list.add(CommandUtils.commandToString(subCommand.getProgram(), subCommand.getArgs(executionIndex)));
                list.add(String.format(CHECK_EXITCODE, flagDir.toAbsolutePath(), workingDir.getFileName()));
            }
        }
    }

    private void postProcess(List<String> list, Command command, int executionIndex) {
        command.getOutputFiles().forEach(file -> {
            FilePostProcessor postProcessor = file.getPostProcessor();
            String fileName;
            fileName = file.getName(executionIndex);
            if (postProcessor != null) {
                if (postProcessor == FilePostProcessor.FILE_GZIP) {
                    list.add(SH_GZIP + WRAP_FILENAME.apply(fileName));
                } else {
                    throw new AssertionError("Unexpected postProcessor type value: " + postProcessor);
                }
            }
        });
    }

    private void postProcess(List<String> shell, Command command) {
        List<OutputFile> outputFiles = command.getOutputFiles();
        for (int i = 0; i < outputFiles.size(); i++) {
            OutputFile outputFile = outputFiles.get(i);
            FilePostProcessor postProcessor = outputFile.getPostProcessor();
            addGzipVariable(shell, POST_FILE + i, postProcessor);
            // TODO add touch my error for gzip command??
        }
    }

    private static void addGzipVariable(List<String> shell, String variableName, FilePostProcessor postProcessor) {
        if (postProcessor != null) {
            if (postProcessor == FilePostProcessor.FILE_GZIP) {
                shell.add(SH_GZIP + VAR2ARG.apply(variableName));
            } else {
                throw new AssertionError("Unexpected postProcessor type value: " + postProcessor);
            }
        }
    }

    private void touchMydone(List<String> list, Path workingDir) {
        list.add(String.format(TOUCH_MYDONE, flagDir.toAbsolutePath(), workingDir.getFileName()));
    }

    private void touchMydoneArray(List<String> list, Path workingDir) {
        list.add(String.format(TOUCH_MYDONE_ARRAY, flagDir.toAbsolutePath(), workingDir.getFileName()));
    }

    private void arrayJobCase(List<String> shell, CommandExecution commandExecution) {
        Command command = commandExecution.getCommand();
        shell.add("case $SLURM_ARRAY_TASK_ID in");
        for (int caseIdx = 0; caseIdx < commandExecution.getExecutionCount(); caseIdx++) {
            shell.add(INDENTATION_4 + caseIdx + ")");
            addInputFilenames(shell, caseIdx, command);
            addOutputFilenames(shell, caseIdx, command);
            // Only two values in the ENUM so an if is used
            if (command.getType() == SIMPLE) {
                SimpleCommand simpleCmd = (SimpleCommand) command;
                String args = CommandUtils.commandArgsToString(simpleCmd.getArgs(caseIdx));
                shell.add(INDENTATION_6 + SPL_CMD_ARGS + "=(" + args + ")");
                shell.add(SH_CASE_BREAK);
            } else {
                GroupCommand groupCommand = (GroupCommand) command;
                List<GroupCommand.SubCommand> subCommands = groupCommand.getSubCommands();
                List<String> subArgs = new ArrayList<>();
                for (int i = 0; i < subCommands.size(); i++) {
                    GroupCommand.SubCommand cmd = subCommands.get(i);
                    String argsSub = CommandUtils.commandArgsToString(cmd.getArgs(caseIdx));
                    String de = SUB_CMD_ARGS + i + "=(" + argsSub + ")";
                    subArgs.add(de);
                }
                String subArgsJoined = String.join(" ", subArgs);
                shell.add(INDENTATION_6 + subArgsJoined);
                shell.add(SH_CASE_BREAK);
            }
        }
        shell.add("esac");
    }

    // TODO Input/OutputFile abstraction in core
    private void addInputFilenames(List<String> shell, int caseIdx, Command command) {
        List<InputFile> inputFiles = command.getInputFiles();
        List<String> ins = new ArrayList<>();
        for (int i = 0; i < inputFiles.size(); i++) {
            InputFile inputFile = inputFiles.get(i);
            if (!inputFile.dependsOnExecutionNumber()) {
                // skip because this file is already unzip in a previous batch
                continue;
            }
            ins.add(PRE_FILE + i + "=" + WRAP_FILENAME.apply(inputFile.getName(caseIdx)));
        }
        if (ins.isEmpty()) {
            return;
        }
        shell.add(INDENTATION_6 + String.join(" ", ins));
    }

    private void addOutputFilenames(List<String> shell, int caseIdx, Command command) {
        List<OutputFile> outputFiles = command.getOutputFiles();
        List<String> outs = new ArrayList<>();
        for (int i = 0; i < outputFiles.size(); i++) {
            OutputFile outputFile = outputFiles.get(i);
            if (!outputFile.dependsOnExecutionNumber() || outputFile.getPostProcessor() == null) {
                continue;
            }
            outs.add(POST_FILE + i + "=" + WRAP_FILENAME.apply(outputFile.getName(caseIdx)));
        }
        if (outs.isEmpty()) {
            return;
        }
        shell.add(INDENTATION_6 + String.join(" ", outs));
    }

    private void cmd(List<String> shell, Command command, Path workingDir) {
        // Only two values in the ENUM so an if is used
        if (command.getType() == SIMPLE) {
            SimpleCommand simpleCmd = (SimpleCommand) command;
            simpleCmdWithArgs(shell, simpleCmd);
            shell.add(String.format(CHECK_EXITCODE_ARRAY, flagDir.toAbsolutePath(), workingDir.getFileName()));
        } else {
            GroupCommand groupCommand = (GroupCommand) command;
            List<GroupCommand.SubCommand> subCommands = groupCommand.getSubCommands();
            for (int i = 0; i < subCommands.size(); i++) {
                GroupCommand.SubCommand cmd = subCommands.get(i);
                subCmdWithArgs(shell, cmd, i);
                shell.add(String.format(CHECK_EXITCODE_ARRAY, flagDir.toAbsolutePath(), workingDir.getFileName()));
            }
        }
    }

    private void simpleCmdWithArgs(List<String> list, SimpleCommand simpleCommand) {
        list.add(simpleCommand.getProgram() + CALL_SPL_CMD_ARGS);
    }

    private void subCmdWithArgs(List<String> list, GroupCommand.SubCommand subCommand, int idxInGroup) {
        // cmd "${ARGS_1{[@]}"
        list.add(subCommand.getProgram() + " \"${ARGS_" + idxInGroup + "[@]}\"");
    }

}
