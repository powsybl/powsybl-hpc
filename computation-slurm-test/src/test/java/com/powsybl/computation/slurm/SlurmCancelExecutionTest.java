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
public class SlurmCancelExecutionTest extends AbstractIntegrationTests {

    @Override
    void baseTest(Supplier<AbstractExecutionHandler<String>> supplier, ComputationParameters parameters, boolean checkClean) {
        try (ComputationManager computationManager = new SlurmComputationManager(slurmConfig)) {
            CompletableFuture<String> completableFuture = computationManager.execute(EMPTY_ENV, supplier.get(), parameters);
            System.out.println("CompletableFuture would be cancelled in 5 seconds...");
            // TODO add a test before finished submit
            Thread.sleep(5000);
            boolean cancel = completableFuture.cancel(true);
            System.out.println("Cancelled:" + cancel);
            Assert.assertTrue(cancel);
            // TODO should throw CancellationException
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            failed = true;
        }
        // assert on main thread
        assertFalse(failed);
    }

    @Test
    public void testLongProgramToCancel() throws InterruptedException {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractExecutionHandler<String>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10);
            }

        };
        baseTest(supplier);
    }

    @Test
    public void testLongProgramInListToCancel() throws InterruptedException {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractExecutionHandler<String>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgramInList();
            }
        };
        baseTest(supplier);
    }

    @Test
    public void testMixedProgramsToCancel() throws InterruptedException {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractExecutionHandler<String>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return mixedPrograms();
            }
        };
        baseTest(supplier);
    }

    @Test
    public void testCancelFirstAfterDone() throws InterruptedException {
        // test only scancel the first job id even it is already done, the following job would be cancelled
        // automatically by slurm with "kill-on-invalid-dep" option
// An output example
//        49711             cFAD1   cccccopf     it          8  COMPLETED      0:0
//        49711.batch       batch                it          8  COMPLETED      0:0
//        49712             cFAD2   cccccopf     it          8     FAILED      1:0
//        49712.batch       batch                it          8     FAILED      1:0
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractExecutionHandler<String>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return cancelFirstJobAfterDone();
            }
        };
        baseTest(supplier);
    }

    @Test
    public void testCancelFirstWithBatches() throws InterruptedException {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractExecutionHandler<String>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return cancelFirstWithBatches();
            }
        };
        baseTest(supplier);
    }
}
