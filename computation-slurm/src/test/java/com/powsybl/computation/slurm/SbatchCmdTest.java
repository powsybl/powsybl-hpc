/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
class SbatchCmdTest {

    @Test
    void testJobArray() {
        SbatchCmdBuilder builder = new SbatchCmdBuilder();
        SbatchCmd cmd = builder.jobName("array3")
                .script("submit.sh")
                .array(3)
                .build();
        assertEquals("sbatch --job-name=array3 --array=0-2 --kill-on-invalid-dep=yes submit.sh", cmd.toString());
        builder = new SbatchCmdBuilder();
        cmd = builder.jobName("array1")
                .script("submit.sh")
                .build();
        assertEquals("sbatch --job-name=array1 --kill-on-invalid-dep=yes submit.sh", cmd.toString());
    }

    @Test
    void invalidJobArray() {
        SbatchCmdBuilder builder = new SbatchCmdBuilder();
        builder.jobName("test")
                .script("submit.sh");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> builder.array(-1));
        assertEquals("-1 is not validate for array.", e.getMessage());
    }

    @Test
    void testAftercorr() {
        SbatchCmdBuilder builder = new SbatchCmdBuilder();
        SbatchCmd cmd = builder.jobName("jobname")
                .script("submit.sh")
                .aftercorr(Collections.singletonList(1111L))
                .build();
        assertEquals("sbatch --job-name=jobname --dependency=aftercorr:1111 --kill-on-invalid-dep=yes submit.sh", cmd.toString());
        builder = new SbatchCmdBuilder();
        cmd = builder.jobName("jobname")
                .script("submit.sh")
                .aftercorr(Arrays.asList(1111L, 2222L, 3333L))
                .build();
        assertEquals("sbatch --job-name=jobname --dependency=aftercorr:1111:2222:3333 --kill-on-invalid-dep=yes submit.sh", cmd.toString());
    }

    @Test
    void testOptions() {
        SbatchCmdBuilder builder = new SbatchCmdBuilder();
        SbatchCmd cmd = builder.jobName("array1")
                .script("submit.sh")
                .oversubscribe()
                .build();
        assertEquals("sbatch --job-name=array1 --kill-on-invalid-dep=yes --oversubscribe submit.sh", cmd.toString());
    }

    @Test
    void testTimeout() {
        SbatchCmd cmd = new SbatchCmdBuilder().jobName("foo")
                .script("foo.sh")
                .timeout("2:00")
                .build();
        assertEquals("sbatch --job-name=foo --kill-on-invalid-dep=yes --time=2:00 foo.sh", cmd.toString());
        String nullDuration = null;
        SbatchCmd nullableTimeout = new SbatchCmdBuilder().jobName("foo")
                .script("foo.sh")
                .timeout(nullDuration)
                .build();
        assertEquals("sbatch --job-name=foo --kill-on-invalid-dep=yes --time=UNLIMITED foo.sh", nullableTimeout.toString());
    }

    @Test
    void testQos() {
        SbatchCmdBuilder builder = new SbatchCmdBuilder();
        SbatchCmd cmd = builder.jobName("testQos")
                .script("submit.sh")
                .qos("value_qos")
                .build();
        assertEquals("sbatch --job-name=testQos --qos=value_qos --kill-on-invalid-dep=yes submit.sh", cmd.toString());
    }

    @Test
    void testDeadline() {
        SbatchCmdBuilder builder = new SbatchCmdBuilder();
        SbatchCmd cmd = builder.jobName("dead")
                .script("submit.sh")
                .deadline(10)
                .build();
        assertEquals("sbatch --job-name=dead --kill-on-invalid-dep=yes --deadline=`date -d \"10 seconds\" \"+%Y-%m-%dT%H:%M:%S\"` submit.sh", cmd.toString());
    }

    @Test
    void testDir() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path dir = fileSystem.getPath("/tmp/foo");
            SbatchCmdBuilder builder = new SbatchCmdBuilder();
            SbatchCmd cmd = builder.jobName("testDir")
                    .script("submit.sh")
                    .workDir(dir)
                    .build();
            assertEquals("sbatch -D /tmp/foo --job-name=testDir --kill-on-invalid-dep=yes submit.sh", cmd.toString());
        }
    }

    @Test
    void testMem() {
        SbatchCmdBuilder builder = new SbatchCmdBuilder();
        SbatchCmd cmd = builder.jobName("testMem")
                .script("submit.sh")
                .mem(20)
                .build();
        assertEquals("sbatch --job-name=testMem --mem=20M --kill-on-invalid-dep=yes submit.sh", cmd.toString());
    }
}
