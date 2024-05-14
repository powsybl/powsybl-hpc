/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import com.powsybl.commons.io.WorkingDirectory;
import com.powsybl.computation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 *
 * A {@link SlurmTask} which submits commands as job arrays when possible,
 * in particular for commands with execution count > 1.
 *
 * @author Yichen TANG {@literal <yichen.tang at rte-france.com>}
 */
class JobArraySlurmTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobArraySlurmTask.class);

    private Long firstJobId = null;
    private final List<Long> ids = new ArrayList<>();

    JobArraySlurmTask(SlurmComputationManager scm, WorkingDirectory directory,
                      List<CommandExecution> executions, ComputationParameters parameters, ExecutionEnvironment environment) {
        super(scm, directory, executions, parameters, environment);
    }

    @Override
    public void submit() throws IOException {
        commandByJobId = new HashMap<>();
        Long prejobId = null;
        for (int executionIdx = 0; executionIdx < executions.size(); executionIdx++) {
            if (cannotSubmit()) {
                break;
            }
            CommandExecution commandExecution = executions.get(executionIdx);
            Command command = commandExecution.getCommand();
            SbatchCmd cmd;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Executing {} command {} in working directory {}", command.getType(), command, workingDir);
            }

            // a master job to copy NonExecutionDependent and PreProcess needed input files
            if (command.getInputFiles().stream()
                    .anyMatch(inputFile -> !inputFile.dependsOnExecutionNumber() && inputFile.getPreProcessor() != null)) {
                if (cannotSubmit()) {
                    break;
                }
                SbatchScriptGenerator sbatchScriptGenerator = new SbatchScriptGenerator(flagDir);
                List<String> shell = sbatchScriptGenerator.unzipCommonInputFiles(command);
                String batchName = UNZIP_INPUTS_COMMAND_ID + "_" + executionIdx;
                copyShellToRemoteWorkingDir(shell, UNZIP_INPUTS_COMMAND_ID + "_" + executionIdx);
                cmd = buildSbatchCmd(UNZIP_INPUTS_COMMAND_ID, batchName, prejobId, parameters);
                prejobId = launchSbatch(cmd);
                checkFirstJob(prejobId);
                ids.add(prejobId);
                jobs.add(new CompletableMonitoredJob(prejobId, false));
            }
            boolean isLast = executionIdx == executions.size() - 1;
            String batchName = prepareBatch(commandExecution, isLast);
            cmd = buildSbatchCmd(commandExecution.getExecutionCount(), command.getId(), batchName, prejobId, parameters);
            prejobId = launchSbatch(cmd);
            checkFirstJob(prejobId);
            ids.add(prejobId);
            CompletableMonitoredJob completableMonitoredJob = new CompletableMonitoredJob(prejobId, isLast);
            completableMonitoredJob.setCounter(commandExecution.getExecutionCount());
            jobs.add(completableMonitoredJob);
            commandByJobId.put(prejobId, command);
        }

        aggregateMonitoredJobs();
    }

    private void checkFirstJob(Long prejobId) {
        if (firstJobId == null) {
            firstJobId = prejobId;
            LOGGER.debug("First jobId : {}", firstJobId);
        }
    }

    @Override
    Collection<Long> getAllJobIds() {
        return ids;
    }

    @Override
    ExecutionError convertScontrolResult2Error(ScontrolCmd.ScontrolResultBean scontrolResultBean) {
        Command cmd = commandByJobId.get(scontrolResultBean.getArrayJobId()) == null ? commandByJobId.get(scontrolResultBean.getJobId()) : commandByJobId.get(scontrolResultBean.getArrayJobId());
        return new ExecutionError(cmd, scontrolResultBean.getArrayTaskId(), scontrolResultBean.getExitCode());
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
        SbatchCmdBuilder builder = new SbatchCmdBuilder()
                .jobName(commandId)
                .workDir(workingDir)
                // TODO check
                .nodes(1)
                .ntasks(1)
                .oversubscribe();
        if (prejobId != null) {
            builder.aftercorr(Collections.singletonList(prejobId));
        }
        builder.jobName(commandId)
                .script(batchName + SlurmConstants.BATCH_EXT);
        if (arrayCount > 1) {
            builder.array(arrayCount)
                    .output(batchName + "_%a" + SlurmConstants.OUT_EXT)
                    .error(batchName + "_%a" + SlurmConstants.ERR_EXT);
        } else {
            builder.output(batchName + "_0" + SlurmConstants.OUT_EXT)
                    .error(batchName + "_0" + SlurmConstants.ERR_EXT);
        }
        addParameters(builder, commandId);
        return builder.build();
    }
}
