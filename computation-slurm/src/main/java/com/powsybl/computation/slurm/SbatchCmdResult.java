/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

/**
 * @author Yichen Tang {@literal <yichen.tang at rte-france.com>}
 */
class SbatchCmdResult extends AbstractSlurmCmdResult {

    SbatchCmdResult(CommandResult commandResult) {
        super(commandResult);
    }

    long getSubmittedJobId() throws SlurmCmdNonZeroException {
        if (isOk()) {
            String jobId = commandResult.stdOut().replaceAll("\\D", "");
            return Long.parseLong(jobId);
        } else {
            throw new SlurmCmdNonZeroException("Sbatch failed:" + this +
                    "\nexitcode:" + commandResult.exitCode() +
                    "\nerr:" + commandResult.stdErr() +
                    "\nout:" + commandResult.stdOut());
        }
    }
}
