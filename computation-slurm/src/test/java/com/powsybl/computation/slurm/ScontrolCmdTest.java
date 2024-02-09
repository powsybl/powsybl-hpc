/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
class ScontrolCmdTest {

    @Test
    void test() throws SlurmCmdNonZeroException {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        CommandResult result = mock(CommandResult.class);
        when(result.getExitCode()).thenReturn(0);
        when(result.getStdOut()).thenReturn("""
            JobId=41524 JobName=metrix-chunk-1
               UserId=test(2000) GroupId=test(2000) MCS_label=N/A
               Priority=4294900045 Nice=0 Account=(null) QOS=normal
               JobState=RUNNING Reason=None Dependency=(null)
               Requeue=1 Restarts=0 BatchFlag=1 Reboot=0 ExitCode=0:0
               RunTime=1-05:11:18 TimeLimit=UNLIMITED TimeMin=N/A
               SubmitTime=2018-09-05T08:59:44 EligibleTime=2018-09-05T08:59:44
               StartTime=2018-09-05T08:59:46 EndTime=Unknown Deadline=N/A
               PreemptTime=None SuspendTime=None SecsPreSuspend=0
               Partition=normal AllocNode:Sid=pf9sosphpc03:5998
               ReqNodeList=(null) ExcNodeList=(null)
               NodeList=pf9sosrek3
               BatchHost=pf9sosrek3
               NumNodes=1 NumCPUs=8 NumTasks=1 CPUs/Task=1 ReqB:S:C:T=0:0:*:*
               TRES=cpu=8,node=1
               Socks/Node=* NtasksPerN:B:S:C=0:0:*:* CoreSpec=*
               MinCPUsNode=1 MinMemoryNode=0 MinTmpDiskNode=0
               Features=(null) Gres=(null) Reservation=(null)
               OverSubscribe=YES Contiguous=0 Licenses=(null) Network=(null)
               Command=/home/test/tmp/metrix-1-1913359386641203013/metrix-chunk-1_8.batch
               WorkDir=/home/test/tmp/metrix-1-1913359386641203013
               StdErr=/home/test/tmp/metrix-1-1913359386641203013/metrix-chunk-1_8.err
               StdIn=/dev/null
               StdOut=/home/test/tmp/metrix-1-1913359386641203013/metrix-chunk-1_8.out
               Power=""");
        when(commandExecutor.execute("scontrol show job 41524")).thenReturn(result);
        ScontrolCmd scontrolCmd = ScontrolCmdFactory.showJob(41524);
        ScontrolCmd.ScontrolResult scontrolResult = scontrolCmd.send(commandExecutor);
        assertEquals("test(2000)", scontrolResult.getResult().getUserId());
        assertEquals("metrix-chunk-1", scontrolResult.getResult().getJobName());
        assertEquals(SlurmConstants.JobState.RUNNING, scontrolResult.getResult().getJobState());
    }

    @Test
    void testArray() throws SlurmCmdNonZeroException {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        CommandResult result = mock(CommandResult.class);
        when(result.getExitCode()).thenReturn(0);
        when(result.getStdOut()).thenReturn("""
            JobId=175667 ArrayJobId=175667 ArrayTaskId=1 JobName=cmdId
               UserId=test(619800090) GroupId=test(619800090) MCS_label=N/A
               Priority=1000 Nice=0 Account=test QOS=test
               JobState=COMPLETED Reason=None Dependency=(null)
               Requeue=1 Restarts=0 BatchFlag=1 Reboot=0 ExitCode=1:0
               RunTime=00:00:00 TimeLimit=2-00:00:00 TimeMin=N/A
               SubmitTime=2020-10-05T14:39:01 EligibleTime=2020-10-05T14:39:01
               AccrueTime=Unknown
               StartTime=2020-10-05T14:39:01 EndTime=2020-10-05T14:39:01 Deadline=N/A
               SuspendTime=None SecsPreSuspend=0 LastSchedEval=2020-10-05T14:39:01
               Partition=calinopf AllocNode:Sid=pf9sosphpc3:12517
               ReqNodeList=(null) ExcNodeList=(null)
               NodeList=pf9sosrek3
               BatchHost=pf9sosrek3
               NumNodes=1 NumCPUs=8 NumTasks=1 CPUs/Task=1 ReqB:S:C:T=0:0:*:*
               TRES=cpu=8,node=1,billing=8
               Socks/Node=* NtasksPerN:B:S:C=0:0:*:* CoreSpec=*
               MinCPUsNode=1 MinMemoryNode=0 MinTmpDiskNode=0
               Features=(null) DelayBoot=00:00:00
               OverSubscribe=YES Contiguous=0 Licenses=(null) Network=(null)
               Command=/home/test/yichen_slurm_unit/unit_test_3609105074134694169/cmdId.batch
               WorkDir=/home/test/yichen_slurm_unit/unit_test_3609105074134694169
               StdErr=/home/test/yichen_slurm_unit/unit_test_3609105074134694169/cmdId_1.err
               StdIn=/dev/null
               StdOut=/home/test/yichen_slurm_unit/unit_test_3609105074134694169/cmdId_1.out
               Power=
               KillOInInvalidDependent=Yes
               MailUser=(null) MailType=NONE

            JobId=175668 ArrayJobId=175667 ArrayTaskId=0 JobName=cmdId
               UserId=test(619800090) GroupId=test(619800090) MCS_label=N/A
               Priority=1000 Nice=0 Account=test QOS=test
               JobState=COMPLETED Reason=None Dependency=(null)
               Requeue=1 Restarts=0 BatchFlag=1 Reboot=0 ExitCode=0:0
               RunTime=00:00:00 TimeLimit=2-00:00:00 TimeMin=N/A
               SubmitTime=2020-10-05T14:39:01 EligibleTime=2020-10-05T14:39:01
               AccrueTime=Unknown
               StartTime=2020-10-05T14:39:01 EndTime=2020-10-05T14:39:01 Deadline=N/A
               SuspendTime=None SecsPreSuspend=0 LastSchedEval=2020-10-05T14:39:01
               Partition=calinopf AllocNode:Sid=pf9sosphpc3:12517
               ReqNodeList=(null) ExcNodeList=(null)
               NodeList=pf9sosrek2
               BatchHost=pf9sosrek2
               NumNodes=1 NumCPUs=8 NumTasks=1 CPUs/Task=1 ReqB:S:C:T=0:0:*:*
               TRES=cpu=8,node=1,billing=8
               Socks/Node=* NtasksPerN:B:S:C=0:0:*:* CoreSpec=*
               MinCPUsNode=1 MinMemoryNode=0 MinTmpDiskNode=0
               Features=(null) DelayBoot=00:00:00
               OverSubscribe=YES Contiguous=0 Licenses=(null) Network=(null)
               Command=/home/test/yichen_slurm_unit/unit_test_3609105074134694169/cmdId.batch
               WorkDir=/home/test/yichen_slurm_unit/unit_test_3609105074134694169
               StdErr=/home/test/yichen_slurm_unit/unit_test_3609105074134694169/cmdId_0.err
               StdIn=/dev/null
               StdOut=/home/test/yichen_slurm_unit/unit_test_3609105074134694169/cmdId_0.out
               Power=
               KillOInInvalidDependent=Yes
               MailUser=(null) MailType=NONE

            """);
        when(commandExecutor.execute("scontrol show job 175667")).thenReturn(result);
        ScontrolCmd scontrolCmd = ScontrolCmdFactory.showJob(175667);
        ScontrolCmd.ScontrolResult scontrolResult = scontrolCmd.send(commandExecutor);
        final List<ScontrolCmd.ScontrolResultBean> resultBeanList = scontrolResult.getResultBeanList();
        assertEquals(2, resultBeanList.size());
        final ScontrolCmd.ScontrolResultBean bean = resultBeanList.get(0);
        assertEquals(175667, bean.getJobId());
        assertEquals(1, bean.getExitCode());
    }
}
