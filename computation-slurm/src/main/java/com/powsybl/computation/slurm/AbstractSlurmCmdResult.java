/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import java.util.Objects;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
public abstract class AbstractSlurmCmdResult implements SlurmCmdResult {

    protected final CommandResult commandResult;

    AbstractSlurmCmdResult(CommandResult commandResult) {
        this.commandResult = Objects.requireNonNull(commandResult);
    }

    @Override
    public boolean isOk() {
        return commandResult.getExitCode() == 0;
    }
}
