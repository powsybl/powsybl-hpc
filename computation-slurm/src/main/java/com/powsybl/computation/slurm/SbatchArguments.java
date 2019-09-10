/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class contains two map and one set for three types of arguments in sbatch.
 * @see <a href="https://slurm.schedmd.com/sbatch.html">Sbatch</a>
 *
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
class SbatchArguments {

    private static final String PRECEDENT = "#SBATCH ";
    private static final String DENPENDENCY_OPT = "dependency";

    private final Map<String, String> sbatchArgsByName = new HashMap<>();
    private final Map<Character, String> sbatchArgsByCharacter = new HashMap<>();
    private final TreeSet<String> sbatchOptions = new TreeSet<>();

    SbatchArguments() {
        killOnInvalidDep();
    }

    SbatchArguments jobName(String jobName) {
        sbatchArgsByName.put("job-name", jobName);
        return this;
    }

    /**
     *
     * @param i if equals 1, --array would not be set. If negative, exception would be thrown.
     * @return this builder
     */
    SbatchArguments array(int i) {
        if (i <= 0) {
            throw new IllegalArgumentException(i + " is not validate for array.");
        }
        if (i > 1) {
            sbatchArgsByName.put("array", "0-" + (i - 1));
        }
        return this;
    }

    SbatchArguments aftercorr(List<Long> jobIds) {
        Objects.requireNonNull(jobIds);
        if (!jobIds.isEmpty()) {
            String coll = jobIds.stream().map(Object::toString).collect(Collectors.joining(":", "aftercorr:", ""));
            sbatchArgsByName.put(DENPENDENCY_OPT, coll);
        }
        return this;
    }

    SbatchArguments aftercorr(@Nullable Long preMasterJob) {
        if (preMasterJob != null) {
            sbatchArgsByName.put(DENPENDENCY_OPT, "aftercorr:" + preMasterJob);
        }
        return this;
    }

    SbatchArguments afternotok(Long lastMasterJob) {
        if (lastMasterJob != null) {
            sbatchArgsByName.put(DENPENDENCY_OPT, "afternotok:" + lastMasterJob);
        }
        return this;
    }

    SbatchArguments nodes(int i) {
        if (i < 1) {
            throw new IllegalArgumentException(i + " is not validate for nodes.");
        }
        sbatchArgsByName.put("nodes", Integer.toString(i));
        return this;
    }

    SbatchArguments ntasks(int i) {
        if (i < 1) {
            throw new IllegalArgumentException(i + " is not validate for ntasks.");
        }
        sbatchArgsByName.put("ntasks", Integer.toString(i));
        return this;
    }

    SbatchArguments tasksPerNode(int i) {
        if (i < 1) {
            throw new IllegalArgumentException(i + " is not validate for tasksPerNode.");
        }
        sbatchArgsByName.put("ntasks-per-node", Integer.toString(i));
        return this;
    }

    SbatchArguments cpusPerTask(int i) {
        sbatchArgsByName.put("cpus-per-task", Integer.toString(i));
        return this;
    }

    // TODO test
    SbatchArguments error(String pattern) {
        Objects.requireNonNull(pattern);
        sbatchArgsByName.put("error", pattern);
        return this;
    }

    SbatchArguments output(String pattern) {
        Objects.requireNonNull(pattern);
        sbatchArgsByName.put("output", pattern);
        return this;
    }

    SbatchArguments partition(String partition) {
        Objects.requireNonNull(partition);
        sbatchArgsByName.put("partition", partition);
        return this;
    }

    SbatchArguments oversubscribe() {
        sbatchOptions.add("oversubscribe");
        return this;
    }

    private SbatchArguments killOnInvalidDep() {
        sbatchArgsByName.put("kill-on-invalid-dep", "yes");
        return this;
    }

    SbatchArguments workDir(Path dir) {
        Objects.requireNonNull(dir);
        sbatchArgsByCharacter.put('D', dir.toAbsolutePath().toString());
        return this;
    }

    SbatchArguments timeout(@Nullable String duration) {
        sbatchArgsByName.put("time", checkTimeout(duration));
        return this;
    }

    private static String checkTimeout(@Nullable String duration) {
        if (duration == null) {
            return "UNLIMITED";
        }
        // TODO check format
        return duration;
    }

    SbatchArguments qos(String qos) {
        Objects.requireNonNull(qos);
        sbatchArgsByName.put("qos", qos);
        return this;
    }

    List<String> toScript() {
        List<String> list = new ArrayList<>();
        sbatchArgsByCharacter.forEach((k, v) -> list.add(PRECEDENT + "-" + k + " " + v));
        sbatchArgsByName.forEach((k, v) -> list.add(PRECEDENT + "--" + k + "=" + v));
        sbatchOptions.forEach(o -> list.add(PRECEDENT + "--" + o));
        return list;
    }

}
