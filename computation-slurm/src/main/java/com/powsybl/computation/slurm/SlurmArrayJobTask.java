/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.commons.io.WorkingDirectory;
import com.powsybl.computation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
class SlurmArrayJobTask extends SlurmTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlurmArrayJobTask.class);

    SlurmArrayJobTask(UUID callableId, SlurmComputationManager scm, WorkingDirectory directory,
                      List<CommandExecution> executions, ComputationParameters parameters, ExecutionEnvironment environment) {
        super(callableId, scm, directory, executions, parameters, environment);
        masters = new ArrayList<>();
    }

    @Override
    protected void initCounter() {
        counter = new TaskCounter(executions.get(executions.size() - 1).getExecutionCount());
    }

    @Override
    void submit() throws IOException, InterruptedException {
        commandByJobId = new HashMap<>();
        Long prejobId = null;
        for (int executionIdx = 0; executionIdx < executions.size(); executionIdx++) {
            checkCancelledDuringSubmitting();
            CommandExecution commandExecution = executions.get(executionIdx);
            Command command = commandExecution.getCommand();
            SbatchCmd cmd;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Executing {} command {} in working directory {}", command.getType(), command, workingDir);
            }

            // a master job to copy NonExecutionDependent and PreProcess needed input files
            if (command.getInputFiles().stream()
                    .anyMatch(inputFile -> !inputFile.dependsOnExecutionNumber() && inputFile.getPreProcessor() != null)) {
                if (scm.isCloseStarted()) {
                    LOGGER.info(CLOSE_START_NO_MORE_SEND_INFO);
                    break;
                }
                checkCancelledDuringSubmitting();
                SbatchScriptGenerator sbatchScriptGenerator = new SbatchScriptGenerator(flagDir);
                List<String> shell = sbatchScriptGenerator.unzipCommonInputFiles(command);
                String batchName = UNZIP_INPUTS_COMMAND_ID + "_" + executionIdx;
                copyShellToRemoteWorkingDir(shell, UNZIP_INPUTS_COMMAND_ID + "_" + executionIdx);
                cmd = buildSbatchCmd(UNZIP_INPUTS_COMMAND_ID, batchName, prejobId, parameters);
                prejobId = launchSbatch(cmd);
                if (firstJobId == null) {
                    firstJobId = prejobId;
                }
                masters.add(prejobId);
                tracingIds.add(prejobId);
            }

            String batchName = prepareBatch(commandExecution, executionIdx == executions.size() - 1);
            cmd = buildSbatchCmd(commandExecution.getExecutionCount(), command.getId(), batchName, prejobId, parameters);
            prejobId = launchSbatch(cmd);
            if (firstJobId == null) {
                firstJobId = prejobId;
            }
            masters.add(prejobId);
            tracingIds.add(prejobId);
            commandByJobId.put(prejobId, command);
        }
    }

    @Override
    boolean contains(Long id) {
        return containsInMaster(id);
    }

    @Override
    Set<Long> getAllJobIds() {
        return new HashSet<>(masters);
    }

    @Override
    protected ExecutionError convertLineToError(String line) {
        if (line.contains("_")) {
            Matcher m = DIGITAL_PATTERN.matcher(line);
            m.find();
            long jobId = Long.parseLong(m.group());
            m.find();
            int executionIdx = Integer.parseInt(m.group());
            m.find();
            int exitCode = Integer.parseInt(m.group());
            return new ExecutionError(commandByJobId.get(jobId), executionIdx, exitCode);
        } else {
            // not array job
            System.out.println(line);
            return super.convertLineToError(line);
        }
    }

    private String prepareBatch(CommandExecution commandExecution, boolean isLastCommandExecution) throws IOException {
        Map<String, String> executionVariables = CommandExecution.getExecutionVariables(environment.getVariables(), commandExecution);
        SbatchScriptGenerator scriptGenerator = new SbatchScriptGenerator(flagDir);
        List<String> shell = scriptGenerator.parser(commandExecution, workingDir, executionVariables, isLastCommandExecution);
        String batchName = commandExecution.getCommand().getId();
        copyShellToRemoteWorkingDir(shell, batchName);
        return batchName;
    }

    private SbatchCmd buildSbatchCmd(String commandId, String batchName, Long prejobId, ComputationParameters parameters) {
        return buildSbatchCmd(1, commandId, batchName, prejobId, parameters);
    }

    private SbatchCmd buildSbatchCmd(int arrayCount, String commandId, String batchName, Long prejobId, ComputationParameters parameters) {
        SbatchCmdBuilder sbatchCmdBuilder = baseCmdBuilder(commandId, prejobId != null ? Collections.singletonList(prejobId) : Collections.emptyList(), parameters);
        sbatchCmdBuilder.jobName(commandId)
                .script(batchName + SlurmConstants.BATCH_EXT);

        if (arrayCount > 1) {
            sbatchCmdBuilder.array(arrayCount)
                    .output(batchName + "_%a" + SlurmConstants.OUT_EXT)
                    .error(batchName + "_%a" + SlurmConstants.ERR_EXT);
        } else {
            sbatchCmdBuilder
                    .output(batchName + "_0" + SlurmConstants.OUT_EXT)
                    .error(batchName + "_0" + SlurmConstants.ERR_EXT);
        }
        return sbatchCmdBuilder.build();
    }
}
