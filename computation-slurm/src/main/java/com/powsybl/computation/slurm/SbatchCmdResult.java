/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
class SbatchCmdResult extends AbstractSlurmCmdResult {

    SbatchCmdResult(CommandResult commandResult) {
        super(commandResult);
    }

    Long getSubmittedJobId() throws SlurmCmdNonZeroException {
        if (isOk()) {
            String jobId = commandResult.getStdOut().replaceAll("[^0-9]", "");
            return Long.valueOf(jobId);
        } else {
            throw new SlurmCmdNonZeroException("Sbatch failed:" + toString() +
                    "\nexitcode:" + commandResult.getExitCode() +
                    "\nerr:" + commandResult.getStdErr() +
                    "\nout:" + commandResult.getStdOut());
        }
    }
}
