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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
class TaskStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskStore.class);

    // workingDir<--->Task of computation
    private Map<String, TaskCounter> workingDirTaskMap = new HashMap<>();
    private Map<String, Long> workingDirFirstJobMap = new HashMap<>();
    private Map<SlurmComputationManager.SlurmCompletableFuture, String> futureWorkingDirMap = new HashMap<>();
    private Map<String, SlurmComputationManager.SlurmCompletableFuture> workingDirFutureMap = new HashMap<>();
    private ReadWriteLock taskLock = new ReentrantReadWriteLock();

    private Map<Long, Long> jobDependencies = new HashMap<>();
    private ReadWriteLock jobDependencyLock = new ReentrantReadWriteLock();

    private Set<Long> tracingIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    TaskCounter getTaskCounter(String workingDir) {
        taskLock.readLock().lock();
        try {
            return workingDirTaskMap.get(workingDir);
        } finally {
            taskLock.readLock().unlock();
        }
    }

    TaskCounter getTaskCounter(CompletableFuture future) {
        taskLock.readLock().lock();
        try {
            String workingDir = futureWorkingDirMap.get(future);
            return workingDirTaskMap.get(workingDir);
        } finally {
            taskLock.readLock().unlock();
        }
    }

    Long getFirstJobId(CompletableFuture future) {
        taskLock.readLock().lock();
        try {
            String workingDir = futureWorkingDirMap.get(future);
            return workingDirFirstJobMap.get(workingDir);
        } finally {
            taskLock.readLock().unlock();
        }
    }

    SlurmComputationManager.SlurmCompletableFuture getCompletableFuture(String workingDirName) {
        taskLock.readLock().lock();
        try {
            return workingDirFutureMap.get(workingDirName);
        } finally {
            taskLock.readLock().unlock();
        }
    }

    List<Long> getDependentJobs(Long jobId) {
        jobDependencyLock.readLock().lock();
        try {
            List<Long> ids = new ArrayList<>();
            Long jobId2 = jobId;
            while ((jobId2 = jobDependencies.get(jobId2)) != null) {
                ids.add(jobId2);
            }
            return ids;
        } finally {
            jobDependencyLock.readLock().unlock();
        }
    }

    void insert(String workingDirName, TaskCounter taskCounter, Long firstJobId) {
        taskLock.writeLock().lock();
        try {
            workingDirTaskMap.put(workingDirName, taskCounter);
            workingDirFirstJobMap.put(workingDirName, firstJobId);
        } finally {
            taskLock.writeLock().unlock();
        }
        trace(firstJobId);
    }

    void insert(String workingDirName, SlurmComputationManager.SlurmCompletableFuture future) {
        taskLock.writeLock().lock();
        try {
            futureWorkingDirMap.put(future, workingDirName);
            workingDirFutureMap.put(workingDirName, future);
        } finally {
            taskLock.writeLock().unlock();
        }
    }

    void insertDependency(Long preJobId, Long jobId) {
        jobDependencyLock.writeLock().lock();
        try {
            jobDependencies.put(preJobId, jobId);
            LOGGER.debug("DependencyId: {} -> {}", preJobId, jobId);
        } finally {
            jobDependencyLock.writeLock().unlock();
        }
        trace(jobId);
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
        return new HashSet<>(workingDirTaskMap.values());
    }

    /**
     * Clear all job ids and its dependency in the task store.
     * Called after complete or cancel all job
     * @param future all job ids belongs to the future
     */
    void clearBy(CompletableFuture future) {
        Long firstJobId = removeTaskMaps(future);
        removeIds(firstJobId);
    }

    private Long removeTaskMaps(CompletableFuture future) {
        String dir;
        Long firstJob;
        taskLock.readLock().lock();
        try {
            dir = futureWorkingDirMap.get(future);
            firstJob = workingDirFirstJobMap.get(dir);
        } finally {
            taskLock.readLock().unlock();
        }
        taskLock.writeLock().lock();
        try {
            workingDirFirstJobMap.remove(dir);
            workingDirTaskMap.remove(dir);
            futureWorkingDirMap.remove(future);
            workingDirFutureMap.remove(dir);
            return firstJob;
        } finally {
            taskLock.writeLock().unlock();
        }
    }

    private Set<Long> removeIds(Long firstId) {
        Set<Long> toRemoveMasterIds = new HashSet<>();
        Set<Long> allIdsFromFirstId = new HashSet<>();
        toRemoveMasterIds.add(firstId);
        jobDependencyLock.writeLock().lock();
        try {
            Long toRemove = firstId;
            while (toRemove != null) {
                toRemoveMasterIds.add(toRemove);
                toRemove = jobDependencies.remove(toRemove);
            }
            allIdsFromFirstId.addAll(toRemoveMasterIds);
            return allIdsFromFirstId;
        } finally {
            jobDependencyLock.writeLock().unlock();
        }
    }

    Optional<SlurmComputationManager.SlurmCompletableFuture> getCompletableFutureByJobId(long id) {
        // try with first id
        Optional<SlurmComputationManager.SlurmCompletableFuture> completableFuture = getFutureByFirstId(id);
        if (completableFuture.isPresent()) {
            return completableFuture;
        }
        // try with master id
        completableFuture = getFutureByMasterId(id);
        if (completableFuture.isPresent()) {
            return completableFuture;
        }
        // TODO get future by array job
        // try with batch id
//        completableFuture = getFutureByBatchId(id);
        return completableFuture;
    }

    private Optional<SlurmComputationManager.SlurmCompletableFuture> getFutureByFirstId(long firstJobId) {
        taskLock.readLock().lock();
        try {
            return workingDirFirstJobMap.entrySet()
                    .stream()
                    .filter(e -> e.getValue() == firstJobId)
                    .findFirst()
                    .map(Map.Entry::getKey)
                    .map(workingDirFutureMap::get);
        } finally {
            taskLock.readLock().unlock();
        }
    }

    private Optional<SlurmComputationManager.SlurmCompletableFuture> getFutureByMasterId(long masterId) {
        OptionalLong option = getFirstId(masterId);
        if (option.isPresent()) {
            return getFutureByFirstId(option.getAsLong());
        } else {
            return Optional.empty();
        }
    }

    private OptionalLong getFirstId(long masterId) {
        // is already a first job id
        Optional<SlurmComputationManager.SlurmCompletableFuture> completableFuture = getFutureByFirstId(masterId);
        if (completableFuture.isPresent()) {
            return OptionalLong.of(masterId);
        }
        Map<Long, Long> inverted;
        jobDependencyLock.readLock().lock();
        try {
            // check is master id
            if (jobDependencies.values().contains(masterId)) {
                inverted = jobDependencies.entrySet().stream()
                        .filter(entry -> entry.getKey() < masterId) // pruned
                        .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey)); // inverted
            } else {
                return OptionalLong.empty();
            }
        } finally {
            jobDependencyLock.readLock().unlock();
        }

        Long tmp;
        Long firstJob = masterId;
        do {
            tmp = firstJob;
            firstJob = inverted.get(tmp);
        } while (firstJob != null);

        return OptionalLong.of(tmp);
    }

}
