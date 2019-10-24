/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.config.ModuleConfig;
import com.powsybl.commons.config.YamlModuleConfigRepository;
import com.powsybl.computation.*;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
@Ignore
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SlurmUnitTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlurmUnitTests.class);
    private static final ExecutionEnvironment EMPTY_ENV = new ExecutionEnvironment(Collections.emptyMap(), "unit_test_", false);

    private static final String QOS = "itesla"; //TODO the qos name should be configured
    private static final String EXPECTED_ERROR_JOB_MSG = "An error job found";

    private ModuleConfig config;

    private static final PowsyblException EXPECTED_EXCEPTION = new PowsyblException(EXPECTED_ERROR_JOB_MSG);

    @Before
    public void setup() {
        YamlModuleConfigRepository configRepository = new YamlModuleConfigRepository(Paths.get("src/test/resources/config.yml"));
        config = configRepository.getModuleConfig("slurm-computation-manager")
                .orElseThrow(() -> new RuntimeException("Config.yaml is not good. Please recheck the config.yaml.example"));
        // TODO prepare myapps if necessary
    }

    static SlurmComputationConfig.SshConfig generateSsh(ModuleConfig config) {
        return new SlurmComputationConfig.SshConfig(config.getStringProperty("hostname"), 22, config.getStringProperty("username"), config.getStringProperty("password"), 10, 5);
    }

    private static SlurmComputationConfig generateSlurmConfig(ModuleConfig config) {
        return new SlurmComputationConfig(generateSsh(config), config.getStringProperty("remote-dir"),
                Paths.get(config.getStringProperty("local-dir")), 5, 4);
    }

    /**
     * Returns a config in which scontrol monitor runs every minute
     */
    private static SlurmComputationConfig generateSlurmConfigWithShortScontrolTime(ModuleConfig config) {
        return new SlurmComputationConfig(generateSsh(config), config.getStringProperty("remote-dir"),
                Paths.get(config.getStringProperty("local-dir")), 5, 1);
    }

    private void baseTest(TestAttribute testAttribute, Supplier<AbstractExecutionHandler<Void>> supplier) {
        baseTest(testAttribute, supplier, ComputationParameters.empty());
    }

    private void baseTest(TestAttribute testAttribute, Supplier<AbstractExecutionHandler<Void>> supplier, ComputationParameters parameters) {
        SlurmComputationConfig slurmConfig = testAttribute.isShortScontrolTime() ? generateSlurmConfigWithShortScontrolTime(config) : generateSlurmConfig(config);
        try (SlurmComputationManager computationManager = new SlurmComputationManager(slurmConfig)) {
            CompletableFuture<Void> completableFuture = computationManager.execute(EMPTY_ENV, supplier.get(), parameters);
            if (testAttribute.getType() == Type.CANCEL) {
                assertCancel(testAttribute, completableFuture);
            } else if (testAttribute.getType() == Type.JOIN_THROW_EXCEPTION) {
                assertThrowException(testAttribute, completableFuture);
            } else if (testAttribute.getType() == Type.WAIT_JOIN_NORMAL) {
                assertWaitJoinNormal(testAttribute, completableFuture);
            } else if (testAttribute.getType() == Type.SHUTDOWN) {
                System.out.println("Go shutdown JVM");
                completableFuture.join();
            } else {
                throw new AssertionError(testAttribute.getType() + " is not valid.");
            }
            assertTaskStoreCleaned(computationManager);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    private void assertTaskStoreCleaned(SlurmComputationManager computationManager) {
        assertTrue(computationManager.getTaskStore().isCleaned());
    }

    private void assertCancel(TestAttribute testAttribute, CompletableFuture completableFuture) throws InterruptedException {
        LOGGER.debug("Will be cancelled by junit test in 3 seconds...");
        TimeUnit.SECONDS.sleep(3);
        boolean cancel = completableFuture.cancel(true);
        LOGGER.debug("Cancel invoked return:" + cancel);
        try {
            completableFuture.join();
            fail();
        } catch (CancellationException ce) {
            assertTrue(completableFuture.isCancelled());
        } catch (Exception e) {
            fail();
        }
        System.out.println("----------Do not close SCM immediately in cancel test-----------");
        TimeUnit.SECONDS.sleep(10);
    }

    private void assertThrowException(TestAttribute testAttribute, CompletableFuture completableFuture) {
        if (testAttribute.getTestName().endsWith("CancelExternal")) {
            System.out.println("Go to cancel on server...");
        } else {
            System.out.println("Wait finish...");
        }
        try {
            completableFuture.join();
            fail();
        } catch (CompletionException e) {
            if (testAttribute.getTestName().endsWith("CancelExternal")) {
                assertTrue(e.getCause().getMessage().contains("is CANCELLED"));
                // it is not cancelled by submitter
                assertFalse(completableFuture.isCancelled());
            } else if (testAttribute.getTestName().equals("missing_qos")) {
                assertTrue(e.getCause() instanceof SlurmException);
                assertTrue(e.getCause().getMessage().contains("Invalid qos specification"));
                assertTrue(e.getCause().getCause() instanceof SlurmCmdNonZeroException);
            } else if (testAttribute.getTestName().contains("deadline")) {
                assertTrue(e.getCause() instanceof SlurmException);
            } else if (testAttribute.getTestName().contains("invalid")) {
                assertSame(EXPECTED_EXCEPTION, e.getCause());
            } else {
                System.out.println(testAttribute.getTestName() + " normal tests");
                fail();
            }
        } catch (Exception e) {
            fail();
        }
    }

    private void assertWaitJoinNormal(TestAttribute testAttribute, CompletableFuture completableFuture) {
        try {
            completableFuture.join();
        } catch (Exception e) {
            fail();
        }
    }

    // ----------------WAIT_JOIN_NORMAL-------------
    @Test
    public void testSimpleCmdWithCount() {
        TestAttribute testAttribute = new TestAttribute(Type.WAIT_JOIN_NORMAL, "simpleCmdWithCount");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path path) {
                return simpleCmdWithCount(7);
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) {
                return null;
            }

            private List<CommandExecution> simpleCmdWithCount(int count) {
                return CommandExecutionsTestFactory.simpleCmdWithCount(count);
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testLongTask() {
        TestAttribute testAttribute = new TestAttribute(Type.WAIT_JOIN_NORMAL, "longTask");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10);
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testMyEchoSimpleCmd() {
        TestAttribute testAttribute = new TestAttribute(Type.WAIT_JOIN_NORMAL, "myEchoSimpleCmdWithUnzipZip");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                generateZipFileOnRemote("in0", workingDir.resolve("in0.zip"));
                generateZipFileOnRemote("in1", workingDir.resolve("in1.zip"));
                generateZipFileOnRemote("in2", workingDir.resolve("in2.zip"));
                return CommandExecutionsTestFactory.myEchoSimpleCmdWithUnzipZip(3);
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) {
                try {
                    super.after(workingDir, report);
                } catch (IOException e) {
                    fail();
                }
                Path out2 = workingDir.resolve("out2.gz");
                System.out.println("---------" + testAttribute.testName + "----------");
                System.out.println("out2.gz should exists:" + Files.exists(out2));
                assertTrue(Files.exists(out2));
                System.out.println("------------------------------------");
                return null;
            }
        };
        baseTest(testAttribute, supplier);
    }

    private static void generateZipFileOnRemote(String name, Path dest) {
        try (InputStream inputStream = SlurmUnitTests.class.getResourceAsStream("/afile.txt");
             ZipArchiveOutputStream zos = new ZipArchiveOutputStream(Files.newOutputStream(dest))) {
            ZipArchiveEntry entry = new ZipArchiveEntry(name);
            zos.putArchiveEntry(entry);
            IOUtils.copy(inputStream, zos);
            zos.closeArchiveEntry();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testGroupCmd() {
        TestAttribute testAttribute = new TestAttribute(Type.WAIT_JOIN_NORMAL, "groupCmd");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return CommandExecutionsTestFactory.groupCmd();
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testTwoSimpleCmd() {
        TestAttribute testAttribute = new TestAttribute(Type.WAIT_JOIN_NORMAL, "twoSimpleCmd");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return twoSimpleCmd();
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testFilesReadBytes() {
        TestAttribute testAttribute = new TestAttribute(Type.WAIT_JOIN_NORMAL, "filesReadBytes");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {

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
            public Void after(Path workingDir, ExecutionReport report) throws IOException {
                IntStream.range(0, COUNT).forEach(i -> {
                    try {
                        byte[] bytes = Files.readAllBytes(workingDir.resolve(i + ".txt"));
                        byte[] bytes1 = Files.readAllBytes(workingDir.resolve("echo_" + i + ".out"));
                        byte[] bytes2 = Files.readAllBytes(workingDir.resolve("echo_" + i + ".err"));
                        assertEquals(5, bytes.length);
                        assertEquals(0, bytes1.length);
                        assertEquals(0, bytes2.length);
                    } catch (IOException e) {
                        fail();
                    }
                });
                return super.after(workingDir, report);
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testzMd5sumLargeFile() {
        TestAttribute testAttribute = new TestAttribute(Type.WAIT_JOIN_NORMAL, "md5sumLargeFile");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                long startDump = System.nanoTime();
                generateGzFileOnRemote(2, workingDir.resolve("2GFile.gz"));
                generateGzFileOnRemote(4, workingDir.resolve("4GFile.gz"));
                LOGGER.info("Dump two files in {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startDump));
                return md5sumLargeFile();
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) throws IOException {
                List<String> actual2GMd5 = Files.readAllLines(workingDir.resolve("c1_0.out"));
                List<String> actual4GMd5 = Files.readAllLines(workingDir.resolve("c2_0.out"));
                String expected2GMd5 = "1ea9851f9b83e9bd50b8d7577b23e14b  2GFile";
                String expected4GMd5 = "bbe2b516d690f337d8f48fc03db99c9a  4GFile";
                if (!Objects.equals(actual2GMd5.get(0), expected2GMd5)) {
                    System.out.println("  actual:" + actual2GMd5.get(0));
                    System.out.println("expected:" + expected2GMd5);
                    fail();
                }
                if (!Objects.equals(actual4GMd5.get(0), expected4GMd5)) {
                    System.out.println("  actual:" + actual4GMd5.get(0));
                    System.out.println("expected:" + expected4GMd5);
                    fail();
                }
                return null;
            }
        };
        baseTest(testAttribute, supplier);
    }

    // -----------------------JOIN_THROW_EXCEPTION-----------------
    @Test
    public void testInvalidProgram() {
        TestAttribute testAttribute = new TestAttribute(Type.JOIN_THROW_EXCEPTION, "invalidProgram");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return invalidProgram();
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) {
                assertErrorsThenThrowPowsyblException(testAttribute.testName, report);
                return null;
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testInvalidProgramInGroup() {
        TestAttribute testAttribute = new TestAttribute(Type.JOIN_THROW_EXCEPTION, "invalidProgramInGroup");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return invalidProgramInGroup();
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) {
                assertErrorsThenThrowPowsyblException(testAttribute.testName, report);
                return null;
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testInvalidProgramInList() {
        TestAttribute testAttribute = new TestAttribute(Type.JOIN_THROW_EXCEPTION, "invalidProgramInList");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return invalidProgramInList();
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) {
                assertErrorsThenThrowPowsyblException(testAttribute.testName, report);
                return null;
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testzzLongProgramToCancelExternal() {
        TestAttribute testAttribute = new TestAttribute(Type.JOIN_THROW_EXCEPTION, "longProgramToCancelExternal", true);
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(200);
            }
        };
        baseTest(testAttribute, supplier);
    }

    // -------------------CANCEL-------------
    @Test
    public void testALongProgramToCancel() {
        TestAttribute testAttribute = new TestAttribute(Type.CANCEL, "longProgramToCancel");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgramToCancel();
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) throws IOException {
                fail();
                return null;
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testALongListProgsToCancel() {
        TestAttribute testAttribute = new TestAttribute(Type.CANCEL, "longListProgsToCancel");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longListProgsToCancel();
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) throws IOException {
                fail();
                return null;
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testAMixedProgsToCancel() {
        TestAttribute testAttribute = new TestAttribute(Type.CANCEL, "mixedProgsToCancel");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return mixedProgsToCancel();
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) throws IOException {
                fail();
                return null;
            }
        };
        baseTest(testAttribute, supplier);
    }

    private static void generateGzFileOnRemote(int sizeGb, Path dest) {
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
                    fail();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testyDeadline() {
        TestAttribute testAttribute = new TestAttribute(Type.JOIN_THROW_EXCEPTION, "deadline", true);
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return CommandExecutionsTestFactory.longProgram(60);
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) throws IOException {
                // do nothing
                return null;
            }
        };
        ComputationParametersBuilder builder = new ComputationParametersBuilder();
        builder.setDeadline("longProgram", 12);

        baseTest(testAttribute, supplier, builder.build());
    }

    // sacctmgr show qos
    @Test
    public void testParameters() {
        testParameters(QOS);
    }

    @Test
    public void testInvalidQos() {
        testParameters("THIS_QOS_SHOULD_NOT_EXIST_IN_SLURM");
    }

    private void testParameters(String qos) {
        TestAttribute testAttribute;
        if (Objects.equals(qos, QOS)) {
            testAttribute = new TestAttribute(Type.WAIT_JOIN_NORMAL, "qos");
        } else {
            testAttribute = new TestAttribute(Type.JOIN_THROW_EXCEPTION, "missing_qos");
        }
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return CommandExecutionsTestFactory.longProgram(10);
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) {
                // do not throw exception here to test slurm exception
                return null;
            }
        };
        ComputationParameters parameters = new ComputationParametersBuilder().setTimeout("longProgram", 60).build();
        SlurmComputationParameters slurmComputationParameters = new SlurmComputationParameters(parameters, qos);
        parameters.addExtension(SlurmComputationParameters.class, slurmComputationParameters);
        baseTest(testAttribute, supplier, parameters);
    }

    private void assertErrorsThenThrowPowsyblException(String testName, ExecutionReport report) {
        System.out.println("---------" + testName + "----------");
        System.out.println("Errors should exists:" + !report.getErrors().isEmpty());
        assertFalse(report.getErrors().isEmpty());
        System.out.println("------------------------------------");
        throw EXPECTED_EXCEPTION;
    }

    private static class TestAttribute {

        private final Type type;
        private final String testName;
        private final boolean shortScontrolTime;

        TestAttribute(Type type, String testName) {
            this(type, testName, false);
        }

        TestAttribute(Type type, String testName, boolean shortScontrolTime) {
            this.type = type;
            this.testName = testName;
            this.shortScontrolTime = shortScontrolTime;
        }

        Type getType() {
            return type;
        }

        String getTestName() {
            return testName;
        }

        boolean isShortScontrolTime() {
            return shortScontrolTime;
        }
    }

    static List<CommandExecution> twoSimpleCmd() {
        Command command1 = new SimpleCommandBuilder()
                .id("simpleCmdId")
                .program("sleep")
                .args("10s")
                .build();
        Command command2 = new SimpleCommandBuilder()
                .id("cmd2")
                .program("echo")
                .args("hello", ">", "output")
                .build();
        return Arrays.asList(new CommandExecution(command1, 1), new CommandExecution(command2, 1));
    }

    private static List<CommandExecution> invalidProgram() {
        Command cmd = new SimpleCommandBuilder()
                .id("invalidProgram")
                .program("echoo")
                .args("hello")
                .build();
        return Collections.singletonList(new CommandExecution(cmd, 1));
    }

    private static List<CommandExecution> invalidProgramInGroup() {
        Command command = new GroupCommandBuilder()
                .id("groupCmdId")
                .subCommand()
                .program("echoo")
                .args("hello")
                .add()
                .subCommand()
                .program("echo")
                .args("sub2")
                .add()
                .build();
        return Collections.singletonList(new CommandExecution(command, 1));
    }

    private static List<CommandExecution> invalidProgramInList() {
        return Arrays.asList(invalidProgram().get(0), CommandExecutionsTestFactory.simpleCmd().get(0));
    }

    private static List<CommandExecution> longProgram(int seconds) {
        Command command = new SimpleCommandBuilder()
                .id("longProgram")
                .program("sleep")
                .args(seconds + "s")
                .build();
        return Collections.singletonList(new CommandExecution(command, 1));
    }

    private static List<CommandExecution> longProgramToCancel() {
        return longProgram(10);
    }

    private static List<CommandExecution> longListProgsToCancel() {
        Command command1 = new SimpleCommandBuilder()
                .id("tLP")
                .program("sleep")
                .args("10s")
                .build();
        Command command2 = new SimpleCommandBuilder()
                .id("tLP2")
                .program("sleep")
                .args("10s")
                .build();
        Command command3 = new SimpleCommandBuilder()
                .id("tLP3")
                .program("sleep")
                .args("10s")
                .build();
        return Arrays.asList(new CommandExecution(command1, 1),
                new CommandExecution(command2, 1),
                new CommandExecution(command3, 1));
    }

    private static List<CommandExecution> mixedProgsToCancel() {
        Command command1 = new SimpleCommandBuilder()
                .id("tLP")
                .program("sleep")
                .args("10s")
                .build();
        Command command2 = new SimpleCommandBuilder()
                .id("tLP2")
                .program("sleep")
                .args("10s")
                .build();
        Command command3 = new SimpleCommandBuilder()
                .id("tLP3")
                .program("sleep")
                .args("10s")
                .build();
        return Arrays.asList(new CommandExecution(command1, 1),
                new CommandExecution(command2, 5),
                new CommandExecution(command3, 2));
    }

    private static List<CommandExecution> md5sumLargeFile() {
        Command command1 = new SimpleCommandBuilder()
                .id("c1")
                .program("md5sum")
                .inputFiles(new InputFile("2GFile.gz", FilePreProcessor.FILE_GUNZIP))
                .args("2GFile")
                .build();
        Command command2 = new SimpleCommandBuilder()
                .id("c2")
                .inputFiles(new InputFile("4GFile.gz", FilePreProcessor.FILE_GUNZIP))
                .program("md5sum")
                .args("4GFile")
                .build();
        return Arrays.asList(new CommandExecution(command1, 3),
                new CommandExecution(command2, 1));
    }

    private enum Type {
        WAIT_JOIN_NORMAL,
        JOIN_THROW_EXCEPTION,
        CANCEL,
        SHUTDOWN
    }
}
