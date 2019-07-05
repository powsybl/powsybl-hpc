/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.computation.*;
import org.apache.commons.lang3.StringUtils;

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
                .forEach(inputFile -> {
                    addUnzip(shell, inputFile.getName(0), inputFile.getPreProcessor());
                });
        return shell;
    }

    /**
     * Returns the list of commands which constitute the sbatch script.
     */
    List<String> parser(Command command, int executionIndex, Path workingDir, Map<String, String> env) {
        List<String> list = new ArrayList<>();
        list.add(SHEBANG);
        preProcess(list, command, executionIndex);
        export(list, env);
        cmdWithArgu(list, command, executionIndex, workingDir);
        postProcess(list, command, executionIndex);
        touchMydone(list, workingDir);
        return list;
    }

    // only preprocess input file which dependent on executionIdx
    private void preProcess(List<String> list, Command command, int executionIndex) {
        command.getInputFiles().stream()
                .filter(InputFile::dependsOnExecutionNumber)
                .forEach(file -> {
                    addUnzip(list, file.getName(executionIndex), file.getPreProcessor());
                });
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
        switch (command.getType()) {
            case SIMPLE:
                SimpleCommand simpleCmd = (SimpleCommand) command;
                list.add(CommandUtils.commandToString(simpleCmd.getProgram(), simpleCmd.getArgs(executionIndex)));
                list.add(String.format(CHECK_EXITCODE, flagDir.toAbsolutePath(), workingDir.getFileName()));
                break;
            case GROUP:
                GroupCommand groupCommand = (GroupCommand) command;
                for (GroupCommand.SubCommand subCommand : groupCommand.getSubCommands()) {
                    list.add(CommandUtils.commandToString(subCommand.getProgram(), subCommand.getArgs(executionIndex)));
                    list.add(String.format(CHECK_EXITCODE, flagDir.toAbsolutePath(), workingDir.getFileName()));
                }
                break;
            default:
                throw new AssertionError("Unexpected command type value: " + command.getType());
        }
    }

    private void postProcess(List<String> list, Command command, int executionIndex) {
        command.getOutputFiles().forEach(file -> {
            FilePostProcessor postProcessor = file.getPostProcessor();
            String fileName;
            fileName = file.getName(executionIndex);
            if (postProcessor != null) {
                if (postProcessor == FilePostProcessor.FILE_GZIP) {
                    list.add(SH_GZIP + fileName);
                } else {
                    throw new AssertionError("Unexpected postProcessor type value: " + postProcessor);
                }
            }
        });
    }

    private void touchMydone(List<String> list, Path workingDir) {
        list.add(String.format(TOUCH_MYDONE, flagDir.toAbsolutePath(), workingDir.getFileName()));
    }

}
