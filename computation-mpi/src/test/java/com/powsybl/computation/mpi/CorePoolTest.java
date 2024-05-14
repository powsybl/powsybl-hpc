/**
 * Copyright (c) 2016, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.mpi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class CorePoolTest {

    public CorePoolTest() {
    }

    private CorePool pool;

    @BeforeEach
    public void setUp() {
        pool = new CorePool();
        MpiRank rank0 = new MpiRank(0);
        MpiRank rank1 = new MpiRank(1);
        pool.returnCore(new Core(rank0, 0));
        pool.returnCore(new Core(rank0, 1));
        pool.returnCore(new Core(rank1, 0));
        pool.returnCore(new Core(rank1, 1));
    }

    @AfterEach
    public void tearDown() {
        pool = null;
    }

    @Test
    void testBorrowReturn() {
        assertEquals(4, pool.availableCores());
        List<Core> cores = pool.borrowCores(1);
        assertEquals(1, cores.size());
        assertEquals(3, pool.availableCores());
        pool.returnCore(cores.get(0));
        assertEquals(4, pool.availableCores());
    }

    @Test
    void testBorrowAll() {
        assertEquals(4, pool.availableCores());
        List<Core> allCores = pool.borrowCores(4);
        assertEquals(4, allCores.size());
        assertEquals(0, pool.availableCores());
        pool.returnCores(allCores);
        assertEquals(4, pool.availableCores());
    }

    @Test
    void testBorrowMoreThanSizePool() {
        assertEquals(4, pool.availableCores());
        try {
            pool.borrowCores(5);
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    void testPreferedCores() {
        assertEquals(4, pool.availableCores());
        List<Core> cores = pool.borrowCores(1, Collections.singleton(0));
        assertEquals(1, cores.size());
        assertEquals(3, pool.availableCores());
        pool.returnCore(cores.get(0));
        assertEquals(4, pool.availableCores());
    }

    @Test
    void testPreferedCoresMixed() {
        assertEquals(4, pool.availableCores());
        List<Core> cores = pool.borrowCores(3, Collections.singleton(0));
        assertEquals(3, cores.size());
        assertEquals(0, cores.get(0).rank.num);
        assertEquals(0, cores.get(1).rank.num);
        assertEquals(1, cores.get(2).rank.num);
        assertEquals(1, pool.availableCores());
        pool.returnCores(cores);
        assertEquals(4, pool.availableCores());
    }
}
