/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author Yichen Tang {@literal <yichen.tang at rte-france.com>}
 */
abstract class AbstractSlurmCmd<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSlurmCmd.class);

    abstract T send(CommandExecutor commandExecutor) throws SlurmCmdNonZeroException;

    CommandResult sendCmd(CommandExecutor commandExecutor, String cmd) throws SlurmCmdNonZeroException {
        Objects.requireNonNull(commandExecutor);
        Objects.requireNonNull(cmd);
        LOGGER.debug("Sending cmd: {}", cmd);
        CommandResult result = commandExecutor.execute(cmd);
        if (result.exitCode() != 0) {
            throw new SlurmCmdNonZeroException(result);
        }
        return result;
    }
}
