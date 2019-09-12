/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class ScontrolCmdTest {

    @Test
    public void test() throws SlurmCmdNonZeroException {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        CommandResult result = mock(CommandResult.class);
        when(result.getExitCode()).thenReturn(0);
        when(result.getStdOut()).thenReturn("JobId=41524 JobName=metrix-chunk-1\n" +
                "   UserId=test(2000) GroupId=test(2000) MCS_label=N/A\n" +
                "   Priority=4294900045 Nice=0 Account=(null) QOS=normal\n" +
                "   JobState=RUNNING Reason=None Dependency=(null)\n" +
                "   Requeue=1 Restarts=0 BatchFlag=1 Reboot=0 ExitCode=0:0\n" +
                "   RunTime=1-05:11:18 TimeLimit=UNLIMITED TimeMin=N/A\n" +
                "   SubmitTime=2018-09-05T08:59:44 EligibleTime=2018-09-05T08:59:44\n" +
                "   StartTime=2018-09-05T08:59:46 EndTime=Unknown Deadline=N/A\n" +
                "   PreemptTime=None SuspendTime=None SecsPreSuspend=0\n" +
                "   Partition=normal AllocNode:Sid=pf9sosphpc03:5998\n" +
                "   ReqNodeList=(null) ExcNodeList=(null)\n" +
                "   NodeList=pf9sosrek3\n" +
                "   BatchHost=pf9sosrek3\n" +
                "   NumNodes=1 NumCPUs=8 NumTasks=1 CPUs/Task=1 ReqB:S:C:T=0:0:*:*\n" +
                "   TRES=cpu=8,node=1\n" +
                "   Socks/Node=* NtasksPerN:B:S:C=0:0:*:* CoreSpec=*\n" +
                "   MinCPUsNode=1 MinMemoryNode=0 MinTmpDiskNode=0\n" +
                "   Features=(null) Gres=(null) Reservation=(null)\n" +
                "   OverSubscribe=YES Contiguous=0 Licenses=(null) Network=(null)\n" +
                "   Command=/home/test/tmp/metrix-1-1913359386641203013/metrix-chunk-1_8.batch\n" +
                "   WorkDir=/home/test/tmp/metrix-1-1913359386641203013\n" +
                "   StdErr=/home/test/tmp/metrix-1-1913359386641203013/metrix-chunk-1_8.err\n" +
                "   StdIn=/dev/null\n" +
                "   StdOut=/home/test/tmp/metrix-1-1913359386641203013/metrix-chunk-1_8.out\n" +
                "   Power=");
        when(commandExecutor.execute("scontrol show job 41524")).thenReturn(result);
        ScontrolCmd scontrolCmd = ScontrolCmdFactory.showJob(41524);
        ScontrolCmd.ScontrolResult scontrolResult = scontrolCmd.send(commandExecutor);
        assertEquals("test(2000)", scontrolResult.getUserId());
        assertEquals("metrix-chunk-1", scontrolResult.getJobName());
        assertEquals(SlurmConstants.JobState.RUNNING, scontrolResult.getJobState());
    }

    @Test
    public void testOneExecutionFailed() throws SlurmCmdNonZeroException {
        String str = "JobId=38295 ArrayJobId=38295 ArrayTaskId=4 JobName=oddEven\n" +
                "   UserId=dev-foo(619800090) GroupId=dev-foo(619800090) MCS_label=N/A\n" +
                "   Priority=4294893687 Nice=0 Account=foo QOS=foo\n" +
                "   JobState=COMPLETED Reason=None Dependency=(null)\n" +
                "   Requeue=1 Restarts=0 BatchFlag=1 Reboot=0 ExitCode=0:0\n" +
                "   RunTime=00:00:00 TimeLimit=2-00:00:00 TimeMin=N/A\n" +
                "   SubmitTime=2019-09-12T14:45:59 EligibleTime=2019-09-12T14:45:59\n" +
                "   AccrueTime=Unknown\n" +
                "   StartTime=2019-09-12T14:45:59 EndTime=2019-09-12T14:45:59 Deadline=N/A\n" +
                "   SuspendTime=None SecsPreSuspend=0 LastSchedEval=2019-09-12T14:45:59\n" +
                "   Partition=hpc AllocNode:Sid=foo.com:2222\n" +
                "   ReqNodeList=(null) ExcNodeList=(null)\n" +
                "   NodeList=pf9sosrek1\n" +
                "   BatchHost=pf9sosrek1\n" +
                "   NumNodes=1 NumCPUs=8 NumTasks=1 CPUs/Task=1 ReqB:S:C:T=0:0:*:*\n" +
                "   TRES=cpu=8,node=1,billing=8\n" +
                "   Socks/Node=* NtasksPerN:B:S:C=0:0:*:* CoreSpec=*\n" +
                "   MinCPUsNode=1 MinMemoryNode=0 MinTmpDiskNode=0\n" +
                "   Features=(null) DelayBoot=00:00:00\n" +
                "   OverSubscribe=YES Contiguous=0 Licenses=(null) Network=(null)\n" +
                "   Command=/home/dev-foo/bar/unit_test_3531940799349509624/oddEven.batch\n" +
                "   WorkDir=/home/dev-foo/bar/unit_test_3531940799349509624\n" +
                "   StdErr=/home/dev-foo/bar/unit_test_3531940799349509624/oddEven_4.err\n" +
                "   StdIn=/dev/null\n" +
                "   StdOut=/home/dev-foo/bar/unit_test_3531940799349509624/oddEven_4.out\n" +
                "   Power=\n" +
                "   KillOInInvalidDependent=Yes\n" +
                "\n" +
                "JobId=38299 ArrayJobId=38295 ArrayTaskId=3 JobName=oddEven\n" +
                "   UserId=dev-foo(619800090) GroupId=dev-foo(619800090) MCS_label=N/A\n" +
                "   Priority=4294893687 Nice=0 Account=foo QOS=foo\n" +
                "   JobState=FAILED Reason=NonZeroExitCode Dependency=(null)\n" +
                "   Requeue=1 Restarts=0 BatchFlag=1 Reboot=0 ExitCode=127:0\n" +
                "   RunTime=00:00:00 TimeLimit=2-00:00:00 TimeMin=N/A\n" +
                "   SubmitTime=2019-09-12T14:45:59 EligibleTime=2019-09-12T14:45:59\n" +
                "   AccrueTime=Unknown\n" +
                "   StartTime=2019-09-12T14:45:59 EndTime=2019-09-12T14:45:59 Deadline=N/A\n" +
                "   SuspendTime=None SecsPreSuspend=0 LastSchedEval=2019-09-12T14:45:59\n" +
                "   Partition=hpc AllocNode:Sid=foo.com:2222\n" +
                "   ReqNodeList=(null) ExcNodeList=(null)\n" +
                "   NodeList=pf9sosrek4\n" +
                "   BatchHost=pf9sosrek4\n" +
                "   NumNodes=1 NumCPUs=8 NumTasks=1 CPUs/Task=1 ReqB:S:C:T=0:0:*:*\n" +
                "   TRES=cpu=8,node=1,billing=8\n" +
                "   Socks/Node=* NtasksPerN:B:S:C=0:0:*:* CoreSpec=*\n" +
                "   MinCPUsNode=1 MinMemoryNode=0 MinTmpDiskNode=0\n" +
                "   Features=(null) DelayBoot=00:00:00\n" +
                "   OverSubscribe=YES Contiguous=0 Licenses=(null) Network=(null)\n" +
                "   Command=/home/dev-foo/bar/unit_test_3531940799349509624/oddEven.batch\n" +
                "   WorkDir=/home/dev-foo/bar/unit_test_3531940799349509624\n" +
                "   StdErr=/home/dev-foo/bar/unit_test_3531940799349509624/oddEven_3.err\n" +
                "   StdIn=/dev/null\n" +
                "   StdOut=/home/dev-foo/bar/unit_test_3531940799349509624/oddEven_3.out\n" +
                "   Power=\n" +
                "   KillOInInvalidDependent=Yes\n" +
                "\n" +
                "JobId=38298 ArrayJobId=38295 ArrayTaskId=2 JobName=oddEven\n" +
                "   UserId=dev-foo(619800090) GroupId=dev-foo(619800090) MCS_label=N/A\n" +
                "   Priority=4294893687 Nice=0 Account=foo QOS=foo\n" +
                "   JobState=COMPLETED Reason=None Dependency=(null)\n" +
                "   Requeue=1 Restarts=0 BatchFlag=1 Reboot=0 ExitCode=0:0\n" +
                "   RunTime=00:00:00 TimeLimit=2-00:00:00 TimeMin=N/A\n" +
                "   SubmitTime=2019-09-12T14:45:59 EligibleTime=2019-09-12T14:45:59\n" +
                "   AccrueTime=Unknown\n" +
                "   StartTime=2019-09-12T14:45:59 EndTime=2019-09-12T14:45:59 Deadline=N/A\n" +
                "   SuspendTime=None SecsPreSuspend=0 LastSchedEval=2019-09-12T14:45:59\n" +
                "   Partition=hpc AllocNode:Sid=foo.com:2222\n" +
                "   ReqNodeList=(null) ExcNodeList=(null)\n" +
                "   NodeList=pf9sosrek3\n" +
                "   BatchHost=pf9sosrek3\n" +
                "   NumNodes=1 NumCPUs=8 NumTasks=1 CPUs/Task=1 ReqB:S:C:T=0:0:*:*\n" +
                "   TRES=cpu=8,node=1,billing=8\n" +
                "   Socks/Node=* NtasksPerN:B:S:C=0:0:*:* CoreSpec=*\n" +
                "   MinCPUsNode=1 MinMemoryNode=0 MinTmpDiskNode=0\n" +
                "   Features=(null) DelayBoot=00:00:00\n" +
                "   OverSubscribe=YES Contiguous=0 Licenses=(null) Network=(null)\n" +
                "   Command=/home/dev-foo/bar/unit_test_3531940799349509624/oddEven.batch\n" +
                "   WorkDir=/home/dev-foo/bar/unit_test_3531940799349509624\n" +
                "   StdErr=/home/dev-foo/bar/unit_test_3531940799349509624/oddEven_2.err\n" +
                "   StdIn=/dev/null\n" +
                "   StdOut=/home/dev-foo/bar/unit_test_3531940799349509624/oddEven_2.out\n" +
                "   Power=\n" +
                "   KillOInInvalidDependent=Yes\n" +
                "\n" +
                "JobId=38297 ArrayJobId=38295 ArrayTaskId=1 JobName=oddEven\n" +
                "   UserId=dev-foo(619800090) GroupId=dev-foo(619800090) MCS_label=N/A\n" +
                "   Priority=4294893687 Nice=0 Account=foo QOS=foo\n" +
                "   JobState=COMPLETED Reason=None Dependency=(null)\n" +
                "   Requeue=1 Restarts=0 BatchFlag=1 Reboot=0 ExitCode=0:0\n" +
                "   RunTime=00:00:00 TimeLimit=2-00:00:00 TimeMin=N/A\n" +
                "   SubmitTime=2019-09-12T14:45:59 EligibleTime=2019-09-12T14:45:59\n" +
                "   AccrueTime=Unknown\n" +
                "   StartTime=2019-09-12T14:45:59 EndTime=2019-09-12T14:45:59 Deadline=N/A\n" +
                "   SuspendTime=None SecsPreSuspend=0 LastSchedEval=2019-09-12T14:45:59\n" +
                "   Partition=hpc AllocNode:Sid=foo.com:2222\n" +
                "   ReqNodeList=(null) ExcNodeList=(null)\n" +
                "   NodeList=pf9sosrek2\n" +
                "   BatchHost=pf9sosrek2\n" +
                "   NumNodes=1 NumCPUs=8 NumTasks=1 CPUs/Task=1 ReqB:S:C:T=0:0:*:*\n" +
                "   TRES=cpu=8,node=1,billing=8\n" +
                "   Socks/Node=* NtasksPerN:B:S:C=0:0:*:* CoreSpec=*\n" +
                "   MinCPUsNode=1 MinMemoryNode=0 MinTmpDiskNode=0\n" +
                "   Features=(null) DelayBoot=00:00:00\n" +
                "   OverSubscribe=YES Contiguous=0 Licenses=(null) Network=(null)\n" +
                "   Command=/home/dev-foo/bar/unit_test_3531940799349509624/oddEven.batch\n" +
                "   WorkDir=/home/dev-foo/bar/unit_test_3531940799349509624\n" +
                "   StdErr=/home/dev-foo/bar/unit_test_3531940799349509624/oddEven_1.err\n" +
                "   StdIn=/dev/null\n" +
                "   StdOut=/home/dev-foo/bar/unit_test_3531940799349509624/oddEven_1.out\n" +
                "   Power=\n" +
                "   KillOInInvalidDependent=Yes\n" +
                "\n" +
                "JobId=38296 ArrayJobId=38295 ArrayTaskId=0 JobName=oddEven\n" +
                "   UserId=dev-foo(619800090) GroupId=dev-foo(619800090) MCS_label=N/A\n" +
                "   Priority=4294893687 Nice=0 Account=foo QOS=foo\n" +
                "   JobState=COMPLETED Reason=None Dependency=(null)\n" +
                "   Requeue=1 Restarts=0 BatchFlag=1 Reboot=0 ExitCode=0:0\n" +
                "   RunTime=00:00:00 TimeLimit=2-00:00:00 TimeMin=N/A\n" +
                "   SubmitTime=2019-09-12T14:45:59 EligibleTime=2019-09-12T14:45:59\n" +
                "   AccrueTime=Unknown\n" +
                "   StartTime=2019-09-12T14:45:59 EndTime=2019-09-12T14:45:59 Deadline=N/A\n" +
                "   SuspendTime=None SecsPreSuspend=0 LastSchedEval=2019-09-12T14:45:59\n" +
                "   Partition=hpc AllocNode:Sid=foo.com:2222\n" +
                "   ReqNodeList=(null) ExcNodeList=(null)\n" +
                "   NodeList=pf9sosrek1\n" +
                "   BatchHost=pf9sosrek1\n" +
                "   NumNodes=1 NumCPUs=8 NumTasks=1 CPUs/Task=1 ReqB:S:C:T=0:0:*:*\n" +
                "   TRES=cpu=8,node=1,billing=8\n" +
                "   Socks/Node=* NtasksPerN:B:S:C=0:0:*:* CoreSpec=*\n" +
                "   MinCPUsNode=1 MinMemoryNode=0 MinTmpDiskNode=0\n" +
                "   Features=(null) DelayBoot=00:00:00\n" +
                "   OverSubscribe=YES Contiguous=0 Licenses=(null) Network=(null)\n" +
                "   Command=/home/dev-foo/bar/unit_test_3531940799349509624/oddEven.batch\n" +
                "   WorkDir=/home/dev-foo/bar/unit_test_3531940799349509624\n" +
                "   StdErr=/home/dev-foo/bar/unit_test_3531940799349509624/oddEven_0.err\n" +
                "   StdIn=/dev/null\n" +
                "   StdOut=/home/dev-foo/bar/unit_test_3531940799349509624/oddEven_0.out\n" +
                "   Power=\n" +
                "   KillOInInvalidDependent=Yes\n";
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        CommandResult result = mock(CommandResult.class);
        when(result.getExitCode()).thenReturn(0);
        when(result.getStdOut()).thenReturn(str);
        when(commandExecutor.execute("scontrol show job 38295")).thenReturn(result);
        ScontrolCmd scontrolCmd = ScontrolCmdFactory.showJob(38295);
        ScontrolCmd.ScontrolResult scontrolResult = scontrolCmd.send(commandExecutor);
        assertEquals(SlurmConstants.JobState.FAILED, scontrolResult.getJobState());
    }
}
