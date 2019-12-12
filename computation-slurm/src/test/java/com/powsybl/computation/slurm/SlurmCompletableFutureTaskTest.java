/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import org.junit.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmCompletableFutureTaskTest {

    @Test
    public void test() {
        TaskStore ts = mock(TaskStore.class);
        UUID uuid = UUID.randomUUID();
        SlurmCompletableFutureTask<String> futureTask = new SlurmCompletableFutureTask<>(SlurmCompletableFutureTaskTest::foo, uuid, ts);
        futureTask.cancel(true);
        verify(ts, times(1)).cancelCallableAndCleanup(uuid);

        SlurmCompletableFutureTask<String> futureTask2 = new SlurmCompletableFutureTask<>(SlurmCompletableFutureTaskTest::foo, uuid, ts);
        futureTask2.cancel(false);
        verifyNoMoreInteractions(ts);
    }

    private static String foo() {
        return "foo";
    }
}
