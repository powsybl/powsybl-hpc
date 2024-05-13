/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * A submission command to Slurm: sbatch options scriptName.
 *
 * Sbatch scripts will be submitted using that kind of command.
 *
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
class SbatchCmd extends AbstractSlurmCmd<SbatchCmdResult> {

    private static final String SBATCH = "sbatch";

    private final Map<String, String> argsByName;
    private final Map<Character, String> argsByChar;

    private final TreeSet<String> options;

    private final String scriptName;

    private String cmd;

    SbatchCmd(Map<String, String> argsByName, Map<Character, String> argsByChar, TreeSet<String> options, String scriptName) {
        this.argsByName = Objects.requireNonNull(argsByName);
        this.argsByChar = Objects.requireNonNull(argsByChar);
        this.scriptName = Objects.requireNonNull(scriptName);
        this.options = Objects.requireNonNull(options);
    }

    SbatchCmdResult send(CommandExecutor commandExecutor) throws SlurmCmdNonZeroException {
        CommandResult result = sendCmd(commandExecutor, toString());
        return new SbatchCmdResult(result);
    }

    @Override
    public String toString() {
        if (cmd == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(SBATCH);
            argsByChar.forEach((c, v) -> {
                sb.append(" -").append(c);
                sb.append(" ").append(v);
            });
            argsByName.forEach((k, v) -> {
                sb.append(" --").append(k);
                sb.append("=").append(v);
            });
            options.forEach(opt -> {
                sb.append(" --");
                sb.append(opt);
            });
            sb.append(" ").append(scriptName);
            cmd = sb.toString();
        }
        return cmd;
    }
}
