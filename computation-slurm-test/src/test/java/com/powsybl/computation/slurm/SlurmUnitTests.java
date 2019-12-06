/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.commons.config.ModuleConfig;
import com.powsybl.commons.config.YamlModuleConfigRepository;
import com.powsybl.computation.ExecutionEnvironment;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Collections;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmUnitTests {

    static final Logger LOGGER = LoggerFactory.getLogger(SlurmUnitTests.class);
    static final ExecutionEnvironment EMPTY_ENV = new ExecutionEnvironment(Collections.emptyMap(), "unit_test_", false);

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
}
