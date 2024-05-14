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
        SHOW_JOB;
    }

    @Override
    public ScontrolResult send(CommandExecutor commandExecutor) throws SlurmCmdNonZeroException {
        CommandResult result = sendCmd(commandExecutor, cmd);
        return new ScontrolResult(result);
    }

    class ScontrolResult extends AbstractSlurmCmdResult {

        private List<ScontrolResultBean> resultBeanList = new ArrayList<>();

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
    // private static final String GROUPID = "GroupId=";
    // private static final int GROUPID_LENGTH = 8;
    // private static final String MCS_LABEL = "MCS_label=";
    // private static final int MCS_LABEL_LENGTH = 10;
    // private static final String PRIORITY = "Priority=";
    // private static final int PRIORITY_LENGTH = 9;
    // private static final String NICE = "Nice=";
    // private static final int NICE_LENGTH = 5;
    // private static final String ACCOUNT = "Account=";
    // private static final int ACCOUNT_LENGTH = 8;
    // private static final String QOS = "QOS=";
    // private static final int QOS_LENGTH = 4;
    private static final String JOBSTATE = "JobState=";
    private static final int JOBSTATE_LENGTH = 9;
    // private static final String REASON = "Reason=";
    // private static final int REASON_LENGTH = 7;
    // private static final String DEPENDENCY = "Dependency=";
    // private static final int DEPENDENCY_LENGTH = 11;
    // private static final String REQUEUE = "Requeue=";
    // private static final int REQUEUE_LENGTH = 8;
    // private static final String RESTARTS = "Restarts=";
    // private static final int RESTARTS_LENGTH = 9;
    // private static final String BATCHFLAG = "BatchFlag=";
    // private static final int BATCHFLAG_LENGTH = 10;
    // private static final String REBOOT = "Reboot=";
    // private static final int REBOOT_LENGTH = 7;
    private static final String EXITCODE = "ExitCode=";
    private static final int EXITCODE_LENGTH = 9;
    private static final String ARRAY_TASK_ID = "ArrayTaskId=";
    private static final int ARRAY_TASK_ID_LENGTH = 12;
    // private static final String RUNTIME = "RunTime=";
    // private static final int RUNTIME_LENGTH = 8;
    // private static final String TIMELIMIT = "TimeLimit=";
    // private static final int TIMELIMIT_LENGTH = 10;
    // private static final String TIMEMIN = "TimeMin=";
    // private static final int TIMEMIN_LENGTH = 8;
    // private static final String SUBMITTIME = "SubmitTime=";
    // private static final int SUBMITTIME_LENGTH = 11;
    // private static final String ELIGIBLETIME = "EligibleTime=";
    // private static final int ELIGIBLETIME_LENGTH = 13;
    // private static final String STARTTIME = "StartTime=";
    // private static final int STARTTIME_LENGTH = 10;
    // private static final String ENDTIME = "EndTime=";
    // private static final int ENDTIME_LENGTH = 8;
    // private static final String DEADLINE = "Deadline=";
    // private static final int DEADLINE_LENGTH = 9;
    // private static final String PREEMPTTIME = "PreemptTime=";
    // private static final int PREEMPTTIME_LENGTH = 12;
    // private static final String SUSPENDTIME = "SuspendTime=";
    // private static final int SUSPENDTIME_LENGTH = 12;
    // private static final String SECSPRESUSPEND = "SecsPreSuspend=";
    // private static final int SECSPRESUSPEND_LENGTH = 15;
    // private static final String PARTITION = "Partition=";
    // private static final int PARTITION_LENGTH = 10;
    // private static final String ALLOCNODE_SID = "AllocNode:Sid=";
    // private static final int ALLOCNODE_SID_LENGTH = 14;
    // private static final String REQNODELIST = "ReqNodeList=";
    // private static final int REQNODELIST_LENGTH = 12;
    // private static final String EXCNODELIST = "ExcNodeList=";
    // private static final int EXCNODELIST_LENGTH = 12;
    // private static final String NODELIST = "NodeList=";
    // private static final int NODELIST_LENGTH = 9;
    // private static final String BATCHHOST = "BatchHost=";
    // private static final int BATCHHOST_LENGTH = 10;
    // private static final String NUMNODES = "NumNodes=";
    // private static final int NUMNODES_LENGTH = 9;
    // private static final String NUMCPUS = "NumCPUs=";
    // private static final int NUMCPUS_LENGTH = 8;
    // private static final String NUMTASKS = "NumTasks=";
    // private static final int NUMTASKS_LENGTH = 9;
    // private static final String CPUS_TASK = "CPUs/Task=";
    // private static final int CPUS_TASK_LENGTH = 10;
    // private static final String REQB_S_C_T = "ReqB:S:C:T=";
    // private static final int REQB_S_C_T_LENGTH = 11;
    // private static final String TRES = "TRES=";
    // private static final int TRES_LENGTH = 5;
    // private static final String SOCKS_NODE = "Socks/Node=";
    // private static final int SOCKS_NODE_LENGTH = 11;
    // private static final String NTASKSPERN_B_S_C = "NtasksPerN:B:S:C=";
    // private static final int NTASKSPERN_B_S_C_LENGTH = 17;
    // private static final String CORESPEC = "CoreSpec=";
    // private static final int CORESPEC_LENGTH = 9;
    // private static final String MINCPUSNODE = "MinCPUsNode=";
    // private static final int MINCPUSNODE_LENGTH = 12;
    // private static final String MINMEMORYNODE = "MinMemoryNode=";
    // private static final int MINMEMORYNODE_LENGTH = 14;
    // private static final String MINTMPDISKNODE = "MinTmpDiskNode=";
    // private static final int MINTMPDISKNODE_LENGTH = 15;
    // private static final String FEATURES = "Features=";
    // private static final int FEATURES_LENGTH = 9;
    // private static final String GRES = "Gres=";
    // private static final int GRES_LENGTH = 5;
    // private static final String RESERVATION = "Reservation=";
    // private static final int RESERVATION_LENGTH = 12;
    // private static final String OVERSUBSCRIBE = "OverSubscribe=";
    // private static final int OVERSUBSCRIBE_LENGTH = 14;
    // private static final String CONTIGUOUS = "Contiguous=";
    // private static final int CONTIGUOUS_LENGTH = 11;
    // private static final String LICENSES = "Licenses=";
    // private static final int LICENSES_LENGTH = 9;
    // private static final String NETWORK = "Network=";
    // private static final int NETWORK_LENGTH = 8;
    // private static final String COMMAND = "Command=";
    // private static final int COMMAND_LENGTH = 8;
    // private static final String WORKDIR = "WorkDir=";
    // private static final int WORKDIR_LENGTH = 8;
    // private static final String STDERR = "StdErr=";
    // private static final int STDERR_LENGTH = 7;
    // private static final String STDIN = "StdIn=";
    // private static final int STDIN_LENGTH = 6;
    // private static final String STDOUT = "StdOut=";
    // private static final int STDOUT_LENGTH = 7;
    // private static final String POWER = "Power=";
    // private static final int POWER_LENGTH = 6;

}
