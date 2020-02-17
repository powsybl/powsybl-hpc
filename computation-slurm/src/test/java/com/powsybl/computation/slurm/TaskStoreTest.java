/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public class TaskStoreTest {

    @Test
    public void test() {
        TaskStore store = new TaskStore();
        SlurmTask task = mock(SlurmTask.class);
        when(task.getId()).thenReturn("workingDir_1234");
        store.add(task);
        assertSame(task, store.getTask("workingDir_1234").orElseThrow(RuntimeException::new));
        assertFalse(store.isEmpty());
        // store is cleanup in SlurmComputationManager
    }
}
