/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import java.util.Objects;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
abstract class AbstractSlurmCmd<T> {

    abstract T send(CommandExecutor commandExecutor) throws SlurmCmdNonZeroException;

    CommandResult sendCmd(CommandExecutor commandExecutor, String cmd) throws SlurmCmdNonZeroException {
        Objects.requireNonNull(commandExecutor);
        Objects.requireNonNull(cmd);
        CommandResult result = commandExecutor.execute(cmd);
        if (result.getExitCode() != 0) {
            throw new SlurmCmdNonZeroException(result);
        }
        return result;
    }
}
