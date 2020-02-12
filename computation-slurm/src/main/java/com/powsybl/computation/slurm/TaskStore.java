/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
class TaskStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskStore.class);

    private Map<String, SlurmTask> taskByDir = new ConcurrentHashMap<>();
    private Map<CompletableFuture, SlurmTask> taskByFuture = new ConcurrentHashMap<>();

    private final StoreCleaner cleaner;

    TaskStore(int cleanTime) {
        cleaner = new StoreCleaner(cleanTime);
        cleaner.start();
    }

    void add(SlurmTask task) {
        taskByDir.put(task.getId(), task);
        taskByFuture.put(task.getCompletableFuture(), task);
    }

    public Map<String, SlurmTask> getTaskByDir() {
        return taskByDir;
    }

    private Optional<SlurmTask> getTask(String workingDir) {
        return Optional.ofNullable(taskByDir.get(workingDir));
    }

    Optional<SlurmTask> getTask(CompletableFuture future) {
        return Optional.ofNullable(taskByFuture.get(future));
    }

    Optional<TaskCounter> getTaskCounter(String workingDir) {
        return getTask(workingDir).map(SlurmTask::getCounter);
    }

    Optional<TaskCounter> getTaskCounter(CompletableFuture future) {
        return getTask(future).map(SlurmTask::getCounter);
    }

    // TODO use task
    Optional<CompletableFuture> getCompletableFuture(String workingDirName) {
        return getTask(workingDirName).map(SlurmTask::getCompletableFuture);
    }

    boolean untracing(long id) {
        Collection<SlurmTask> tasks = taskByDir.values();
        for (SlurmTask t : tasks) {
            boolean untracing = t.untracing(id);
            if (untracing) {
                LOGGER.debug("Untracing: {}", id);
                return true;
            }
        }
        return false;
    }

    Set<Long> getTracingIds() {
        Set<Long> tracingIds = new HashSet<>();
        taskByDir.values().stream()
                .map(SlurmTask::getTracingIds)
                .forEach(tracingIds::addAll);
        return tracingIds;
    }

    Set<TaskCounter> getTaskCounters() {
        return taskByDir.values().stream()
                .map(SlurmTask::getCounter).collect(Collectors.toSet());
    }

    Optional<CompletableFuture> getCompletableFutureByJobId(long id) {
        return taskByDir.values().stream().filter(task -> task.contains(id))
                .findFirst().map(SlurmTask::getCompletableFuture);
    }

    boolean isEmpty() {
        return taskByDir.isEmpty()
                && taskByFuture.isEmpty();
    }

    class StoreCleaner {

        private final int cleanTime;
        ScheduledExecutorService scheduledExecutorService;

        StoreCleaner(int cleanTime) {
            this.cleanTime = cleanTime;
            scheduledExecutorService = Executors.newScheduledThreadPool(1);
        }

        void start() {
            scheduledExecutorService.scheduleAtFixedRate(this::clean, cleanTime, cleanTime, TimeUnit.SECONDS);
        }

        void clean() {
            taskByDir.entrySet().stream().filter(e ->
                    e.getValue().getCompletableFuture().isDone())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet())
                    .forEach(id -> taskByDir.remove(id));
            taskByFuture.keySet().stream()
                    .filter(CompletableFuture::isDone)
                    .collect(Collectors.toSet())
                    .forEach(f -> taskByFuture.remove(f));
        }
    }
}
