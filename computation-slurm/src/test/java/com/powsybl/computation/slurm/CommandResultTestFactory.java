/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import java.util.Arrays;
import java.util.List;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
final class CommandResultTestFactory {

    private CommandResultTestFactory() {
    }

    static CommandResult emptyResult() {
        return new CommandResult(0, "\n", "");
    }

    static CommandResult simpleOutput(String msg) {
        return new CommandResult(0, msg + "\n", "");
    }

    static CommandResult multilineOutput(List<String> msg) {
        return simpleOutput(String.join("\n", msg));
    }

    static CommandResult multilineOutput(String... msg) {
        return multilineOutput(Arrays.asList(msg));
    }
}
