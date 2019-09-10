/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.google.common.base.Preconditions;

import java.util.Objects;

/**
 * A submission command to Slurm: sbatch [--deadline] scriptName.
 *
 * Sbatch scripts will be submitted using that kind of command.
 *
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
class SbatchCmd extends AbstractSlurmCmd<SbatchCmdResult> {

    private static final String SBATCH = "sbatch ";
    private static final String DATETIME_FORMATTER = "--deadline=`date -d \"%d seconds\" \"+%%Y-%%m-%%dT%%H:%%M:%%S\"` ";

    private final String scriptName;

    private long deadLine = 0;

    SbatchCmd(String scriptName) {
        this.scriptName = Objects.requireNonNull(scriptName);
    }

    SbatchCmd deadLine(long seconds) {
        Preconditions.checkArgument(seconds > 0, "Invalid seconds({}) for deadline: must be 1 or greater", seconds);
        deadLine = seconds;
        return this;
    }

    SbatchCmdResult send(CommandExecutor commandExecutor) throws SlurmCmdNonZeroException {
        CommandResult result = sendCmd(commandExecutor, toString());
        return new SbatchCmdResult(result);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append(SBATCH);
        if (deadLine > 0) {
            sb.append(String.format(DATETIME_FORMATTER, deadLine));
        }
        return sb.append(scriptName).toString();
    }
}
