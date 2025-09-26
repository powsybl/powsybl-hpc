/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.mpi;

import com.powsybl.computation.ComputationManager;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.nio.file.FileSystems;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Matthieu SAUR {@literal <matthieu.saur at rte-france.com>}
 */
class MpiToolUtilTest {

    @Mock
    private MpiComputationManager mpiComputationManager;

    private static final String TMP_DIR = "tmp-dir";
    private static final String STATISTICS_DB_DIR = "statistics-db-dir";
    private static final String STATISTICS_DB_NAME = "statistics-db-name";
    private static final String CORES = "cores";
    private static final String VERBOSE = "verbose";
    private static final String STDOUT_ARCHIVE = "stdout-archive";
    public static final String DIR = "dir";

    @Test
    void testCreateMpiOptions() {
        // WHEN
        Options options = MpiToolUtil.createMpiOptions();
        // THEN
        assertNotNull(options);
        assertEquals(6, options.getOptions().size());
        // THEN TMP_DIR
        String optionName = TMP_DIR;
        assertTrue(options.getOption(optionName).hasArg());
        assertFalse(options.getOption(optionName).isRequired());
        assertEquals(DIR, options.getOption(optionName).getArgName());
        assertEquals("local temporary directory", options.getOption(optionName).getDescription());
        // THEN STATISTICS_DB_DIR
        optionName = STATISTICS_DB_DIR;
        assertTrue(options.getOption(optionName).hasArg());
        assertFalse(options.getOption(optionName).isRequired());
        assertEquals(DIR, options.getOption(optionName).getArgName());
        assertEquals("statistics db directory", options.getOption(optionName).getDescription());
        // THEN STATISTICS_DB_NAME
        optionName = STATISTICS_DB_NAME;
        assertTrue(options.getOption(optionName).hasArg());
        assertFalse(options.getOption(optionName).isRequired());
        assertEquals("name", options.getOption(optionName).getArgName());
        assertEquals("statistics db name", options.getOption(optionName).getDescription());
        // THEN CORES
        optionName = CORES;
        assertTrue(options.getOption(optionName).hasArg());
        assertTrue(options.getOption(optionName).isRequired());
        assertEquals("n", options.getOption(optionName).getArgName());
        assertEquals("number of cores per rank", options.getOption(optionName).getDescription());
        // THEN VERBOSE
        optionName = VERBOSE;
        assertFalse(options.getOption(optionName).hasArg());
        assertFalse(options.getOption(optionName).isRequired());
        assertEquals("verbose mode", options.getOption(optionName).getDescription());
        // THEN STDOUT_ARCHIVE
        optionName = STDOUT_ARCHIVE;
        assertTrue(options.getOption(optionName).hasArg());
        assertFalse(options.getOption(optionName).isRequired());
        assertEquals("file", options.getOption(optionName).getArgName());
        assertEquals("tasks standard output archive", options.getOption(optionName).getDescription());
    }

    @Test
    void testCreateMpiComputationManager() throws ParseException {
        // GIVEN
        Options options = MpiToolUtil.createMpiOptions();
        CommandLineParser commandLineParser = new DefaultParser();
        String localDir = "/tmp";
        String statDbDir = "/statDbDir";
        String archiveDir = "/archiveDir";
        int cores = 2;
        int timeout = 60;
        boolean isVerbose = false;
        String[] commandWithOptions = {
            addOption(CORES), String.valueOf(cores),
            addOption(TMP_DIR), localDir,
            addOption(STATISTICS_DB_DIR), statDbDir,
            addOption(STDOUT_ARCHIVE), archiveDir,
        };
        CommandLine commandLine = commandLineParser.parse(options, commandWithOptions);
        try (MockedConstruction<MpiComputationManager> mockMpiComputationManager = Mockito.mockConstruction(MpiComputationManager.class, (mock, context) -> {
            // MpiStatisticsFactory
            MpiStatisticsFactory mpiStatisticsFactory = (MpiStatisticsFactory) context.arguments().get(0);
            assertNotNull(mpiStatisticsFactory);
            // MpiExecutorContext
            MpiExecutorContext mpiExecutorContext = (MpiExecutorContext) context.arguments().get(1);
            assertNotNull(mpiExecutorContext);
            // MpiConfig
            MpiConfig mpiConfig = (MpiConfig) context.arguments().get(2);
            assertEquals(Path.of(localDir), mpiConfig.getLocalDir());
            assertEquals(Path.of(statDbDir), mpiConfig.getStatisticsDbDir());
            assertEquals(Path.of(archiveDir), mpiConfig.getStdOutArchive());
            assertEquals(timeout, mpiConfig.getTimeout());
            assertEquals(cores, mpiConfig.getCoresPerRank());
            assertEquals(isVerbose, mpiConfig.isVerbose());
        })) {
            // WHEN
            ComputationManager computationManager = MpiToolUtil.createMpiComputationManager(commandLine, FileSystems.getDefault());
            // THEN
            assertEquals(1, mockMpiComputationManager.constructed().size());
            assertNotNull(computationManager);
        }

    }

    private String addOption(String optionName) {
        return "-" + optionName;
    }
}
