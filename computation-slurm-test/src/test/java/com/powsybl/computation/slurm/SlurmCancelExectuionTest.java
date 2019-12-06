/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.computation.AbstractExecutionHandler;
import com.powsybl.computation.CommandExecution;
import com.powsybl.computation.ComputationManager;
import com.powsybl.computation.ComputationParameters;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.powsybl.computation.slurm.CommandExecutionsTestFactory.*;
import static org.junit.Assert.assertFalse;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
@Ignore
public class SlurmCancelExectuionTest extends SlurmUnitTests {

    private void baseTest(Supplier<AbstractExecutionHandler<Void>> supplier) throws InterruptedException {
        baseTest(supplier, ComputationParameters.empty());
    }

    private void baseTest(Supplier<AbstractExecutionHandler<Void>> supplier, ComputationParameters parameters) throws InterruptedException {
        try (ComputationManager computationManager = new SlurmComputationManager(slurmConfig)) {
            CompletableFuture<Void> completableFuture = computationManager.execute(EMPTY_ENV, supplier.get(), parameters);
            System.out.println("CompletableFuture would be cancelled in 5 seconds...");
            // TODO add a test before finished submit
            Thread.sleep(5000);
            boolean cancel = completableFuture.cancel(true);
            System.out.println("Cancelled:" + cancel);
            Assert.assertTrue(cancel);
            // TODO should throw CancellationException
        } catch (IOException e) {
            e.printStackTrace();
            failed = true;
        }
        // assert on main thread
        assertFalse(failed);
    }

    @Test
    public void testLongProgramToCancel() throws InterruptedException {
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10);
            }
        };
        baseTest(supplier);
    }

    @Test
    public void testLongProgramInListToCancel() throws InterruptedException {
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgramInList();
            }
        };
        baseTest(supplier);
    }

    @Test
    public void testMixedProgramsToCancel() throws InterruptedException {
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return mixedPrograms();
            }
        };
        baseTest(supplier);
    }
}
