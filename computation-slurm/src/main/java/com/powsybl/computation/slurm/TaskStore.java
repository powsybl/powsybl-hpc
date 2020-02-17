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
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
class TaskStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskStore.class);

    // TODO why two map? use UUID instead of dir str
    private Map<String, SlurmTask> taskByDir = new ConcurrentHashMap<>();
    private Map<UUID, SlurmException> exceptionMap = new ConcurrentHashMap<>();

    void add(SlurmTask task) {
        taskByDir.put(task.getId(), task);
    }

    Optional<SlurmTask> getTask(String workingDir) {
        return Optional.ofNullable(taskByDir.get(workingDir));
    }

    void cancelCallableAndCleanup(UUID callableId) {
        LOGGER.debug("Cancel and clean callableId:" + callableId);
        getTaskByCallableId(callableId).ifPresent(SlurmTask::cancel);
        getTaskByCallableId(callableId).ifPresent(task -> taskByDir.remove(task.getId()));
    }

    boolean finallyCleanCallable(UUID callableId) {
        LOGGER.debug("Clean " + callableId);
        getTaskByCallableId(callableId).ifPresent(task -> taskByDir.remove(task.getId()));
        return true;
    }

    private Optional<SlurmTask> getTaskByCallableId(UUID callableId) {
        return taskByDir.values().stream().filter(task -> task.getCallableId().equals(callableId))
                .findFirst();
    }

    boolean isCancelled(UUID callableId) {
        Boolean isCancelled = getTaskByCallableId(callableId).map(SlurmTask::isCancel).orElseThrow(() -> new RuntimeException("Fail to call isCancel() on " + callableId));
        LOGGER.debug("Callable '{}' cancelled: {}", callableId, isCancelled);
        if (isCancelled) {
            cancelCallableAndCleanup(callableId);
        }
        return isCancelled;
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

    void cancelCallable(long id, SlurmException slurmException) {
        taskByDir.values().stream().filter(task -> task.contains(id))
                .findFirst()
                .ifPresent(task -> {
                    UUID callableId = task.getCallableId();
                    LOGGER.debug("A SlurmException {} found on Callable '{}'", slurmException, callableId);
                    exceptionMap.put(callableId, slurmException);
                    cancelCallableAndCleanup(callableId);
                    task.getCounter().cancel();
                });
    }

    Optional<SlurmException> getException(UUID callableId) {
        SlurmException exception = exceptionMap.get(callableId);
        if (exception != null) {
            exceptionMap.remove(callableId);
            return Optional.of(exception);
        }
        return Optional.empty();
    }

    void cancelAll() {
        LOGGER.info("Cancelling all tasks...");
        taskByDir.values().forEach(SlurmTask::cancel);
    }

    // ========================
    // === integration test ===
    // ========================
    Map<String, SlurmTask> getTaskByDir() {
        return taskByDir;
    }

    boolean isEmpty() {
        System.out.println("taskByDir empty:" + taskByDir.isEmpty());
        System.out.println("exceptionMap empty:" + exceptionMap.isEmpty());
        return taskByDir.isEmpty()
                && exceptionMap.isEmpty();
    }
}
