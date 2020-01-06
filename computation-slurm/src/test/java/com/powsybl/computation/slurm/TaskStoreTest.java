/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class TaskStoreTest {

    @Test
    public void test() throws InterruptedException {
        TaskStore store = new TaskStore(1);
        CompletableFuture<String> cf = new CompletableFuture<>();
        SlurmTask task = SlurmTaskTest.mockSubmittedTask(cf);
        store.add(task);
        assertSame(task, store.getTask(cf).orElseThrow(RuntimeException::new));
        assertSame(cf, store.getCompletableFuture(task.getId()).orElseThrow(RuntimeException::new));
        assertFalse(store.isEmpty());
        cf.complete("done");
        TimeUnit.SECONDS.sleep(2);
        assertTrue(store.isEmpty());
    }
}
