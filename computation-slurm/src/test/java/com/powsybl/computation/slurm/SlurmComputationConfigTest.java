/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.config.InMemoryPlatformConfig;
import com.powsybl.commons.config.MapModuleConfig;
import com.powsybl.commons.config.PlatformConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Mathieu Bague {@literal <mathieu.bague at rte-france.com>}
 */
class SlurmComputationConfigTest {

    private static final String HOSTNAME = "localhost";
    private static final int PORT = 8022;
    private static final String USERNAME = "johndoe";
    private static final String PASSWORD = "p@ssw0rd";
    private static final String REMOTE_DIR = "/remote/dir";
    private static final String WORKING_DIR = "/working/dir";
    private static final String LOCAL_DIR = "/local/dir";
    private static final int MAX_SSH_CONNECTION = 5;
    private static final int MAX_RETRY = 10;
    private static final int POLLING_INTERVAL = 30;
    private static final int SCONTROL_INTERVAL = 15;
    private static final boolean JOB_ARRAY = false;

    private FileSystem fileSystem;

    private PlatformConfig platformConfig;

    private MapModuleConfig moduleConfig;

    @BeforeEach
    public void setUp() {
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        InMemoryPlatformConfig platformConfigInMemory = new InMemoryPlatformConfig(fileSystem);

        moduleConfig = platformConfigInMemory.createModuleConfig("slurm-computation-manager");
        moduleConfig.setStringProperty("polling-time", Integer.toString(POLLING_INTERVAL));
        moduleConfig.setStringProperty("scontrol-time", Integer.toString(SCONTROL_INTERVAL));
        moduleConfig.setStringProperty("job-array", Boolean.toString(JOB_ARRAY));

        // Remote configuration (default)
        moduleConfig.setStringProperty("hostname", HOSTNAME);
        moduleConfig.setStringProperty("port", Integer.toString(PORT));
        moduleConfig.setStringProperty("username", USERNAME);
        moduleConfig.setStringProperty("password", PASSWORD);
        moduleConfig.setStringProperty("remote-dir", REMOTE_DIR);
        moduleConfig.setStringProperty("local-dir", LOCAL_DIR);
        moduleConfig.setStringProperty("max-ssh-connection", Integer.toString(MAX_SSH_CONNECTION));
        moduleConfig.setStringProperty("max-retry", Integer.toString(MAX_RETRY));

        // Local configuration
        moduleConfig.setStringProperty("working-dir", WORKING_DIR);

        this.platformConfig = platformConfigInMemory;
    }

    @AfterEach
    public void tearDown() throws IOException {
        fileSystem.close();
    }

    @Test
    void testLocal() {
        SlurmComputationConfig config = SlurmComputationConfig.load(platformConfig);
        assertTrue(config.isRemote());

        // Switch to local mode
        moduleConfig.setStringProperty("remote", "false");
        config = SlurmComputationConfig.load(platformConfig);
        assertFalse(config.isRemote());

        assertEquals(LOCAL_DIR, config.getLocalDir().toString());
        assertEquals(WORKING_DIR, config.getWorkingDir());
        assertEquals(POLLING_INTERVAL, config.getPollingInterval());
    }

    @Test
    void testRemote() {
        SlurmComputationConfig config = SlurmComputationConfig.load(platformConfig);
        assertTrue(config.isRemote());

        SlurmComputationConfig.SshConfig sshConfig = config.getSshConfig();
        assertNotNull(sshConfig);
        assertEquals(HOSTNAME, sshConfig.getHostname());
        assertEquals(PORT, sshConfig.getPort());
        assertEquals(USERNAME, sshConfig.getUsername());
        assertEquals(PASSWORD, sshConfig.getPassword());
        assertEquals(MAX_SSH_CONNECTION, sshConfig.getMaxSshConnection());
        assertEquals(MAX_RETRY, sshConfig.getMaxRetry());

        assertEquals(LOCAL_DIR, config.getLocalDir().toString());
        assertEquals(REMOTE_DIR, config.getWorkingDir());
        assertEquals(POLLING_INTERVAL, config.getPollingInterval());
        assertEquals(SCONTROL_INTERVAL, config.getScontrolInterval());
        assertEquals(JOB_ARRAY, config.isJobArray());
    }

    @Test
    void testModuleAbsent() {
        platformConfig = new InMemoryPlatformConfig(fileSystem);
        PowsyblException exception = assertThrows(PowsyblException.class, () -> SlurmComputationConfig.load(platformConfig));
        assertEquals("slurm-computation-manager module config not found in platform config", exception.getMessage());
    }
}
