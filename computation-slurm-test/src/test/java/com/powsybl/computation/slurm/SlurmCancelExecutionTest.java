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
import com.powsybl.computation.ExecutionReport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.powsybl.computation.slurm.CommandExecutionsTestFactory.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
@Disabled("Slurm integration tests must be run locally.")
class SlurmCancelExecutionTest extends AbstractIntegrationTests {
    static final Logger LOGGER = LoggerFactory.getLogger(SlurmCancelExecutionTest.class);

    @Override
    void baseTest(SlurmComputationConfig config, Supplier<AbstractExecutionHandler<String>> supplier, ComputationParameters parameters) {
        try (SlurmComputationManager computationManager = new SlurmComputationManager(config)) {
            CompletableFuture<String> completableFuture = computationManager.execute(EMPTY_ENV, supplier.get(), parameters);
            System.out.println("CompletableFuture would be cancelled in 5 seconds...");
            // TODO add a test before submit
            Thread.sleep(5000);
            boolean cancel = completableFuture.cancel(true);
            System.out.println("Cancelled:" + cancel);
            assertTrue(cancel);
            assertIsCleaned(computationManager.getTaskStore());
            Assertions.assertThatThrownBy(completableFuture::join)
                    .isInstanceOf(CancellationException.class);
            // TODO detailed msg getCause is null
        } catch (IOException | InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
            failed = true;
        }
        // assert on main thread
        assertFalse(failed);
    }

    @Test
    void testLongProgramToCancel() {
        // Script configuration
        String program = String.format("%s/%s",
            moduleConfig.getOptionalStringProperty("program").orElse("No program configured"),
            "testToStop.sh");

        ListAppender<ILoggingEvent> testAppender = new ListAppender<>();
        addAppender(testAppender);
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractFailInAfterHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10, program);
            }

            @Override
            public String after(Path workingDir, ExecutionReport report) {
                failed = true;
                return "KO";
            }
        };
        try {
            baseTest(supplier);
            assertTrue(testAppender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("An exception occurred during execution of commands on slurm")));
        } finally {
            removeAppender(testAppender);
        }
    }

    @Test
    void testLongProgramInListToCancel() {
        ListAppender<ILoggingEvent> testAppender = new ListAppender<>();
        addAppender(testAppender);
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractFailInAfterHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgramInList();
            }
        };
        try {
            baseTest(supplier);
            assertTrue(testAppender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("An exception occurred during execution of commands on slurm")));
        } finally {
            removeAppender(testAppender);
        }
    }

    @Test
    void testMixedProgramsToCancel() {
        ListAppender<ILoggingEvent> testAppender = new ListAppender<>();
        addAppender(testAppender);
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractFailInAfterHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return mixedPrograms();
            }
        };
        try {
            baseTest(supplier);
            assertTrue(testAppender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("An exception occurred during execution of commands on slurm")));
        } finally {
            removeAppender(testAppender);
        }
    }

    abstract class AbstractFailInAfterHandler extends AbstractExecutionHandler<String> {
        @Override
        public String after(Path workingDir, ExecutionReport report) {
            System.out.println("------------SHOULD NOT EXECUTED------------");
            failed = true;
            return "KO";
        }
    }

}
