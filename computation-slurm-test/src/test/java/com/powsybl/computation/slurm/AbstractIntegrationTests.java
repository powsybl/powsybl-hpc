/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.powsybl.commons.config.ModuleConfig;
import com.powsybl.commons.config.YamlModuleConfigRepository;
import com.powsybl.computation.AbstractExecutionHandler;
import com.powsybl.computation.ComputationParameters;
import com.powsybl.computation.ExecutionEnvironment;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Yichen TANG {@literal <yichen.tang at rte-france.com>}
 */
public abstract class AbstractIntegrationTests {

    static final Logger LOGGER = LoggerFactory.getLogger(AbstractIntegrationTests.class);
    static final ExecutionEnvironment EMPTY_ENV = new ExecutionEnvironment(Collections.emptyMap(), "unit_test_", false);
    private static final ch.qos.logback.classic.Logger SCM_LOGGER = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SlurmComputationManager.class);

    SlurmComputationConfig batchConfig;
    SlurmComputationConfig arrayConfig;
    ModuleConfig moduleConfig;

    volatile boolean failed = false;

    @BeforeEach
    public void setup() {
        YamlModuleConfigRepository configRepository = new YamlModuleConfigRepository(Paths.get("src/test/resources/config.yml"));
        moduleConfig = configRepository.getModuleConfig("slurm-computation-manager")
                .orElseThrow(() -> new RuntimeException("Config.yaml is not good. Please recheck the config.yaml.example"));
        batchConfig = batchConfig(moduleConfig);
        arrayConfig = arrayConfig(moduleConfig);
        // TODO prepare myapps if necessary
    }

    private static SlurmComputationConfig.SshConfig generateSsh(ModuleConfig config) {
        return new SlurmComputationConfig.SshConfig(config.getStringProperty("hostname"), 22, config.getStringProperty("username"), config.getStringProperty("password"), 10, 5);
    }

    private static SlurmComputationConfig batchConfig(ModuleConfig config) {
        return new SlurmComputationConfig(generateSsh(config), config.getStringProperty("remote-dir"),
                Paths.get(config.getStringProperty("local-dir")), 5, 1, false);
    }

    private static SlurmComputationConfig arrayConfig(ModuleConfig config) {
        return new SlurmComputationConfig(generateSsh(config), config.getStringProperty("remote-dir"),
                Paths.get(config.getStringProperty("local-dir")), 5, 1, true);
    }

    static void assertIsCleaned(TaskStore store) {
        assertTrue(store.isEmpty());
    }

    void baseTest(Supplier<AbstractExecutionHandler<String>> supplier) {
        baseTest(supplier, ComputationParameters.empty());
    }

    void baseTest(Supplier<AbstractExecutionHandler<String>> supplier, ComputationParameters parameters) {
        baseTest(batchConfig, supplier, parameters);
        baseTest(arrayConfig, supplier, parameters);
    }

    abstract void baseTest(SlurmComputationConfig config, Supplier<AbstractExecutionHandler<String>> supplier, ComputationParameters parameters);

    static void addAppender(ListAppender<ILoggingEvent> appender) {
        appender.start();
        SCM_LOGGER.addAppender(appender);
    }

    static void removeAppender(ListAppender<ILoggingEvent> appender) {
        appender.stop();
        SCM_LOGGER.detachAppender(appender);
    }

    static void generateZipFileOnRemote(String name, Path dest) {
        try (InputStream inputStream = SlurmNormalExecutionTest.class.getResourceAsStream("/afile.txt");
             ZipArchiveOutputStream zos = new ZipArchiveOutputStream(Files.newOutputStream(dest))) {
            ZipArchiveEntry entry = new ZipArchiveEntry(name);
            zos.putArchiveEntry(entry);
            assert inputStream != null;
            IOUtils.copy(inputStream, zos);
            zos.closeArchiveEntry();
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
