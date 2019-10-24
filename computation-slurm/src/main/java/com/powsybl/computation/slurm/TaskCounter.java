/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import java.util.concurrent.CountDownLatch;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
class TaskCounter {
    private volatile CountDownLatch latch;

    TaskCounter(int totalJobs) {
        latch = new CountDownLatch(totalJobs);
    }

    void await() throws InterruptedException {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new InterruptedException("cancelled during await");
        }
    }

    void countDown() {
        latch.countDown();
    }

    void cancel() {
        while (latch.getCount() > 0) {
            latch.countDown();
        }
    }
}
