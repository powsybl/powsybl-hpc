/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.powsybl.commons.config.InMemoryPlatformConfig;
import com.powsybl.commons.config.MapModuleConfig;
import com.powsybl.commons.config.PlatformConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;

import static org.junit.Assert.*;

/**
 * @author Mathieu Bague <mathieu.bague at rte-france.com>
 */
public class SlurmComputationConfigTest {

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
    private static final boolean ARRAY_JOB = false;

    private FileSystem fileSystem;

    private PlatformConfig platformConfig;

    private MapModuleConfig moduleConfig;

    @Before
    public void setUp() {
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        InMemoryPlatformConfig platformConfig = new InMemoryPlatformConfig(fileSystem);

        moduleConfig = platformConfig.createModuleConfig("slurm-computation-manager");
        moduleConfig.setStringProperty("polling-time", Integer.toString(POLLING_INTERVAL));
        moduleConfig.setStringProperty("scontrol-time", Integer.toString(SCONTROL_INTERVAL));
        moduleConfig.setStringProperty("array-job", Boolean.toString(ARRAY_JOB));

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

        this.platformConfig = platformConfig;
    }

    @After
    public void tearDown() throws IOException {
        fileSystem.close();
    }

    @Test
    public void testLocal() {
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
    public void testRemote() {
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
        assertEquals(ARRAY_JOB, config.isArrayJob());
    }
}
