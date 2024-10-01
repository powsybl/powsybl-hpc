/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.computation.slurm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Nicolas Rol {@literal <nicolas.rol at rte-france.com>}
 */
class LocalCommandExecutorTest {

    @Test
    void test() {
        try (CommandExecutor commandExecutor = new LocalCommandExecutor()) {
            CommandResult commandResult = commandExecutor.execute("echo hello");
            assertEquals(0, commandResult.exitCode());
            assertEquals("hello" + System.lineSeparator(), commandResult.stdOut());
            assertEquals("", commandResult.stdErr());
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }
}
