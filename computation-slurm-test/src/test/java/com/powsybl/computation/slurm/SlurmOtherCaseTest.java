/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import static com.powsybl.computation.slurm.CommandExecutionsTestFactory.makeSlurmBusy;
import static org.junit.Assert.*;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
@Ignore
public class SlurmOtherCaseTest extends AbstractIntegrationTests {

    @Test
    public void testLongProgramToCancelExternal() {
        testLongProgramToCancelExternal(batchConfig);
        testLongProgramToCancelExternal(arrayConfig);
    }

    private void testLongProgramToCancelExternal(SlurmComputationConfig config) {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractExecutionHandler<String>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(200);
            }
        };
        try (SlurmComputationManager computationManager = new SlurmComputationManager(config)) {
            CompletableFuture<String> completableFuture = computationManager.execute(EMPTY_ENV, supplier.get(), ComputationParameters.empty());
            System.out.println("Go to interrupt on server");
            Assertions.assertThatThrownBy(completableFuture::join)
                    .isInstanceOf(CompletionException.class);
            // TODO detail msg
//                    .hasMessageContaining("is CANCELLED");
            assertIsCleaned(computationManager.getTaskStore());
        } catch (IOException e) {
            e.printStackTrace();
            failed = true;
        } catch (CompletionException ce) {
            System.out.println("in ce");
        }
        // assert on main thread
        assertFalse(failed);
    }

    private void runMakeSlurmBusy() {
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                // FIXME get total resources on slurm
                return makeSlurmBusy(3);
            }
        };
        try (SlurmComputationManager computationManager = new SlurmComputationManager(batchConfig)) {
            CompletableFuture<Void> execute = computationManager.execute(EMPTY_ENV, supplier.get(), ComputationParameters.empty());
            System.out.println("MakeSlurmBusy submitted.");
            execute.join();
        } catch (IOException e) {
            failed = true;
        }
    }

    @Test
    public void testDeadline() throws InterruptedException {
        testDeadline(batchConfig);
        testDeadline(arrayConfig);
    }

    private void testDeadline(SlurmComputationConfig config) throws InterruptedException {
        Thread makeSlurmBusyThread = new Thread(this::runMakeSlurmBusy);
        makeSlurmBusyThread.start();
        TimeUnit.SECONDS.sleep(10);
        Supplier<AbstractExecutionHandler<Void>> deadlineTest = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10);
            }
        };
        try (SlurmComputationManager computationManager = new SlurmComputationManager(config)) {
            // FIXME submit two jobs in same SCM
            ComputationParametersBuilder builder = new ComputationParametersBuilder();
            builder.setDeadline("longProgram", 12);
            ComputationParameters computationParameters = builder.build();
            CompletableFuture<Void> completableFuture = computationManager.execute(EMPTY_ENV, deadlineTest.get(), computationParameters);
            Assertions.assertThatThrownBy(completableFuture::join)
                    .isInstanceOf(CompletionException.class);
            assertIsCleaned(computationManager.getTaskStore());
        } catch (IOException e) {
            e.printStackTrace();
            failed = true;
        }
        assertFalse(failed);
    }

    // sacctmgr show qos
    @Test
    public void testInvalidQos() {
        testInvalidQos(batchConfig);
        testInvalidQos(arrayConfig);
    }

    private void testInvalidQos(SlurmComputationConfig config) {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractReturnOKExecutionHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10);
            }
        };
        ComputationParameters parameters = new ComputationParametersBuilder().setTimeout("longProgram", 60).build();
        SlurmComputationParameters slurmComputationParameters = new SlurmComputationParameters(parameters, "THIS_QOS_SHOULD_NOT_EXIST_IN_SLURM");
        parameters.addExtension(SlurmComputationParameters.class, slurmComputationParameters);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        addApprender(appender);
        try (SlurmComputationManager computationManager = new SlurmComputationManager(config)) {
            CompletableFuture<String> execute = computationManager.execute(EMPTY_ENV, supplier.get(), parameters);
            execute.join();
            assertIsCleaned(computationManager.getTaskStore());
            assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("exit point 2: Error by slurm")));
        } catch (IOException e) {
            fail();
        } catch (CompletionException ce) {
            assertTrue(ce.getCause().getMessage().contains("Invalid qos specification"));
        } finally {
            removeApprender(appender);
        }
    }

    // FIXME shutdown and check programmatically
    @Test
    public void testStopSendingAfterShutdown() {
        try (SlurmComputationManager computationManager = new SlurmComputationManager(batchConfig)) {
            CompletableFuture<Void> execute = computationManager.execute(EMPTY_ENV, new AbstractExecutionHandler<Void>() {
                @Override
                public List<CommandExecution> before(Path path) throws IOException {
                    return makeSlurmBusy(5);
                }
            }, ComputationParameters.empty());
            execute.join();
        } catch (IOException e) {
            fail();
        }
    }

    @Override
    void baseTest(SlurmComputationConfig config, Supplier<AbstractExecutionHandler<String>> supplier, ComputationParameters parameters) {
        // do nothing
    }
}
