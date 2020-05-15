/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.powsybl.computation.*;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.zip.GZIPOutputStream;

import static com.powsybl.computation.slurm.CommandExecutionsTestFactory.*;
import static org.junit.Assert.*;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
@Ignore
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SlurmNormalExecutionTest extends AbstractIntegrationTests {

    @Override
    public void baseTest(SlurmComputationConfig slurmConfig, Supplier<AbstractExecutionHandler<String>> supplier, ComputationParameters parameters) {
        AbstractExecutionHandler<String> handler = supplier.get();
        ListAppender<ILoggingEvent> normalAppender = new ListAppender<>();
        addApprender(normalAppender);
        try (SlurmComputationManager computationManager = new SlurmComputationManager(slurmConfig)) {
            CompletableFuture<String> completableFuture = computationManager.execute(EMPTY_ENV, handler, parameters);
            System.out.println("to wait finish");
            String join = completableFuture.join();
            assertEquals("OK", join);
            assertIsCleaned(computationManager.getTaskStore());
            assertTrue(normalAppender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("Normal exit")));
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        } finally {
            removeApprender(normalAppender);
        }
        assertFalse(failed);
    }

    @Test
    public void testSimpleCmdWithCount() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractReturnOKExecutionHandler() {
            @Override
            public List<CommandExecution> before(Path path) {
                return simpleCmdWithCount(7);
            }
        };
        baseTest(supplier);
    }

    @Test
    public void testClean() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractReturnOKExecutionHandler() {
            @Override
            public List<CommandExecution> before(Path path) {
                return simpleCmdWithCount(7);
            }
        };
        baseTest(supplier, ComputationParameters.empty());
    }

    @Test
    public void testLongTask() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractReturnOKExecutionHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10);
            }
        };
        ComputationParameters parameters = new ComputationParametersBuilder().setTimeout("longProgram", 60).build();
        SlurmComputationParameters slurmComputationParameters = new SlurmComputationParameters(parameters, "itesla");
        parameters.addExtension(SlurmComputationParameters.class, slurmComputationParameters);
        baseTest(supplier, parameters);
    }

    @Test
    public void testMyEchoSimpleCmd() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractExecutionHandler<String>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                generateZipFileOnRemote("in0", workingDir.resolve("in0.zip"));
                generateZipFileOnRemote("in1", workingDir.resolve("in1.zip"));
                generateZipFileOnRemote("in2", workingDir.resolve("in2.zip"));
                return CommandExecutionsTestFactory.myEchoSimpleCmdWithUnzipZip(3);
            }

            @Override
            public String after(Path workingDir, ExecutionReport report) throws IOException {
                super.after(workingDir, report);
                Path out2 = workingDir.resolve("out2.gz");
                System.out.println("out2.gz should exists, actual exists:" + Files.exists(out2));
                if (Files.exists(out2)) {
                    return "OK";
                }
                return "KO";
            }
        };
        baseTest(supplier);
    }

    @Test
    public void testFilesWithSpaces() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractExecutionHandler<String>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                generateZipFileOnRemote("in 0", workingDir.resolve("in 0.zip"));
                generateZipFileOnRemote("in 1", workingDir.resolve("in 1.zip"));
                generateZipFileOnRemote("in 2", workingDir.resolve("in 2.zip"));
                return CommandExecutionsTestFactory.testFilesWithSpaces(3);
            }

            @Override
            public String after(Path workingDir, ExecutionReport report) throws IOException {
                super.after(workingDir, report);
                Path out2 = workingDir.resolve("out 2.gz");
                System.out.println("out 2.gz should exists, actual exists:" + Files.exists(out2));
                if (Files.exists(out2)) {
                    return "OK";
                }
                return "KO";
            }
        };
        baseTest(supplier);
    }

    @Test
    public void testGroupCmd() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractReturnOKExecutionHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return CommandExecutionsTestFactory.groupCmd();
            }
        };
        baseTest(supplier);
    }

    @Test
    public void testArgsWithSpace() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractReturnOKExecutionHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return CommandExecutionsTestFactory.argsWithSpaces(3);
            }

            @Override
            public String after(Path workingDir, ExecutionReport report) throws IOException {
                if (Files.exists(workingDir.resolve("line 1,line 2")) && Files.exists(workingDir.resolve("v2"))) {
                    return super.after(workingDir, report);
                } else {
                    failed = true;
                    return "KO";
                }
            }
        };
        baseTest(supplier);
    }

    @Test
    public void testTwoSimpleCmd() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractReturnOKExecutionHandler() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return CommandExecutionsTestFactory.twoSimpleCmd();
            }
        };
        baseTest(supplier);
    }

    @Test
    public void testFilesReadBytes() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractExecutionHandler<String>() {

            static final int COUNT = 10;

            @Override
            public List<CommandExecution> before(Path workingDir) {
                Command command = new SimpleCommandBuilder()
                        .id("echo")
                        .program("echo asdf >")
                        .arg(i -> i + ".txt")
                        .build();
                return Collections.singletonList(new CommandExecution(command, COUNT));
            }

            @Override
            public String after(Path workingDir, ExecutionReport report) throws IOException {
                IntStream.range(0, COUNT).forEach(i -> {
                    try {
                        System.out.println("i : " + i);
                        byte[] bytes = Files.readAllBytes(workingDir.resolve(i + ".txt"));
                        byte[] bytes1 = Files.readAllBytes(workingDir.resolve("echo_" + i + ".out"));
                        byte[] bytes2 = Files.readAllBytes(workingDir.resolve("echo_" + i + ".err"));
                        System.out.println(bytes.length);
                        System.out.println(bytes1.length);
                        System.out.println(bytes2.length);
                    } catch (IOException e) {
                        failed = true;
                    }
                });
                super.after(workingDir, report);
                return "OK";
            }
        };
        baseTest(supplier);
    }

    @Test
    public void testZMd5sumLargeFile() {
        Supplier<AbstractExecutionHandler<String>> supplier = () -> new AbstractExecutionHandler<String>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                long startDump = System.nanoTime();
                generateGzFileOnRemote(2, workingDir.resolve("2GFile.gz"));
                generateGzFileOnRemote(4, workingDir.resolve("4GFile.gz"));
                LOGGER.info("Dump two files in {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startDump));
                return md5sumLargeFile();
            }

            @Override
            public String after(Path workingDir, ExecutionReport report) throws IOException {
                List<String> actual2GMd5 = Files.readAllLines(workingDir.resolve("c1_0.out"));
                List<String> actual4GMd5 = Files.readAllLines(workingDir.resolve("c2_0.out"));
                String expected2GMd5 = "1ea9851f9b83e9bd50b8d7577b23e14b  2GFile";
                String expected4GMd5 = "bbe2b516d690f337d8f48fc03db99c9a  4GFile";
                if (!Objects.equals(actual2GMd5.get(0), expected2GMd5)) {
                    failed = true;
                    System.out.println("  actual:" + actual2GMd5.get(0));
                    System.out.println("expected:" + expected2GMd5);
                    return "KO";
                }
                if (!Objects.equals(actual4GMd5.get(0), expected4GMd5)) {
                    failed = true;
                    System.out.println("  actual:" + actual4GMd5.get(0));
                    System.out.println("expected:" + expected4GMd5);
                    return "KO";
                }
                return "OK";
            }
        };
        baseTest(supplier);
    }

    private void generateGzFileOnRemote(int sizeGb, Path dest) {
        StringBuilder sb = new StringBuilder();
        IntStream.range(0, 1024).forEach(i ->
                IntStream.range(0, 128).forEach(j -> sb.append("KKKKKKKK"))); // 1mb
        String oneMb = sb.toString();
        try (OutputStream outStream = new GZIPOutputStream(Files.newOutputStream(dest))) {
            IntStream.range(0, 1024 * sizeGb).forEach(i -> {
                try {
                    outStream.write(oneMb.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                    failed = true;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            failed = true;
        }
    }
}
