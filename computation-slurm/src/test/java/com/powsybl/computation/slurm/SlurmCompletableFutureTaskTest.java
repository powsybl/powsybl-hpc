/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.Callable;

import static org.mockito.Mockito.*;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmCompletableFutureTaskTest {

    @Test
    public void test() {
        TaskStore ts = mock(TaskStore.class);
        Callable callable = mock(Callable.class);
        UUID id = UUID.randomUUID();
        SlurmCompletableFutureTask sut = new SlurmCompletableFutureTask(callable, id, ts);
        sut.cancel(true);
        verify(ts, times(1)).cancelCallable(id);
        sut.cancel(false);
        verify(ts, times(1)).cancelCallable(id);
    }
}
