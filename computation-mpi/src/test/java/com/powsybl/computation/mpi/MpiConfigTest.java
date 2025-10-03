/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.mpi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.nio.file.Path;

/**
 * @author Matthieu SAUR {@literal <matthieu.saur at rte-france.com>}
 */
class MpiConfigTest {

    @Test
    void testMpiConfig() {
        MpiConfig config = new MpiConfig();
        assertFalse(config.isVerbose());
        assertEquals(1, config.getCoresPerRank());
        assertEquals(60, config.getTimeout());
        assertNull(config.getLocalDir());
        assertNull(config.getStatisticsDbDir());
        assertNull(config.getStdOutArchive());
        assertNull(config.getStatisticsDbName());
    }

    @Test
    void testMpiConfigSet() {
        MpiConfig config = new MpiConfig();
        assertTrue(config.setVerbose(true).isVerbose());
        assertEquals(1000, config.setCoresPerRank(1000).getCoresPerRank());
        assertEquals(getPathWithOSSeparator("local"), config.setLocalDir(Path.of(getPathWithOSSeparator("local"))).getLocalDir().toString());
        assertEquals(getPathWithOSSeparator("dbdir"), config.setStatisticsDbDir(Path.of(getPathWithOSSeparator("dbdir"))).getStatisticsDbDir().toString());
        assertEquals(getPathWithOSSeparator("archive"), config.setStdOutArchive(Path.of(getPathWithOSSeparator("archive"))).getStdOutArchive().toString());
        assertEquals("StatsDBNAME", config.setStatisticsDbName("StatsDBNAME").getStatisticsDbName());
    }

    private static String getPathWithOSSeparator(String dirName) {
        return String.join(File.separator, "tmp", dirName, "dummy");
    }

    @ParameterizedTest
    @CsvSource({"-2", "-5", "0"})
    void testMpiConfigTimeoutThrow(int timeout) {
        MpiConfig config = new MpiConfig();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> config.setTimeout(timeout));
        assertEquals("invalid timeout value " + timeout, e.getMessage());
    }

    @ParameterizedTest
    @CsvSource({"-1", "1", "2"})
    void testMpiConfigTimeoutOk(int timeout) {
        MpiConfig config = new MpiConfig();
        config.setTimeout(timeout);
        assertEquals(timeout, config.getTimeout());
    }
}
