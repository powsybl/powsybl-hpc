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
    private Map<String, SlurmException> workingDirException = new HashMap<>();
    private ReadWriteLock taskLock = new ReentrantReadWriteLock();

    private Map<Long, Long> jobDependencies = new HashMap<>();
    private ReadWriteLock jobDependencyLock = new ReentrantReadWriteLock();

    private Map<Long, List<Long>> batchIds = new HashMap<>();
    private ReadWriteLock batchIdsLock = new ReentrantReadWriteLock();

    private Set<Long> tracingIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Set<UUID> cancellingCallable = Collections.newSetFromMap(new ConcurrentHashMap<>());

    Optional<TaskCounter> getTaskCounter(String workingDir) {
        taskLock.readLock().lock();
        try {
            return Optional.ofNullable(workingDirTaskMap.get(workingDir));
        } finally {
            taskLock.readLock().unlock();
        }
    }

    OptionalLong getFirstJobId(String workingDir) {
        taskLock.readLock().lock();
        try {
            Long firstJobId = workingDirFirstJobMap.get(workingDir);
            return firstJobId == null ? OptionalLong.empty() : OptionalLong.of(firstJobId);
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

    void insertBatchIds(Long masterJobId, Long jobId) {
        if (!masterJobId.equals(jobId)) {
            batchIdsLock.writeLock().lock();
            try {
                batchIds.computeIfAbsent(masterJobId, k -> new ArrayList<>()).add(jobId);
                LOGGER.debug("batchIds: {} -> {}", masterJobId, jobId);
            } finally {
                batchIdsLock.writeLock().unlock();
            }
            trace(jobId);
        }
    }

    List<Long> getBatchIds(Long masterJobId) {
        batchIdsLock.readLock().lock();
        try {
            List<Long> longs = batchIds.get(masterJobId);
            return longs == null ? Collections.emptyList() : longs;
        } finally {
            batchIdsLock.readLock().unlock();
        }
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

    void cleanTaskMaps(String dirName) {
        taskLock.writeLock().lock();
        try {
            workingDirFirstJobMap.remove(dirName);
            workingDirTaskMap.remove(dirName);
            workingDirException.remove(dirName);
        } finally {
            taskLock.writeLock().unlock();
        }
    }

    Set<Long> cleanIdMaps(Long firstId) {
        Set<Long> toRemoveMasterIds = new HashSet<>();
        Set<Long> allIdsFromFirstId = new HashSet<>();
        toRemoveMasterIds.add(firstId);
        jobDependencyLock.writeLock().lock();
        try {
            Long toRemove = firstId;
            while (toRemove != null) {
                // common unzip batch is not untraced by "mydone_"
                untracing(toRemove);
                toRemoveMasterIds.add(toRemove);
                toRemove = jobDependencies.remove(toRemove);
            }
        } finally {
            jobDependencyLock.writeLock().unlock();
        }
        allIdsFromFirstId.addAll(toRemoveMasterIds);
        batchIdsLock.writeLock().lock();
        try {
            toRemoveMasterIds.forEach(masterId -> {
                List<Long> remove = batchIds.remove(masterId);
                if (remove != null && !remove.isEmpty()) {
                    allIdsFromFirstId.addAll(remove);
                }
            });
            return allIdsFromFirstId;
        } finally {
            batchIdsLock.writeLock().unlock();
        }
    }

    Optional<String> getDirNameByJobId(long id) {
        // try with first id
        Optional<String> optDirName = getDirNameByFirstId(id);
        if (optDirName.isPresent()) {
            return optDirName;
        }
        // try with master id
        optDirName = getDirNameByMasterId(id);
        if (optDirName.isPresent()) {
            return optDirName;
        }
        // try with batch id
        optDirName = getDirNameByBatchId(id);
        return optDirName;
    }

    private Optional<String> getDirNameByFirstId(long firstJobId) {
        taskLock.readLock().lock();
        try {
            return workingDirFirstJobMap.entrySet()
                    .stream()
                    .filter(e -> e.getValue() == firstJobId)
                    .findFirst()
                    .map(Map.Entry::getKey);
        } finally {
            taskLock.readLock().unlock();
        }
    }

    private Optional<String> getDirNameByMasterId(long masterId) {
        OptionalLong option = getFirstId(masterId);
        if (option.isPresent()) {
            return getDirNameByFirstId(option.getAsLong());
        } else {
            return Optional.empty();
        }
    }

    private OptionalLong getFirstId(long masterId) {
        // is already a first job id
        Optional<String> optDirName = getDirNameByFirstId(masterId);
        if (optDirName.isPresent()) {
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

    private Optional<String> getDirNameByBatchId(long batchId) {
        OptionalLong optMasterId = getMasterId(batchId);
        if (optMasterId.isPresent()) {
            return getDirNameByMasterId(optMasterId.getAsLong());
        }
        return Optional.empty();
    }

    private OptionalLong getMasterId(long batchId) {
        Optional<Map.Entry<Long, List<Long>>> max;
        batchIdsLock.readLock().lock();
        try {
            max = batchIds.entrySet().stream()
                    .filter(entry -> entry.getKey() < batchId)
                    .max(Comparator.comparingLong(Map.Entry::getKey));
        } finally {
            batchIdsLock.readLock().unlock();
        }

        if (max.isPresent() && (max.get().getValue().contains(batchId))) {
            return OptionalLong.of(max.get().getKey());
        }
        return OptionalLong.empty();
    }

    void insertException(String workingDirName, SlurmException e) {
        taskLock.writeLock().lock();
        try {
            workingDirException.put(workingDirName, e);
        } finally {
            taskLock.writeLock().unlock();
        }
    }

    Optional<SlurmException> getExceptionAndClean(String workingDirName) {
        Optional<SlurmException> exception = Optional.ofNullable(workingDirException.get(workingDirName));
        if (exception.isPresent()) {
            workingDirException.remove(workingDirName);
        }
        return exception;
    }

    void cancelCallable(UUID callableId) {
        LOGGER.debug("cancelCallable added");
        cancellingCallable.add(callableId);
    }

    boolean isCancelledThenClean(UUID callableId) {
        boolean contains = cancellingCallable.contains(callableId);
        if (contains) {
            cancellingCallable.remove(callableId);
        }
        LOGGER.debug("cancelCallable checked:" + contains);
        return contains;
    }

    boolean isCleaned() {
        return debug(workingDirException, "workingDirException")
                && debug(workingDirFirstJobMap, "workingDirFirstJobMap")
                && debug(workingDirTaskMap, "workingDirTaskMap")
                && debug(jobDependencies, "jobDependencies")
                && debug(batchIds, "batchIds")
                && debug(tracingIds, "tracingIds")
                && debug(cancellingCallable, "cancellingCallable");
    }

    private boolean debug(Collection collection, String name) {
        if (collection.isEmpty()) {
            return true;
        }
        System.out.println(name + " is not empty");
        System.out.println(collection);
        return false;
    }

    private boolean debug(Map map, String name) {
        if (map.isEmpty()) {
            return true;
        }
        System.out.println(name + " is not empty");
        System.out.println(map);
        return false;
    }
}
