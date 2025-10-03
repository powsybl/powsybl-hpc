/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.computation.mpi;

import java.nio.file.Path;
import java.util.Objects;

/**
 * @author Nicolas Rol {@literal <nicolas.rol at rte-france.com>}
 */
public class MpiConfig {

    private static final int DEFAULT_TIMEOUT = 60;

    private boolean verbose = false;
    private int coresPerRank = 1;
    private int timeout = DEFAULT_TIMEOUT;
    private Path localDir = null;
    private Path statisticsDbDir = null;
    private Path stdOutArchive = null;
    private String statisticsDbName = null;

    private static int checkTimeout(int timeout) {
        if (timeout < -1 || timeout == 0) {
            throw new IllegalArgumentException("invalid timeout value " + timeout);
        }
        return timeout;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public MpiConfig setVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    public int getCoresPerRank() {
        return coresPerRank;
    }

    public MpiConfig setCoresPerRank(int coresPerRank) {
        this.coresPerRank = coresPerRank;
        return this;
    }

    public int getTimeout() {
        return timeout;
    }

    public MpiConfig setTimeout(int timeout) {
        this.timeout = checkTimeout(timeout);
        return this;
    }

    public Path getLocalDir() {
        return localDir;
    }

    public MpiConfig setLocalDir(Path localDir) {
        this.localDir = Objects.requireNonNull(localDir);
        return this;
    }

    public Path getStatisticsDbDir() {
        return statisticsDbDir;
    }

    public MpiConfig setStatisticsDbDir(Path statisticsDbDir) {
        this.statisticsDbDir = Objects.requireNonNull(statisticsDbDir);
        return this;
    }

    public Path getStdOutArchive() {
        return stdOutArchive;
    }

    public MpiConfig setStdOutArchive(Path stdOutArchive) {
        this.stdOutArchive = Objects.requireNonNull(stdOutArchive);
        return this;
    }

    public String getStatisticsDbName() {
        return statisticsDbName;
    }

    public MpiConfig setStatisticsDbName(String statisticsDbName) {
        this.statisticsDbName = statisticsDbName;
        return this;
    }

}
