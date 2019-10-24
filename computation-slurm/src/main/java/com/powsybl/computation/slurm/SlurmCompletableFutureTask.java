/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.computation.CompletableFutureTask;

import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmCompletableFutureTask<R> extends CompletableFutureTask<R> {

    private final TaskStore taskStore;
    private final UUID uuid;

    SlurmCompletableFutureTask(Callable<R> task, UUID callableID, TaskStore taskStore) {
        super(task);
        this.taskStore = taskStore;
        this.uuid = callableID;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (mayInterruptIfRunning) {
            taskStore.cancelCallable(uuid);
        }
        return super.cancel(mayInterruptIfRunning);
    }
}
