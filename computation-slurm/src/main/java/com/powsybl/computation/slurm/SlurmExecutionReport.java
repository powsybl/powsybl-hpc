/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.computation.Command;
import com.powsybl.computation.DefaultExecutionReport;
import com.powsybl.computation.ExecutionError;
import com.powsybl.computation.ExecutionReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmExecutionReport extends DefaultExecutionReport implements ExecutionReport {

    private static final Logger LOG = LoggerFactory.getLogger(SlurmExecutionReport.class);

    private final Path workingDir;

    SlurmExecutionReport(List<ExecutionError> errors, Path workingDir) {
        super(errors);
        this.workingDir = Objects.requireNonNull(workingDir);
    }

    @Override
    public Optional<InputStream> getStdOut(Command command, int index) {
        return getStream(command, index, SlurmConstants.OUT_EXT);
    }

    @Override
    public Optional<InputStream> getStdErr(Command command, int index) {
        return getStream(command, index, SlurmConstants.ERR_EXT);
    }

    private Optional<InputStream> getStream(Command command, int index, String type) {
        Objects.requireNonNull(command);

        if (index < 0) {
            throw new IllegalArgumentException("index:" + index + " is not valid");
        }

        String id = command.getId();
        Path stdPath = workingDir.resolve(id + "_" + index + type);
        try {
            return Optional.of(Files.newInputStream(stdPath));
        } catch (IOException e) {
            LOG.warn("Can not read: " + stdPath);
        }
        return Optional.empty();
    }

}
