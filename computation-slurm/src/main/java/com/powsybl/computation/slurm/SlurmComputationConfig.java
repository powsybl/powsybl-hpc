/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.config.ModuleConfig;
import com.powsybl.commons.config.PlatformConfig;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class SlurmComputationConfig {

    private static final int DEFAULT_MAX_SSH_CONNECTION = 10;
    private static final int DEFAULT_MAX_RETRY = 5;
    private static final int DEFAULT_POLLING = 10; // "ls" command in flags directory
    // https://slurm.schedmd.com/slurm.conf.html
    // MinJobAge : The minimum age of a completed job before its record is purged from Slurm's active database.
    // The default value is 300 seconds.
    private static final int DEFAULT_SCONTROL_INTERVAL = 4 * 60; // "scontrol" command to check job state
    private static final int DEFAULT_PORT = 22;
    private static final boolean DEFAULT_REMOTE = true;
    private static final boolean DEFAULT_JOB_ARRAY = true;

    private final String workingDir;
    private final Path localDir;
    private final int pollingInSecond;
    private final int scontrolInSeconds;

    private final boolean jobArray;

    private final SshConfig sshConfig;

    public static class SshConfig {

        private final int maxSshConnection;
        private final int maxRetry;
        private final String hostname;
        private final int port;
        private final String username;
        private final String password;

        public SshConfig(String hostname, int port, String username, String password, int maxSshConnection, int maxRetry) {
            this.hostname = requireNonNull(hostname);
            this.port = port;
            this.username = requireNonNull(username);
            this.password = requireNonNull(password);
            this.maxSshConnection = maxSshConnection;
            this.maxRetry = maxRetry;
        }

        int getMaxSshConnection() {
            return maxSshConnection;
        }

        int getMaxRetry() {
            return maxRetry;
        }

        String getHostname() {
            return hostname;
        }

        int getPort() {
            return port;
        }

        String getUsername() {
            return username;
        }

        String getPassword() {
            return password;
        }
    }

    /**
     * Configuration for a remote access to a Slurm infrastructure, through SSH.
     */
    // TODO uniform time units
    SlurmComputationConfig(SshConfig sshConfig, String workingDir, Path localDir, int pollingInSecond,
                           int scontrolInSeconds, boolean jobArray) {
        this.sshConfig = requireNonNull(sshConfig);
        this.workingDir = requireNonNull(workingDir);
        this.localDir = requireNonNull(localDir);
        this.pollingInSecond = pollingInSecond;
        this.scontrolInSeconds = scontrolInSeconds;
        this.jobArray = jobArray;
    }

    /**
     * Configuration for a local access to a Slurm infrastructure.
     */
    SlurmComputationConfig(String workingDir, Path localDir, int pollingInSecond, int scontrolInSeconds, boolean jobArray) {
        this.sshConfig = null;
        this.workingDir = requireNonNull(workingDir);
        this.localDir = requireNonNull(localDir);
        this.pollingInSecond = pollingInSecond;
        this.scontrolInSeconds = scontrolInSeconds;
        this.jobArray = jobArray;
    }

    public static SlurmComputationConfig load() {
        return load(PlatformConfig.defaultConfig());
    }

    public static SlurmComputationConfig load(PlatformConfig platformConfig) {
        ModuleConfig config = platformConfig.getOptionalModuleConfig("slurm-computation-manager")
            .orElseThrow(() -> new PowsyblException("slurm-computation-manager module config not found in platform config"));

        boolean remote = config.getBooleanProperty("remote", DEFAULT_REMOTE);

        Path localDir = config.getPathProperty("local-dir");
        int pollingInSecond = config.getIntProperty("polling-time", DEFAULT_POLLING);
        int scontrolInSecond = config.getIntProperty("scontrol-time", DEFAULT_SCONTROL_INTERVAL);
        boolean arrayJob = config.getBooleanProperty("job-array", DEFAULT_JOB_ARRAY);

        if (remote) {
            String workingDir = config.getStringProperty("remote-dir");

            String host = config.getStringProperty("hostname");
            int port = config.getIntProperty("port", DEFAULT_PORT);
            String userName = config.getStringProperty("username");
            String password = config.getStringProperty("password");
            int maxSshConnection = config.getIntProperty("max-ssh-connection", DEFAULT_MAX_SSH_CONNECTION);
            int maxRetry = config.getIntProperty("max-retry", DEFAULT_MAX_RETRY);
            SshConfig sshConfig = new SshConfig(host, port, userName, password, maxSshConnection, maxRetry);

            return new SlurmComputationConfig(sshConfig, workingDir, localDir, pollingInSecond, scontrolInSecond, arrayJob);
        } else {
            String workingDir = config.getStringProperty("working-dir");

            return new SlurmComputationConfig(workingDir, localDir, pollingInSecond, scontrolInSecond, arrayJob);
        }
    }

    String getWorkingDir() {
        return workingDir;
    }

    Path getLocalDir() {
        return localDir;
    }

    int getPollingInterval() {
        return pollingInSecond;
    }

    int getScontrolInterval() {
        return scontrolInSeconds;
    }

    boolean isRemote() {
        return sshConfig != null;
    }

    SshConfig getSshConfig() {
        return requireNonNull(sshConfig);
    }

    boolean isJobArray() {
        return jobArray;
    }

}
