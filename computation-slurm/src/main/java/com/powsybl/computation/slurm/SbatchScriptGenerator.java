/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.computation.*;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates the content of the sbatch script, to be submitted using and {@link SbatchCmd}.
 *
 * @author Yichen Tang <yichen.tang at rte-france.com>
 * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
 */
class SbatchScriptGenerator {

    private static final String SHEBANG = "#!/bin/sh";
    private static final String SH_GUNZIP = "gunzip ";
    private static final String SH_GZIP = "gzip ";
    private static final String SH_EXPORT = "export ";
    private static final String SH_UNZIP = "unzip -o -q "; // overwrite/quiet

    private static final String CHECK_EXITCODE = "rc=$?; if [[ $rc != 0 ]]; then touch %s/myerror_%s_$SLURM_JOBID; exit $rc; fi";
    private static final String TOUCH_MYDONE = "touch %s/mydone_%s_$SLURM_JOBID";

    private static final String INDENTATION_4 = "    ";
    private static final String INDENTATION_6 = "      ";
    private static final String SH_CASE_BREAK = INDENTATION_4 + ";;";
    private static final String SPL_CMD_ARGS = "ARGS";
    private static final String SUB_CMD_ARGS = "ARGS_";
    private static final String PRE_FILE = "PRE";
    private static final String POST_FILE = "POST";

    private final Path flagDir;
    private final Path workingDir;
    private final Map<String, String> env;
    private final CommandExecution commandExecution;

    private boolean isArrayJob;
    private boolean isLast = false;

    SbatchScriptGenerator(Path flagDir, CommandExecution commandExecution, Path workingDir, Map<String, String> env) {
        this.flagDir = Objects.requireNonNull(flagDir);
        this.commandExecution = Objects.requireNonNull(commandExecution);
        this.workingDir = Objects.requireNonNull(workingDir);
        this.env = Objects.requireNonNull(env);
        isArrayJob = commandExecution.getExecutionCount() != 1;
    }

    SbatchScriptGenerator setIsLast(boolean isLast) {
        this.isLast = isLast;
        return this;
    }

    /**
     * Returns the list of commands which constitute the sbatch script.
     */
    List<String> parse() {
        List<String> shell = new ArrayList<>();
        shell.add(SHEBANG);
        if (isArrayJob) {
            arrayJobCase(shell);
            preProcess(shell);
        } else {
            preProcessNonArray(shell);
        }
        export(shell, env);
        cmd(shell);
        if (isArrayJob) {
            postProcess(shell);
        } else {
            postProcessNonArray(shell);
        }
        touchMydone(shell);
        return shell;
    }

    /**
     * Returns the list of commands which constitute the sbatch script.
     * The unzip common input files script DO NOT create a flag file at the end.
     */
    static List<String> unzipCommonInputFiles(Command command) {
        List<String> shell = new ArrayList<>();
        shell.add(SHEBANG);
        command.getInputFiles()
                .stream().filter(inputFile -> !inputFile.dependsOnExecutionNumber()) // common
                .filter(inputFile -> inputFile.getPreProcessor() != null) // to unzip
                .forEach(inputFile -> addUnzip(shell, inputFile.getName(0), inputFile.getPreProcessor()));
        return shell;
    }

    private void arrayJobCase(List<String> shell) {
        Command command = commandExecution.getCommand();
        shell.add("case $SLURM_ARRAY_TASK_ID in");
        for (int caseIdx = 0; caseIdx < commandExecution.getExecutionCount(); caseIdx++) {
            shell.add(INDENTATION_4 + caseIdx + ")");
            addInputFilenames(shell, caseIdx);
            addOutputFilenames(shell, caseIdx);
            switch (command.getType()) {
                case SIMPLE:
                    SimpleCommand simpleCmd = (SimpleCommand) command;
                    String args = CommandUtils.commandArgsToString(simpleCmd.getArgs(caseIdx));
                    shell.add(INDENTATION_6 + SPL_CMD_ARGS + "=\"" + args + "\"");
                    shell.add(SH_CASE_BREAK);
                    break;
                case GROUP:
                    GroupCommand groupCommand = (GroupCommand) command;
                    List<GroupCommand.SubCommand> subCommands = groupCommand.getSubCommands();
                    List<String> subArgs = new ArrayList<>();
                    for (int i = 0; i < subCommands.size(); i++) {
                        GroupCommand.SubCommand cmd = subCommands.get(i);
                        String argsSub = CommandUtils.commandArgsToString(cmd.getArgs(caseIdx));
                        String de = SUB_CMD_ARGS + i + "=\"" + argsSub + "\"";
                        subArgs.add(de);
                    }
                    String subArgsJoined = String.join(" ", subArgs);
                    shell.add(INDENTATION_6 + subArgsJoined);
                    shell.add(SH_CASE_BREAK);
                    break;
                default:
                    throw new AssertionError("Unexpected command type value: " + command.getType());
            }
        }
        shell.add("esac");
    }

    // TODO Input/OutputFile abstraction in core
    private void addInputFilenames(List<String> shell, int caseIdx) {
        List<InputFile> inputFiles = commandExecution.getCommand().getInputFiles();
        List<String> ins = new ArrayList<>();
        for (int i = 0; i < inputFiles.size(); i++) {
            InputFile inputFile = inputFiles.get(i);
            if (!inputFile.dependsOnExecutionNumber()) {
                // skip because this file is already unzip in a previous batch
                continue;
            }
            ins.add(PRE_FILE + i + "=" + inputFile.getName(caseIdx));
        }
        if (ins.isEmpty()) {
            return;
        }
        shell.add(INDENTATION_6 + String.join(" ", ins));
    }

    private void addOutputFilenames(List<String> shell, int caseIdx) {
        List<OutputFile> outputFiles = commandExecution.getCommand().getOutputFiles();
        List<String> outs = new ArrayList<>();
        for (int i = 0; i < outputFiles.size(); i++) {
            OutputFile outputFile = outputFiles.get(i);
            if (!outputFile.dependsOnExecutionNumber() || outputFile.getPostProcessor() == null) {
                continue;
            }
            outs.add(POST_FILE + i + "=" + outputFile.getName(caseIdx));
        }
        if (outs.isEmpty()) {
            return;
        }
        shell.add(INDENTATION_6 + String.join(" ", outs));
    }

    private void cmd(List<String> shell) {
        Command command = commandExecution.getCommand();
        switch (command.getType()) {
            case SIMPLE:
                SimpleCommand simpleCmd = (SimpleCommand) command;
                simpleCmdWithArgs(shell, simpleCmd);
                shell.add(String.format(CHECK_EXITCODE, flagDir.toAbsolutePath(), workingDir.getFileName()));
                break;
            case GROUP:
                GroupCommand groupCommand = (GroupCommand) command;
                List<GroupCommand.SubCommand> subCommands = groupCommand.getSubCommands();
                for (int i = 0; i < subCommands.size(); i++) {
                    GroupCommand.SubCommand cmd = subCommands.get(i);
                    subCmdWithArgs(shell, cmd, i);
                    shell.add(String.format(CHECK_EXITCODE, flagDir.toAbsolutePath(), workingDir.getFileName()));
                }
                break;
            default:
                throw new AssertionError("Unexpected command type value: " + command.getType());
        }
    }

    private void simpleCmdWithArgs(List<String> list, SimpleCommand simpleCommand) {
        if (isArrayJob) {
            list.add(simpleCommand.getProgram() + " $" + SPL_CMD_ARGS);
        } else {
            list.add(CommandUtils.commandToString(simpleCommand.getProgram(), simpleCommand.getArgs(0))); // not an array job, 0 is the only number
        }
    }

    private void subCmdWithArgs(List<String> list, GroupCommand.SubCommand subCommand, int idxInGroup) {
        if (isArrayJob) {
            list.add(subCommand.getProgram() + " $" + SUB_CMD_ARGS + idxInGroup);
        } else {
            list.add(CommandUtils.commandToString(subCommand.getProgram(), subCommand.getArgs(0))); // not an array job, 0 is the only number
        }
    }

    // only preprocess input file which dependent on executionIdx
    private void preProcessNonArray(List<String> shell) {
        Command command = commandExecution.getCommand();
        command.getInputFiles().stream()
                .filter(InputFile::dependsOnExecutionNumber)
                .forEach(file -> {
                    addUnzip(shell, file.getName(0), file.getPreProcessor());
                });
    }

    private void preProcess(List<String> shell) {
        List<InputFile> inputFiles = commandExecution.getCommand().getInputFiles();
        for (int i = 0; i < inputFiles.size(); i++) {
            InputFile inputFile = inputFiles.get(i);
            if (!inputFile.dependsOnExecutionNumber()) {
                // skip because this file is already unzip in a previous batch
                continue;
            }
            addUnzip(shell, "$" + PRE_FILE + i, inputFile.getPreProcessor());
        }
    }

    private static void addUnzip(List<String> shell, String filename, FilePreProcessor preProcessor) {
        switch (preProcessor) {
            case FILE_GUNZIP:
                // gunzip the file
                shell.add(SH_GUNZIP + filename);
                break;
            case ARCHIVE_UNZIP:
                // extract the archive
                shell.add(SH_UNZIP + filename);
                break;
            default:
                throw new AssertionError("Unexpected FilePreProcessor value: " + preProcessor);
        }
    }

    private void export(List<String> shell, Map<String, String> env) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            sb.append(SH_EXPORT).append(name).append("=").append(value);
            if (name.endsWith("PATH")) {
                sb.append(":$").append(name);
            }
            sb.append("; ");
        }
        if (!StringUtils.isEmpty(sb.toString())) {
            shell.add(sb.toString());
        }
    }

    private void postProcessNonArray(List<String> shell) {
        commandExecution.getCommand().getOutputFiles().forEach(file -> {
            addGzip(shell, file.getName(0), file.getPostProcessor());
        });
    }

    private void postProcess(List<String> shell) {
        if (isArrayJob) {
            List<OutputFile> outputFiles = commandExecution.getCommand().getOutputFiles();
            for (int i = 0; i < outputFiles.size(); i++) {
                OutputFile outputFile = outputFiles.get(i);
                if (!outputFile.dependsOnExecutionNumber()) {
                    // skip because this file is already unzip in a previous batch
                    continue;
                }
                FilePostProcessor postProcessor = outputFile.getPostProcessor();
                addGzip(shell, "$" + POST_FILE + i, postProcessor);
            }
        } else {
            postProcessNonArray(shell);
        }
    }

    private static void addGzip(List<String> shell, String fileName, @Nullable FilePostProcessor postProcessor) {
        if (postProcessor != null) {
            if (postProcessor == FilePostProcessor.FILE_GZIP) {
                shell.add(SH_GZIP + fileName);
                // TODO gzip fails
//                shell.add(String.format(CHECK_EXITCODE, flagDir.toAbsolutePath(), workingDir.getFileName()));
            } else {
                throw new AssertionError("Unexpected postProcessor type value: " + postProcessor);
            }
        }
    }

    private void touchMydone(List<String> list) {
        if (isLast) {
            list.add(String.format(TOUCH_MYDONE, flagDir.toAbsolutePath(), workingDir.getFileName()));
        }
    }

}
