/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.commons.io.WorkingDirectory;
import com.powsybl.computation.CommandExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class contains those job ids relationship in Slurm platform for one task.
 * It has a correspondent working directory and the CompletableFuture as return value.
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlurmTask.class);

    private final WorkingDirectory directory;
    private final List<CommandExecution> executions;
    private final CompletableFuture completableFuture;
    private final TaskCounter counter;

    private Long firstJobId;
    private List<Long> masters;
    private Map<Long, SubTask> subTaskMap;
    private Long currentMaster;

    private Set<Long> tracingIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    SlurmTask(WorkingDirectory directory, List<CommandExecution> executions, CompletableFuture completableFuture) {
        this.directory = Objects.requireNonNull(directory);
        this.executions = Objects.requireNonNull(executions);
        this.completableFuture = Objects.requireNonNull(completableFuture);
        int sum = executions.stream().mapToInt(CommandExecution::getExecutionCount).sum();
        this.counter = new TaskCounter(sum);
    }

    /**
     * The working directory and task is a one-to-one relationship.
     * So it returns the directory name as ID.
     * @return Returns working directory name as ID.
     */
    String getId() {
        return directory.toPath().getFileName().toString();
    }

    Path getWorkingDirPath() {
        return directory.toPath();
    }

    /**
     * Returns the all ids.
     * For array jobs can be cancelled just by calling on master jobId
     * but currently array_job in slurm is not used, so jobs should be cancelled one by one.
     */
    Set<Long> getToCancelIds() {
        Set<Long> set = new HashSet<>();
        set.addAll(masters);
        Set<Long> subIds = getMasters().stream().flatMap(mId -> subTaskMap.get(mId).getBatchStream())
                .collect(Collectors.toSet());
        set.addAll(subIds);
        return set;
    }

    Long getFirstJobId() {
        return firstJobId;
    }

    TaskCounter getCounter() {
        return counter;
    }

    int getCommandCount() {
        return executions.size();
    }

    CommandExecution getCommand(int i) {
        return executions.get(i);
    }

    CompletableFuture getCompletableFuture() {
        return completableFuture;
    }

    boolean contains(Long id) {
        if (firstJobId > id) {
            return false;
        }
        boolean containsInMaster = masters.stream().anyMatch(l -> l.equals(id));
        if (containsInMaster) {
            return true;
        }
        return subTaskMap.values().stream().flatMap(SubTask::getBatchStream).anyMatch(l -> l.equals(id));
    }

    Long getCurrentMaster() {
        return currentMaster;
    }

    /**
     * If first job id is null, this masterId would take account as first job id.
     * @param masterId
     */
    private void newMaster(Long masterId) {
        setFirstJobIfIsNull(masterId);
        currentMaster = masterId;
    }

    /**
     * A CommonUnzipJob is always a master job, but never be a current master.
     * @param masterId
     */
    void newCommonUnzipJob(Long masterId) {
        Objects.requireNonNull(masterId);
        tracingIds.add(masterId);
        LOGGER.debug("tracing common unzip job:{}", masterId);
        setFirstJobIfIsNull(masterId);
        currentMaster = null;
    }

    // TODO rename to dependent jobs
    List<Long> getPreJobIds() {
        if (masters == null) {
            return Collections.emptyList();
        }
        int preMasterIdOffset = currentMaster == null ? 1 : 2;
        if (masters.size() == 1 && preMasterIdOffset == 2) {
            return Collections.emptyList();
        }
        Long preMasterId = masters.get(masters.size() - preMasterIdOffset);
        List<Long> preJobIds = new ArrayList<>();
        preJobIds.add(preMasterId);
        preJobIds.addAll(subTaskMap.get(preMasterId).batchIds);
        return preJobIds;
    }

    private void setFirstJobIfIsNull(Long masterId) {
        Objects.requireNonNull(masterId);
        if (firstJobId == null) {
            initCollections(masterId);
            subTaskMap = new HashMap<>();
        } else {
            Long preMasterId = masters.get(masters.size() - 1);
            LOGGER.debug("DependencyId: {} -> {}", preMasterId, masterId);
        }
        newMasterInCollections(masterId);
    }

    private void initCollections(Long masterId) {
        firstJobId = masterId;
        masters = new ArrayList<>();
        LOGGER.debug("First jobId : {}", firstJobId);
    }

    private void newMasterInCollections(Long masterId) {
        masters.add(masterId);
        subTaskMap.put(masterId, new SubTask(masterId));
    }

    /**
     * The batchId could be a batchId if currentMaster is null.
     * @param batchId
     */
    void newBatch(Long batchId) {
        Objects.requireNonNull(batchId);
        tracingIds.add(batchId);
        LOGGER.debug("tracing job:{}", batchId);
        if (masters == null || currentMaster == null) {
            newMaster(batchId);
        } else {
            subTaskMap.get(currentMaster).add(batchId);
        }
    }

    void setCurrentMasterNull() {
        // make current master to null, and wait to be set
        currentMaster = null;
    }

    // methods used in task store
    boolean untracing(long id) {
        return tracingIds.remove(id);
    }

    Set<Long> getTracingIds() {
        return tracingIds;
    }

    // ===============================
    // ==== for unit test methods ====
    // ===============================
    List<Long> getMasters() {
        return masters;
    }

    List<Long> getBatches(Long masterId) {
        return subTaskMap.get(masterId).batchIds;
    }

    private static final class SubTask {

        private Long masterId;
        private List<Long> batchIds;

        private SubTask(Long masterId) {
            this.masterId = Objects.requireNonNull(masterId);
            batchIds = new ArrayList<>();
        }

        private boolean add(Long batchId) {
            LOGGER.debug("batchIds: {} -> {}", masterId, batchId);
            return batchIds.add(batchId);
        }

        private Stream<Long> getBatchStream() {
            return batchIds.stream();
        }

        @Override
        public String toString() {
            return "SubTask{" +
                    "masterId=" + masterId +
                    ", batchIds=" + batchIds +
                    '}';
        }
    }
}
