/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collector;

import static java.util.Objects.requireNonNull;

/**
 * @author Sylvain Leclerc {@literal <sylvain.leclerc at rte-france.com>}
 */
final class CommandUtils {

    private CommandUtils() {
    }

    private static Collector<String, StringJoiner, String> getWrapperAndJoiner() {
        return Collector.of(
            () -> new StringJoiner(" "),
            (j, s) -> j.add("\"" + s + "\""),
            StringJoiner::merge,
            StringJoiner::toString);
    }

    /**
     * Generates a command string, with each argument wrapped with quotes.
     */
    static String commandToString(String program, List<String> args) {
        requireNonNull(program);
        requireNonNull(args);

        String argStr = args.stream().collect(getWrapperAndJoiner());
        return program + " " + argStr;
    }

    /**
     * Generates a command's argu string, with each argument wrapped with quotes.
     * @return the argu's string
     */
    static String commandArgsToString(List<String> args) {
        requireNonNull(args);
        return args.stream().collect(getWrapperAndJoiner());
    }
}
