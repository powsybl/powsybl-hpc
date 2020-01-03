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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
class TaskStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskStore.class);

    private Map<String, SlurmTask> taskByDir = new ConcurrentHashMap<>();
    private Map<CompletableFuture, SlurmTask> taskByFuture = new ConcurrentHashMap<>();

    private Set<Long> tracingIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

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

    /**
     * Get a set of submitting or submitted but not finished task's first job id.
     * @return
     */
    Set<Long> getTracingFirstIds() {
        return taskByDir.values().stream()
                .flatMap(task -> task.getToCancelIds().stream()).collect(Collectors.toSet());
    }

    // TODO use task
    Optional<CompletableFuture> getCompletableFuture(String workingDirName) {
        return getTask(workingDirName).map(SlurmTask::getCompletableFuture);
    }

    private void trace(long id) {
        LOGGER.debug("tracing {}", id);
        tracingIds.add(id);
    }

    boolean untracing(long id) {
        return tracingIds.remove(id);
    }

    Set<Long> getTracingIds() {
        return new HashSet<>(tracingIds);
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
