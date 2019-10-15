/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SbatchCmdTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testJobArray() {
        SbatchCmdBuilder builder = new SbatchCmdBuilder();
        SbatchCmd cmd = builder.jobName("array3")
                .script("submit.sh")
                .array(3)
                .build();
        assertEquals("sbatch --job-name=array3 --array=0-2 submit.sh", cmd.toString());
        builder = new SbatchCmdBuilder();
        cmd = builder.jobName("array1")
                .script("submit.sh")
                .array(1)
                .build();
        assertEquals("sbatch --job-name=array1 --array=0 submit.sh", cmd.toString());
    }

    @Test
    public void invalidJobArray() {
        thrown.expect(IllegalArgumentException.class);
        SbatchCmdBuilder builder = new SbatchCmdBuilder();
        builder.jobName("test")
                .script("submit.sh")
                .array(-1)
                .build();
    }

    @Test
    public void testAftercorr() {
        SbatchCmdBuilder builder = new SbatchCmdBuilder();
        SbatchCmd cmd = builder.jobName("jobname")
                .script("submit.sh")
                .aftercorr(Collections.singletonList(1111L))
                .build();
        assertEquals("sbatch --job-name=jobname --dependency=aftercorr:1111 submit.sh", cmd.toString());
        builder = new SbatchCmdBuilder();
        cmd = builder.jobName("jobname")
                .script("submit.sh")
                .aftercorr(Arrays.asList(1111L, 2222L, 3333L))
                .build();
        assertEquals("sbatch --job-name=jobname --dependency=aftercorr:1111:2222:3333 submit.sh", cmd.toString());
    }

    @Test
    public void testOptions() {
        SbatchCmdBuilder builder = new SbatchCmdBuilder();
        SbatchCmd cmd = builder.jobName("array1")
                .script("submit.sh")
                .array(1)
                .oversubscribe()
                .build();
        assertEquals("sbatch --job-name=array1 --array=0 --oversubscribe submit.sh", cmd.toString());
    }

    @Test
    public void testTimeout() {
        SbatchCmd cmd = new SbatchCmdBuilder().jobName("foo")
                .script("foo.sh")
                .timeout("2:00")
                .build();
        assertEquals("sbatch --job-name=foo --time=2:00 foo.sh", cmd.toString());
        String nullDuration = null;
        SbatchCmd nullableTimeout = new SbatchCmdBuilder().jobName("foo")
                .script("foo.sh")
                .timeout(nullDuration)
                .build();
        assertEquals("sbatch --job-name=foo --time=UNLIMITED foo.sh", nullableTimeout.toString());
    }

    @Test
    public void testQos() {
        SbatchCmdBuilder builder = new SbatchCmdBuilder();
        SbatchCmd cmd = builder.jobName("testQos")
                .script("submit.sh")
                .qos("value_qos")
                .build();
        assertEquals("sbatch --job-name=testQos --qos=value_qos submit.sh", cmd.toString());
    }

    @Test
    public void testDeadline() {
        SbatchCmdBuilder builder = new SbatchCmdBuilder();
        SbatchCmd cmd = builder.jobName("dead")
                .script("submit.sh")
                .deadline(10)
                .build();
        assertEquals("sbatch --job-name=dead --deadline=`date -d \"10 seconds\" \"+%Y-%m-%dT%H:%M:%S\"` submit.sh", cmd.toString());
    }

    @Test
    public void testDir() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path dir = fileSystem.getPath("/tmp/foo");
            SbatchCmdBuilder builder = new SbatchCmdBuilder();
            SbatchCmd cmd = builder.jobName("testDir")
                    .script("submit.sh")
                    .workDir(dir)
                    .build();
            assertEquals("sbatch -D /tmp/foo --job-name=testDir submit.sh", cmd.toString());
        }
    }

    @Test
    public void testPriority()  {
        SbatchCmdBuilder builder = new SbatchCmdBuilder();
        SbatchCmd cmd = builder.jobName("testPriority")
                .script("submit.sh")
                .nice(2)
                .priority(1)
                .build();
        assertEquals("sbatch --job-name=testPriority --priority=1 --nice=2 submit.sh", cmd.toString());
    }
}
