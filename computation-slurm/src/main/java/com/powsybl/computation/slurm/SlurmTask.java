/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.commons.io.WorkingDirectory;
import com.powsybl.computation.Command;
import com.powsybl.computation.CommandExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Represents a submitted task.
 * It has a correspondent working directory and the CompletableFuture as return value.
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlurmTask.class);

    WorkingDirectory directory;
    List<CommandExecution> executions;
    CompletableFuture completableFuture;
    TaskCounter counter;

    Long firstJobId;
    List<Long> masters;
    Map<Long, SubTask> subTaskMap;
    Long currentMaster;

    SlurmTask(WorkingDirectory directory, List<CommandExecution> executions, CompletableFuture completableFuture) {
        this.directory = Objects.requireNonNull(directory);
        this.executions = Objects.requireNonNull(executions);
        this.completableFuture = Objects.requireNonNull(completableFuture);
        int sum = executions.stream().mapToInt(CommandExecution::getExecutionCount).sum();
        this.counter = new TaskCounter(sum);
    }

    /**
     * Returns the first job id and it's batchIds
     * For array jobs can be cancelled just by calling on master jobId
     * but currently array_job in slurm is not used, so jobs should be cancelled one by one.
     * As "kill-on-invalid-dep" is used by default, all following job could be cancelled automatically
     * But except for batch job binding to the first job.
     */
    Set<Long> getToCancelIds() {
        Set<Long> set = new HashSet<>();
        set.add(firstJobId);
        set.addAll(getBatchesWithFirst());
        return set;
    }

    Set<Long> getBatchesWithFirst() {
        return new HashSet<>(subTaskMap.get(firstJobId).batchIds);
    }

    Long getFirstJobId() {
        return firstJobId;
    }

    WorkingDirectory getDirectory() {
        return directory;
    }

    TaskCounter getCounter() {
        return counter;
    }

    List<CommandExecution> getExecutions() {
        return executions;
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

    /**
     * If first job id is null, this masterId would take account as first job id.
     * @param masterId
     */
    void newMaster(Long masterId) {
        setFirstJobIfIsNull(masterId);
        currentMaster = masterId;
    }

    /**
     * A CommonUnzipJob is always a master job, but never be a current master.
     * @param masterId
     */
    void newCommonUnzipJob(Long masterId) {
        setFirstJobIfIsNull(masterId);
        currentMaster = null;
    }

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

    class SubTask {
        Long masterId;
        List<Long> batchIds;

        SubTask(Long masterId) {
            this.masterId = Objects.requireNonNull(masterId);
            batchIds = new ArrayList<>();
        }

        boolean add(Long batchId) {
            LOGGER.debug("batchIds: {} -> {}", masterId, batchId);
            return batchIds.add(batchId);
        }

        Stream<Long> getBatchStream() {
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
