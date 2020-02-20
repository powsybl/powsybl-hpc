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
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.powsybl.computation.slurm.CommandExecutionsTestFactory.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
@Ignore
public class SlurmCancelExecutionTest extends AbstractIntegrationTests {

    void baseTest(SlurmComputationConfig config, Supplier<AbstractExecutionHandler<String>> supplier, ComputationParameters parameters, boolean checkClean) {
        try (SlurmComputationManager computationManager = new SlurmComputationManager(config)) {
            CompletableFuture<String> completableFuture = computationManager.execute(EMPTY_ENV, supplier.get(), parameters);
            System.out.println("CompletableFuture would be cancelled in 5 seconds...");
            // TODO add a test before submit
            Thread.sleep(5000);
            boolean cancel = completableFuture.cancel(true);
            System.out.println("Cancelled:" + cancel);
            Assert.assertTrue(cancel);
            if (checkClean) {
                assertIsCleanedAfterWait(computationManager.getTaskStore());
            }
            Assertions.assertThatThrownBy(completableFuture::join)
                    .isInstanceOf(CancellationException.class);
            // TODO detailed msg getCause is null
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            failed = true;
        }
        // assert on main thread
        assertFalse(failed);
    }

    @Test
    public void testLongProgramToCancel() {
        ListAppender<ILoggingEvent> testAppender = new ListAppender<>();
        addApprender(testAppender);
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractFailInAfterHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10);
            }

            @Override
            public String after(Path workingDir, ExecutionReport report) {
                failed = true;
                return "KO";
            }
        };
        try {
            baseTest(supplier, true);
            assertTrue(testAppender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("Interrupted cancelled during await")));
        } finally {
            removeApprender(testAppender);
        }
    }

    @Test
    public void testLongProgramInListToCancel() {
        ListAppender<ILoggingEvent> testAppender = new ListAppender<>();
        addApprender(testAppender);
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractFailInAfterHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgramInList();
            }
        };
        try {
            baseTest(supplier, true);
            assertTrue(testAppender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("Interrupted cancelled during await")));
        } finally {
            removeApprender(testAppender);
        }
    }

    @Test
    public void testMixedProgramsToCancel() {
        ListAppender<ILoggingEvent> testAppender = new ListAppender<>();
        addApprender(testAppender);
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractFailInAfterHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return mixedPrograms();
            }
        };
        try {
            baseTest(supplier, true);
            assertTrue(testAppender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("Interrupted cancelled during")));
        } finally {
            removeApprender(testAppender);
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
