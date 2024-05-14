/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.jcraft.jsch.JSchException;
import com.pastdev.jsch.command.CommandRunner;
import com.powsybl.commons.PowsyblException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Executes shell commands through an SSH connection,
 * using an underlying JSCH {@link CommandRunner}.
 *
 * @author Sylvain Leclerc {@literal <sylvain.leclerc at rte-france.com>}
 */
class SshCommandExecutor implements CommandExecutor {

    private final CommandRunner runner;

    SshCommandExecutor(CommandRunner runner) {
        this.runner = Objects.requireNonNull(runner);
    }

    @Override
    public CommandResult execute(String command) {
        Objects.requireNonNull(command);

        try {
            CommandRunner.ExecuteResult result = runner.execute(command);
            return new CommandResult(result.getExitCode(), result.getStdout(), result.getStderr());
        } catch (JSchException | IOException e) {
            throw new PowsyblException(e);
        }
    }

    @Override
    public void close() {
        try {
            runner.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
