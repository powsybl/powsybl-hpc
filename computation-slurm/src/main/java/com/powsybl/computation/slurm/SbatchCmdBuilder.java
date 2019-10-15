/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds an {@link SbatchCmd}, used to submit script execution to Slurm.
 * @see <a href="https://slurm.schedmd.com/sbatch.html">Sbatch</a>
 *
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
class SbatchCmdBuilder {

    private static final String DATETIME_FORMATTER = "`date -d \"%d seconds\" \"+%%Y-%%m-%%dT%%H:%%M:%%S\"`";

    private final Map<String, String> sbatchArgsByName = new HashMap<>();
    private final Map<Character, String> sbatchArgsByCharacter = new HashMap<>();
    private final TreeSet<String> sbatchOptions = new TreeSet<>();
    private String script;

    SbatchCmdBuilder jobName(String jobName) {
        sbatchArgsByName.put("job-name", jobName);
        return this;
    }

    SbatchCmdBuilder array(int i) {
        if (i < 0) {
            throw new IllegalArgumentException(i + " is not validate for array.");
        }
        if (i == 1) {
            sbatchArgsByName.put("array", Integer.toString(0));
        }
        if (i != 1) {
            sbatchArgsByName.put("array", "0-" + (i - 1));
        }
        return this;
    }

    SbatchCmdBuilder aftercorr(List<Long> jobIds) {
        Objects.requireNonNull(jobIds);
        if (!jobIds.isEmpty()) {
            String coll = jobIds.stream().map(Object::toString).collect(Collectors.joining(":", "aftercorr:", ""));
            sbatchArgsByName.put("dependency", coll);
        }
        return this;
    }

    SbatchCmdBuilder nodes(int i) {
        if (i < 1) {
            throw new IllegalArgumentException(i + " is not validate for nodes.");
        }
        sbatchArgsByName.put("nodes", Integer.toString(i));
        return this;
    }

    SbatchCmdBuilder ntasks(int i) {
        if (i < 1) {
            throw new IllegalArgumentException(i + " is not validate for ntasks.");
        }
        sbatchArgsByName.put("ntasks", Integer.toString(i));
        return this;
    }

    SbatchCmdBuilder tasksPerNode(int i) {
        if (i < 1) {
            throw new IllegalArgumentException(i + " is not validate for tasksPerNode.");
        }
        sbatchArgsByName.put("ntasks-per-node", Integer.toString(i));
        return this;
    }

    SbatchCmdBuilder cpusPerTask(int i) {
        sbatchArgsByName.put("cpus-per-task", Integer.toString(i));
        return this;
    }

    SbatchCmdBuilder error(String pattern) {
        Objects.requireNonNull(pattern);
        sbatchArgsByName.put("error", pattern);
        return this;
    }

    SbatchCmdBuilder output(String pattern) {
        Objects.requireNonNull(pattern);
        sbatchArgsByName.put("output", pattern);
        return this;
    }

    SbatchCmdBuilder partition(String partition) {
        Objects.requireNonNull(partition);
        sbatchArgsByName.put("partition", partition);
        return this;
    }

    SbatchCmdBuilder oversubscribe() {
        sbatchOptions.add("oversubscribe");
        return this;
    }

    SbatchCmdBuilder script(String name) {
        this.script = Objects.requireNonNull(name);
        return this;
    }

    SbatchCmdBuilder workDir(Path dir) {
        Objects.requireNonNull(dir);
        sbatchArgsByCharacter.put('D', dir.toAbsolutePath().toString());
        return this;
    }

    SbatchCmdBuilder timeout(@Nullable String duration) {
        sbatchArgsByName.put("time", checkTimeout(duration));
        return this;
    }

    SbatchCmdBuilder timeout(long seconds) {
        return timeout(SlurmUtils.toTime(seconds));
    }

    SbatchCmdBuilder deadline(long seconds) {
        Preconditions.checkArgument(seconds > 0, "Invalid seconds({}) for deadline: must be 1 or greater", seconds);
        sbatchArgsByName.put("deadline", String.format(DATETIME_FORMATTER, seconds));
        return this;
    }

    SbatchCmdBuilder priority(int priority) {
        Preconditions.checkArgument(priority >= 0, "Priority must be >= 0");
        sbatchArgsByName.put("priority", Integer.toString(priority));
        return this;
    }

    SbatchCmdBuilder nice(int nice) {
        Preconditions.checkArgument(nice >= 0, "Invalid --nice value");
        sbatchArgsByName.put("nice", Integer.toString(nice));
        return this;
    }

    private static String checkTimeout(@Nullable String duration) {
        if (duration == null) {
            return "UNLIMITED";
        }
        // TODO check format
        return duration;
    }

    SbatchCmdBuilder qos(String qos) {
        Objects.requireNonNull(qos);
        sbatchArgsByName.put("qos", qos);
        return this;
    }

    SbatchCmd build() {
        validate();
        return new SbatchCmd(sbatchArgsByName, sbatchArgsByCharacter, sbatchOptions, script);
    }

    private void validate() {
        if (null == script) {
            throw new SlurmException("Script is null in cmd");
        }
    }
}
