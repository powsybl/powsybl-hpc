/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.powsybl.commons.config.ModuleConfig;
import com.powsybl.commons.config.YamlModuleConfigRepository;
import com.powsybl.computation.AbstractExecutionHandler;
import com.powsybl.computation.ComputationParameters;
import com.powsybl.computation.ExecutionEnvironment;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public abstract class AbstractIntegrationTests {

    static final Logger LOGGER = LoggerFactory.getLogger(AbstractIntegrationTests.class);
    static final ExecutionEnvironment EMPTY_ENV = new ExecutionEnvironment(Collections.emptyMap(), "unit_test_", false);
    private static final ch.qos.logback.classic.Logger SCM_LOGGER = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SlurmComputationManager.class);

    SlurmComputationConfig slurmConfig;

    volatile boolean failed = false;

    @Before
    public void setup() {
        YamlModuleConfigRepository configRepository = new YamlModuleConfigRepository(Paths.get("src/test/resources/config.yml"));
        ModuleConfig config = configRepository.getModuleConfig("slurm-computation-manager")
                .orElseThrow(() -> new RuntimeException("Config.yaml is not good. Please recheck the config.yaml.example"));
        slurmConfig = generateSlurmConfigWithShortScontrolTime(config);
        // TODO prepare myapps if necessary
    }

    private static SlurmComputationConfig.SshConfig generateSsh(ModuleConfig config) {
        return new SlurmComputationConfig.SshConfig(config.getStringProperty("hostname"), 22, config.getStringProperty("username"), config.getStringProperty("password"), 10, 5);
    }

    /**
     * Returns a config in which scontrol monitor runs every minute
     */
    private static SlurmComputationConfig generateSlurmConfigWithShortScontrolTime(ModuleConfig config) {
        return new SlurmComputationConfig(generateSsh(config), config.getStringProperty("remote-dir"),
                Paths.get(config.getStringProperty("local-dir")), 5, 1);
    }

    static void assertIsCleanedAfterWait(TaskStore store) {
        try {
            int seconds = 15;
            LOGGER.debug("Waiting " + seconds + " seconds to check clean...");
            TimeUnit.SECONDS.sleep(seconds);
            assertTrue(store.isEmpty());
        } catch (InterruptedException e) {
            fail();
        }
    }

    void baseTest(Supplier<AbstractExecutionHandler<String>> supplier) {
        baseTest(supplier, ComputationParameters.empty());
    }

    void baseTest(Supplier<AbstractExecutionHandler<String>> supplier, ComputationParameters parameters) {
        baseTest(supplier, parameters, false);
    }

    void baseTest(Supplier<AbstractExecutionHandler<String>> supplier, boolean checkClean) {
        baseTest(supplier, ComputationParameters.empty(), checkClean);
    }

    abstract void baseTest(Supplier<AbstractExecutionHandler<String>> supplier, ComputationParameters parameters, boolean checkClean);

    static void addApprender(ListAppender<ILoggingEvent> appender) {
        appender.start();
        SCM_LOGGER.addAppender(appender);
    }

    static void removeApprender(ListAppender<ILoggingEvent> appender) {
        appender.stop();
        SCM_LOGGER.detachAppender(appender);
    }
}
