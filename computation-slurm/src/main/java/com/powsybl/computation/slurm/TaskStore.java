/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * @author Yichen Tang {@literal <yichen.tang at rte-france.com>}
 */
class TaskStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskStore.class);

    private Queue<SlurmTask> tasks = new ConcurrentLinkedQueue<>();

    void add(SlurmTask task) {
        tasks.add(task);
    }

    List<SlurmTask> getTasks() {
        return ImmutableList.copyOf(tasks);
    }

    List<MonitoredJob> getPendingJobs() {
        return getTasks().stream()
                .flatMap(t -> t.getPendingJobs().stream())
                .collect(Collectors.toList());
    }

    void remove(SlurmTask task) {
        tasks.remove(task);
    }

    void interruptAll() {
        LOGGER.info("Interrupting all tasks...");
        tasks.forEach(SlurmTask::interrupt);
    }

    // ========================
    // === integration test ===
    // ========================
    boolean isEmpty() {
        System.out.println("taskByDir empty:" + tasks.isEmpty());
        return tasks.isEmpty();
    }
}
