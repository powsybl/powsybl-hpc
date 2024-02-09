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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import static com.powsybl.computation.slurm.CommandExecutionsTestFactory.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
@Disabled
class SlurmInvalidExecutionTest extends AbstractIntegrationTests {
    static final Logger LOGGER = LoggerFactory.getLogger(SlurmInvalidExecutionTest.class);

    @Override
    void baseTest(SlurmComputationConfig slurmConfig, Supplier<AbstractExecutionHandler<String>> supplier, ComputationParameters parameters) {
        ListAppender<ILoggingEvent> testAppender = new ListAppender<>();
        addApprender(testAppender);
        try (SlurmComputationManager computationManager = new SlurmComputationManager(slurmConfig)) {
            CompletableFuture<String> completableFuture = computationManager.execute(EMPTY_ENV, supplier.get(), parameters);
            System.out.println("to wait finish");
            // As there are errors, the after() would throw exception(in this test)
            Assertions.assertThatThrownBy(completableFuture::join)
                    .isInstanceOf(CompletionException.class)
                    .hasMessageContaining("com.powsybl.commons.PowsyblException: Error during the execution in directory");
            assertIsCleaned(computationManager.getTaskStore());
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            fail();
        } finally {
            removeApprender(testAppender);
        }
        // assert on main thread
        assertFalse(failed);
    }

    @Test
    void testInvalidProgram() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractCheckErrorsExecutionHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return invalidProgram();
            }
        };
        baseTest(supplier);
    }

    @Test
    void testInvalidInBatch() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractCheckErrorsExecutionHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                generateZipFileOnRemote("in0", workingDir.resolve("in0.zip"));
                generateZipFileOnRemote("in1", workingDir.resolve("in1.zip"));
                generateZipFileOnRemote("in2", workingDir.resolve("in2.zip"));
                generateZipFileOnRemote("in3", workingDir.resolve("in3.zip"));
                return CommandExecutionsTestFactory.myEchoSimpleCmdWithUnzipZip(4);
            }
        };
        baseTest(supplier);
    }

    @Test
    void testInvalidProgramInList() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractCheckErrorsExecutionHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return invalidProgramInList();
            }
        };
        baseTest(supplier);
    }

    @Test
    void testInvalidProgramInGroup() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractCheckErrorsExecutionHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return invalidProgramInGroup();
            }
        };
        baseTest(supplier);
    }

    abstract static class AbstractCheckErrorsExecutionHandler extends AbstractExecutionHandler<String> {
        @Override
        public String after(Path workingDir, ExecutionReport report) throws IOException {
            System.out.println("Errors should exists, actual exists:" + !report.getErrors().isEmpty());
            if (report.getErrors().isEmpty()) {
                return "KO";
            }
            super.after(workingDir, report);
            return "OK";
        }
    }
}
