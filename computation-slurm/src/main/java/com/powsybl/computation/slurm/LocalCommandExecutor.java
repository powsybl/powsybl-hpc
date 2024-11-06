/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Executes shell commands using the local OS.
 *
 * @author Sylvain Leclerc {@literal <sylvain.leclerc at rte-france.com>}
 */
class LocalCommandExecutor implements CommandExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalCommandExecutor.class);

    private static final Charset CHARSET = StandardCharsets.ISO_8859_1;

    @Override
    public CommandResult execute(String command) {
        Objects.requireNonNull(command);

        LOGGER.debug("Locally executing command {}", command);
        try (ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
             ByteArrayOutputStream stdErr = new ByteArrayOutputStream()) {

            DefaultExecutor executor = DefaultExecutor.builder().get();
            executor.setExitValues(null); //Accept all exit values
            executor.setStreamHandler(new PumpStreamHandler(stdOut, stdErr));
            int exitCode = executor.execute(CommandLine.parse(command));

            return new CommandResult(exitCode, stdOut.toString(CHARSET), stdErr.toString(CHARSET));

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        // Nothing to do
    }
}
