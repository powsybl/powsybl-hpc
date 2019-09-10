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
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SbatchArgumentsTest {

    private static final String INVALID_DEP_YES = "#SBATCH --kill-on-invalid-dep=yes";

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testJobArray() {
        SbatchArguments builder = new SbatchArguments();
        List<String> script = builder.jobName("array3")
                .array(3)
                .toScript();
        assertEquals(Arrays.asList("#SBATCH --job-name=array3", "#SBATCH --array=0-2", INVALID_DEP_YES), script);
        builder = new SbatchArguments();
        script = builder.jobName("array1")
                .array(1)
                .toScript();
        assertEquals(Arrays.asList("#SBATCH --job-name=array1", INVALID_DEP_YES), script);
    }

    @Test
    public void invalidJobArray() {
        thrown.expect(IllegalArgumentException.class);
        SbatchArguments builder = new SbatchArguments();
        builder.jobName("test")
                .array(-1)
                .toScript();
    }

    @Test
    public void testAftercorr() {
        SbatchArguments builder = new SbatchArguments();
        List<String> script = builder.jobName("jobname")
                .aftercorr(1111L)
                .toScript();
        assertEquals(Arrays.asList("#SBATCH --job-name=jobname", "#SBATCH --dependency=aftercorr:1111", INVALID_DEP_YES), script);
        builder = new SbatchArguments();
        script = builder.jobName("jobname")
                .aftercorr(Arrays.asList(1111L, 2222L, 3333L))
                .toScript();
        assertEquals(Arrays.asList("#SBATCH --job-name=jobname", "#SBATCH --dependency=aftercorr:1111:2222:3333", INVALID_DEP_YES), script);
    }

    @Test
    public void testOptions() {
        SbatchArguments builder = new SbatchArguments();
        List<String> script = builder.jobName("array1")
                .array(1)
                .oversubscribe()
                .toScript();
        assertEquals(Arrays.asList("#SBATCH --job-name=array1", INVALID_DEP_YES, "#SBATCH --oversubscribe"), script);
    }

    @Test
    public void testTimeout() {
        List<String> script = new SbatchArguments().jobName("foo")
                .timeout("2:00")
                .toScript();
        assertEquals(Arrays.asList("#SBATCH --job-name=foo", "#SBATCH --kill-on-invalid-dep=yes", "#SBATCH --time=2:00"), script);
        String nullDuration = null;
        List<String> nullableTimeout = new SbatchArguments().jobName("foo")
                .timeout(nullDuration)
                .toScript();
        assertEquals(Arrays.asList("#SBATCH --job-name=foo", "#SBATCH --kill-on-invalid-dep=yes", "#SBATCH --time=UNLIMITED"), nullableTimeout);
    }

    @Test
    public void testQos() {
        SbatchArguments builder = new SbatchArguments();
        List<String> script = builder.jobName("testQos")
                .qos("value_qos")
                .toScript();
        assertEquals(Arrays.asList("#SBATCH --job-name=testQos", "#SBATCH --qos=value_qos", INVALID_DEP_YES), script);
    }

//    @Test
//    public void testDeadline() {
//        SbatchArguments builder = new SbatchArguments();
//        List<String> script = builder.jobName("dead")
//                .deadline(10)
//                .toScript();
//        assertEquals(Arrays.asList("#SBATCH --job-name=dead --kill-on-invalid-dep=yes --deadline=`date -d \"10 seconds\" \"+%Y-%m-%dT%H:%M:%S\"` submit.sh", script);
//    }

    @Test
    public void testDir() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path dir = fileSystem.getPath("/tmp/foo");
            SbatchArguments builder = new SbatchArguments();
            List<String> script = builder.jobName("testDir")
                    .workDir(dir)
                    .toScript();
            assertEquals(Arrays.asList("#SBATCH -D /tmp/foo", "#SBATCH --job-name=testDir", INVALID_DEP_YES), script);
        }
    }
}
