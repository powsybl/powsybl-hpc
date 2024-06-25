/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Yichen Tang {@literal <yichen.tang at rte-france.com>}
 */
class ScontrolCmd extends AbstractSlurmCmd<ScontrolCmd.ScontrolResult> {

    private final String cmd;
    private final ScontrolType type;

    ScontrolCmd(String cmd, ScontrolType type) {
        this.cmd = Objects.requireNonNull(cmd);
        this.type = Objects.requireNonNull(type);
    }

    enum ScontrolType {
        SHOW_JOB
    }

    @Override
    public ScontrolResult send(CommandExecutor commandExecutor) throws SlurmCmdNonZeroException {
        CommandResult result = sendCmd(commandExecutor, cmd);
        return new ScontrolResult(result);
    }

    class ScontrolResult extends AbstractSlurmCmdResult {

        private final List<ScontrolResultBean> resultBeanList = new ArrayList<>();

        ScontrolResult(CommandResult commandResult) {
            super(commandResult);
            parse();
        }

        private void parse() {
            final String stdOut = commandResult.stdOut();
            final String[] blocks = stdOut.split("\\n\\n");
            for (String block : blocks) {
                if (!StringUtils.isEmpty(block)) {
                    resultBeanList.add(new ScontrolResultBean(block));
                }
            }
        }

        public ScontrolResultBean getResult() {
            return resultBeanList.get(0);
        }

        public List<ScontrolResultBean> getResultBeanList() {
            return resultBeanList;
        }
    }

    class ScontrolResultBean {

        private final String block;
        long jobId;
        String jobName;
        String userId;
        String groupId;
        String qos;
        SlurmConstants.JobState jobState;
        String dependency;
        int exitCode;
        int arrayTaskId;
        long arrayJobId;

        ScontrolResultBean(String block) {
            this.block = Objects.requireNonNull(block);
            parse();
        }

        String getUserId() {
            return userId;
        }

        public long getJobId() {
            return jobId;
        }

        long getArrayJobId() {
            return arrayJobId;
        }

        public String getJobName() {
            return jobName;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getQos() {
            return qos;
        }

        public SlurmConstants.JobState getJobState() {
            return jobState;
        }

        public String getDependency() {
            return dependency;
        }

        public int getExitCode() {
            return exitCode;
        }

        public int getArrayTaskId() {
            return arrayTaskId;
        }

        private void parse() {
            if (type == ScontrolType.SHOW_JOB) {
                parseShowJob();
            } else {
                throw new SlurmException("Not implemented yet for type:" + type);
            }
        }

        private void parseShowJob() {
            String[] split = block.split("\\s+");
            // do this way because not sure the order and other fields configured by slurm
            for (String s : split) {
                if (s.startsWith(USERID)) {
                    userId = s.substring(USERID_LENGTH);
                } else if (s.startsWith(JOBNAME)) {
                    jobName = s.substring(JOBNAME_LENGTH);
                } else if (s.startsWith(JOBID)) {
                    jobId = Long.parseLong(s.substring(JOBID_LENGTH));
                } else if (s.startsWith(JOBSTATE)) {
                    jobState = SlurmConstants.JobState.valueOf(s.substring(JOBSTATE_LENGTH));
                } else if (s.startsWith(EXITCODE)) {
                    final String str = s.substring(EXITCODE_LENGTH);
                    exitCode = Integer.parseInt(str.substring(0, str.indexOf(":")));
                } else if (s.startsWith(ARRAY_TASK_ID)) {
                    final String arrayTaskIdStr = s.substring(ARRAY_TASK_ID_LENGTH);
                    if (!arrayTaskIdStr.contains("-")) {
                        arrayTaskId = Integer.parseInt(arrayTaskIdStr);
                    } else {
                        // range ids are cancelled
                        arrayTaskId = Integer.parseInt(arrayTaskIdStr.split("-")[0]);
                    }
                } else if (s.startsWith(ARRAY_JOB_ID)) {
                    arrayJobId = Long.parseLong(s.substring(ARRAY_JOB_ID_LENGTH));
                }
            }
        }
    }

    private static final String JOBID = "JobId=";
    private static final int JOBID_LENGTH = 6;
    private static final String JOBNAME = "JobName=";
    private static final int JOBNAME_LENGTH = 8;
    private static final String USERID = "UserId=";
    private static final int USERID_LENGTH = 7;
    private static final String ARRAY_JOB_ID = "ArrayJobId=";
    private static final int ARRAY_JOB_ID_LENGTH = 11;
    private static final String JOBSTATE = "JobState=";
    private static final int JOBSTATE_LENGTH = 9;
    private static final String EXITCODE = "ExitCode=";
    private static final int EXITCODE_LENGTH = 9;
    private static final String ARRAY_TASK_ID = "ArrayTaskId=";
    private static final int ARRAY_TASK_ID_LENGTH = 12;

}
