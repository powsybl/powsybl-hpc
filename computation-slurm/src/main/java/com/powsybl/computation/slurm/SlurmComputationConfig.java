/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.commons.config.ModuleConfig;
import com.powsybl.commons.config.PlatformConfig;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SlurmComputationConfig {

    private static final int DEFAULT_MAX_SSH_CONNECTION = 10;
    private static final int DEFAULT_MAX_RETRY = 5;
    private static final int DEFAULT_POLLING = 10; // "ls" command in flags directory
    // https://slurm.schedmd.com/slurm.conf.html
    // MinJobAge : The minimum age of a completed job before its record is purged from Slurm's active database.
    // The default value is 300 seconds.
    private static final int DEFAULT_SCONTROL_INTERVAL = 4; // "scontrol" command to check job state
    private static final int DEFAULT_CLEAN_INTERVAL = 300; // 5 mins
    private static final int DEFAULT_PORT = 22;
    private static final boolean DEFAULT_REMOTE = true;

    private final String workingDir;
    private final Path localDir;
    private final int pollingInSecond;
    // TODO change to second
    private final int scontrolInMinute;

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

        public int getMaxSshConnection() {
            return maxSshConnection;
        }

        public int getMaxRetry() {
            return maxRetry;
        }

        public String getHostname() {
            return hostname;
        }

        public int getPort() {
            return port;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }

    /**
     * Configuration for a remote access to a Slurm infrastructure, through SSH.
     */
    // TODO uniform time units
    SlurmComputationConfig(SshConfig sshConfig, String workingDir, Path localDir, int pollingInSecond,
                           int scontrolInMinute) {
        this.sshConfig = requireNonNull(sshConfig);
        this.workingDir = requireNonNull(workingDir);
        this.localDir = requireNonNull(localDir);
        this.pollingInSecond = pollingInSecond;
        this.scontrolInMinute = scontrolInMinute;
    }

    /**
     * Configuration for a local access to a Slurm infrastructure.
     */
    SlurmComputationConfig(String workingDir, Path localDir, int pollingInSecond, int scontrolInMinute) {
        this.sshConfig = null;
        this.workingDir = requireNonNull(workingDir);
        this.localDir = requireNonNull(localDir);
        this.pollingInSecond = pollingInSecond;
        this.scontrolInMinute = scontrolInMinute;
    }

    public static SlurmComputationConfig load() {
        return load(PlatformConfig.defaultConfig());
    }

    public static SlurmComputationConfig load(PlatformConfig platformConfig) {
        ModuleConfig config = platformConfig.getModuleConfig("slurm-computation-manager");

        boolean remote = config.getBooleanProperty("remote", DEFAULT_REMOTE);

        Path localDir = config.getPathProperty("local-dir");
        int pollingInSecond = config.getIntProperty("polling-time", DEFAULT_POLLING);
        int scontrolInMinute = config.getIntProperty("scontrol-time", DEFAULT_SCONTROL_INTERVAL);

        if (remote) {
            String workingDir = config.getStringProperty("remote-dir");

            String host = config.getStringProperty("hostname");
            int port = config.getIntProperty("port", DEFAULT_PORT);
            String userName = config.getStringProperty("username");
            String password = config.getStringProperty("password");
            int maxSshConnection = config.getIntProperty("max-ssh-connection", DEFAULT_MAX_SSH_CONNECTION);
            int maxRetry = config.getIntProperty("max-retry", DEFAULT_MAX_RETRY);
            SshConfig sshConfig = new SshConfig(host, port, userName, password, maxSshConnection, maxRetry);

            return new SlurmComputationConfig(sshConfig, workingDir, localDir, pollingInSecond, scontrolInMinute);
        } else {
            String workingDir = config.getStringProperty("working-dir");

            return new SlurmComputationConfig(workingDir, localDir, pollingInSecond, scontrolInMinute);
        }
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public Path getLocalDir() {
        return localDir;
    }

    public int getPollingInterval() {
        return pollingInSecond;
    }

    public int getScontrolInterval() {
        return scontrolInMinute;
    }

    public boolean isRemote() {
        return sshConfig != null;
    }

    public SshConfig getSshConfig() {
        return requireNonNull(sshConfig);
    }

}
