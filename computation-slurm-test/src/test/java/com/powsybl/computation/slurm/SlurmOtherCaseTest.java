/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.computation.AbstractExecutionHandler;
import com.powsybl.computation.CommandExecution;
import com.powsybl.computation.ComputationParameters;
import com.powsybl.computation.ComputationParametersBuilder;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.powsybl.computation.slurm.CommandExecutionsTestFactory.longProgram;
import static org.junit.Assert.*;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
@Ignore
public class SlurmOtherCaseTest extends SlurmUnitTests {

    @Test
    public void testLongProgramToCancelExternal() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractExecutionHandler<String>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(200);
            }
        };
        try (SlurmComputationManager computationManager = new SlurmComputationManager(slurmConfig)) {
            CompletableFuture<String> completableFuture = computationManager.execute(EMPTY_ENV, supplier.get(), ComputationParameters.empty());
            System.out.println("Go to cancel on server");
            // TODO should thrown CompletionException
            String join = completableFuture.join();
            assertNull(join);
            assertTrue(completableFuture.isCancelled());
        } catch (IOException e) {
            e.printStackTrace();
            failed = true;
        }
        // assert on main thread
        assertFalse(failed);
    }

    private void makeSlurmBusy() {
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                // FIXME get total resources on slurm
                return CommandExecutionsTestFactory.makeSlurmBusy(3);
            }
        };
        try (SlurmComputationManager computationManager = new SlurmComputationManager(slurmConfig)) {
            CompletableFuture<Void> execute = computationManager.execute(EMPTY_ENV, supplier.get(), ComputationParameters.empty());
            System.out.println("MakeSlurmBusy submitted.");
            execute.join();
        } catch (IOException e) {
            failed = true;
        }
    }

    @Test
    public void testDeadline() throws InterruptedException {
        Thread makeSlurmBusyThread = new Thread(this::makeSlurmBusy);
        makeSlurmBusyThread.start();
        TimeUnit.SECONDS.sleep(10);
        Supplier<AbstractExecutionHandler<Void>> deadlineTest = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10);
            }
        };
        try (SlurmComputationManager computationManager = new SlurmComputationManager(slurmConfig)) {
            // FIXME submit two jobs in same SCM
            ComputationParametersBuilder builder = new ComputationParametersBuilder();
            builder.setDeadline("longProgram", 12);
            ComputationParameters computationParameters = builder.build();
            CompletableFuture<Void> completableFuture = computationManager.execute(EMPTY_ENV, deadlineTest.get(), computationParameters);
            Assertions.assertThatThrownBy(() -> {
                System.out.println("Tring to get result, should throw CompletionException");
                Void join = completableFuture.join();
            }).isInstanceOf(CompletionException.class);
        } catch (IOException e) {
            e.printStackTrace();
            failed = true;
        }
        assertFalse(failed);
    }

    // sacctmgr show qos
    @Test
    public void testInvalidQos() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractReturnOKExecutionHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10);
            }
        };
        ComputationParameters parameters = new ComputationParametersBuilder().setTimeout("longProgram", 60).build();
        SlurmComputationParameters slurmComputationParameters = new SlurmComputationParameters(parameters, "THIS_QOS_SHOULD_NOT_EXIST_IN_SLURM");
        parameters.addExtension(SlurmComputationParameters.class, slurmComputationParameters);

        try (SlurmComputationManager computationManager = new SlurmComputationManager(slurmConfig)) {
            CompletableFuture<String> execute = computationManager.execute(EMPTY_ENV, supplier.get(), parameters);
            Assertions.assertThatThrownBy(execute::join)
                    .isInstanceOf(CompletionException.class)
                    .hasMessageContaining("Invalid qos specification");
        } catch (IOException e) {
            fail();
        }
    }

    // FIXME missing a shutdown test
}
