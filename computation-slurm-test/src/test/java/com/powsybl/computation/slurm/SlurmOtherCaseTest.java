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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.powsybl.computation.slurm.CommandExecutionsTestFactory.longProgram;
import static com.powsybl.computation.slurm.CommandExecutionsTestFactory.makeSlurmBusy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Yichen TANG {@literal <yichen.tang at rte-france.com>}
 */
@Disabled("Slurm integration tests must be run locally.")
class SlurmOtherCaseTest extends AbstractIntegrationTests {
    static final Logger LOGGER = LoggerFactory.getLogger(SlurmOtherCaseTest.class);

    @Test
    void testLongProgramToCancelExternal() {
        testLongProgramToCancelExternal(batchConfig);
        testLongProgramToCancelExternal(arrayConfig);
    }

    private void testLongProgramToCancelExternal(SlurmComputationConfig config) {
        // Script configuration
        String program = String.format("%s/%s",
            moduleConfig.getOptionalStringProperty("program").orElse("No program configured"),
            "testToStop.sh");

        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractExecutionHandler<>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(200, program);
            }
        };
        try (SlurmComputationManager computationManager = new SlurmComputationManager(config)) {
            CompletableFuture<String> completableFuture = computationManager.execute(EMPTY_ENV, supplier.get(), ComputationParameters.empty());
            LOGGER.warn("Please interrupt the process on the server using the command \"scancel <JobId>\"");
            Assertions.assertThatThrownBy(completableFuture::join)
                    .isInstanceOf(CompletionException.class)
                    .hasMessageContaining("has been interrupted on slurm infrastructure");
            assertIsCleaned(computationManager.getTaskStore());
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
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
    void testDeadline() throws InterruptedException {
        testDeadline(batchConfig);
        testDeadline(arrayConfig);
    }

    private void testDeadline(SlurmComputationConfig config) throws InterruptedException {
        // Script configuration
        String program = String.format("%s/%s",
            moduleConfig.getOptionalStringProperty("program").orElse("No program configured"),
            "testToStop.sh");

        Thread makeSlurmBusyThread = new Thread(this::runMakeSlurmBusy);
        makeSlurmBusyThread.start();
        TimeUnit.SECONDS.sleep(10);
        Supplier<AbstractExecutionHandler<Void>> deadlineTest = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10, program);
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
            LOGGER.error(e.getMessage(), e);
            failed = true;
        }
        assertFalse(failed);
    }

    // sacctmgr show qos
    @Test
    void testInvalidQos() {
        testInvalidQos(batchConfig);
        testInvalidQos(arrayConfig);
    }

    private void testInvalidQos(SlurmComputationConfig config) {
        // Script configuration
        String program = String.format("%s/%s",
            moduleConfig.getOptionalStringProperty("program").orElse("No program configured"),
            "testToStop.sh");

        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractReturnOKExecutionHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10, program);
            }
        };
        ComputationParameters parameters = new ComputationParametersBuilder().setTimeout("longProgram", 60).build();
        SlurmComputationParameters slurmComputationParameters = new SlurmComputationParameters(parameters, "THIS_QOS_SHOULD_NOT_EXIST_IN_SLURM");
        parameters.addExtension(SlurmComputationParameters.class, slurmComputationParameters);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        addAppender(appender);
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
            removeAppender(appender);
        }
    }

    // FIXME shutdown and check programmatically
    @Test
    void testStopSendingAfterShutdown() {
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
