/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import static java.util.Objects.requireNonNull;

/**
 * The results of the execution of a shell command:
 * the exit code, and standard output and standard error as strings.
 *
 * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
 */
public record CommandResult(int exitCode, String stdOut, String stdErr) {

    public CommandResult(int exitCode, String stdOut, String stdErr) {
        this.exitCode = exitCode;
        this.stdOut = requireNonNull(stdOut);
        this.stdErr = requireNonNull(stdErr);
    }
}
