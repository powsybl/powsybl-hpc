/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.commons.config.ModuleConfig;
import com.powsybl.commons.config.YamlModuleConfigRepository;
import com.powsybl.computation.*;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
@Ignore
public class SlurmUnitTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlurmUnitTests.class);
    private static final ExecutionEnvironment EMPTY_ENV = new ExecutionEnvironment(Collections.emptyMap(), "unit_test_", false);

    private static final String EXPECTED_ERROR_JOB_MSG = "An error job found";
    private static final String EXPECTED_SCANCEL_JOB_MSG = "A CANCELLED job detected by monitor";
    private static final String EXPECTED_DEADLINE_JOB_MSG = "A DEADLINE job detected by monitor";
    private static final String EXPECTED_SUBMITTER_CANCEL_MSG = "Cancelled by submitter";
    private static final String FAILED_SEP = "******** TEST FAILED ********";

    private ModuleConfig config;

    private volatile boolean failed = false;

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

    private void baseTest(TestAttribute testAttribute, Supplier<AbstractExecutionHandler<Void>> supplier) throws InterruptedException {
        baseTest(testAttribute, supplier, ComputationParameters.empty());
    }

    private void baseTest(TestAttribute testAttribute, Supplier<AbstractExecutionHandler<Void>> supplier, ComputationParameters parameters) throws InterruptedException {
        SlurmComputationConfig slurmConfig = testAttribute.isShortScontrolTime() ? generateSlurmConfigWithShortScontrolTime(config) : generateSlurmConfig(config);
        try (ComputationManager computationManager = new SlurmComputationManager(slurmConfig)) {
            CompletableFuture<Void> completableFuture = computationManager.execute(EMPTY_ENV, supplier.get(), parameters);
            if (testAttribute.getType() == Type.TO_CANCEL) {
                System.out.println("Will be cancelled by junit test in 5 seconds...");
                Thread.sleep(5000);
                boolean cancel = completableFuture.cancel(true);
                System.out.println("Cancelled:" + cancel);
                failed = !cancel;
                try {
                    completableFuture.join();
                    failed = true;
                } catch (CancellationException ce) {
                    boolean b = failed || !ce.getMessage().equals(EXPECTED_SUBMITTER_CANCEL_MSG);
                    failed = b;
                }
            } else if (testAttribute.getType() == Type.TO_WAIT) {
                assertToWaitTest(testAttribute, completableFuture);
            } else if (testAttribute.getType() == Type.TO_SHUTDOWN) {
                System.out.println("Go shutdown JVM");
                completableFuture.join();
            } else {
                throw new AssertionError(testAttribute.getType() + " is not valid.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (failed) {
            fail();
        }
    }

    private void assertToWaitTest(TestAttribute testAttribute, CompletableFuture completableFuture) {
        if (testAttribute.getTestName().endsWith("CancelExternal")) {
            System.out.println("Go to cancel on server...");
        } else {
            System.out.println("Wait finish...");
        }
        try {
            completableFuture.join();
            failed = false;
        } catch (CompletionException e) {
            if (testAttribute.getTestName().equals("qos")) {
                failed = !e.getMessage().contains("Invalid qos specification");
                return;
            }
            String expected = "no exception";
            if (testAttribute.getTestName().contains("invalid")) {
                expected = EXPECTED_ERROR_JOB_MSG;
            } else if (testAttribute.getTestName().endsWith("CancelExternal")) {
                expected = EXPECTED_SCANCEL_JOB_MSG;
            } else if (testAttribute.getTestName().contains("deadline")) {
                expected = EXPECTED_DEADLINE_JOB_MSG;
            } else {
                // normal tests
                failed = true;
            }
            if (!e.getMessage().equals(expected)) {
                failed = true;
                System.out.println(FAILED_SEP);
                System.out.println("Actuel:" + e.getMessage());
                System.out.println("Expect:" + expected);
            }
        }
    }

    @Test
    public void testSimpleCmdWithCount() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_WAIT, "simpleCmdWithCount");
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
    public void testLongTask() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_WAIT, "longTask");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10);
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testMyEchoSimpleCmd() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_WAIT, "myEchoSimpleCmdWithUnzipZip");
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
                        failed = true;
                }
                Path out2 = workingDir.resolve("out2.gz");
                System.out.println("---------" + testAttribute.testName + "----------");
                System.out.println("out2.gz should exists:" + Files.exists(out2));
                failed = !Files.exists(out2);
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
    public void testGroupCmd() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_WAIT, "groupCmd");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return CommandExecutionsTestFactory.groupCmd();
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testTwoSimpleCmd() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_WAIT, "twoSimpleCmd");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return twoSimpleCmd();
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testInvalidProgram() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_WAIT, "invalidProgram");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return invalidProgram();
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) {
                assertErrors(testAttribute.testName, report);
                return null;
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testInvalidProgramInGroup() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_WAIT, "invalidProgramInGroup");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return invalidProgramInGroup();
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) {
                assertErrors(testAttribute.testName, report);
                return null;
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testInvalidProgramInList() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_WAIT, "invalidProgramInList");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return invalidProgramInList();
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) {
                assertErrors(testAttribute.testName, report);
                return null;
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testLongProgramToCancel() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_CANCEL, "longProgramToCancel");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgramToCancel();
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) throws IOException {
                failed = true;
                return null;
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testLongListProgsToCancel() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_CANCEL, "longListProgsToCancel");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longListProgsToCancel();
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) throws IOException {
                failed = true;
                return null;
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testMixedProgsToCancel() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_CANCEL, "mixedProgsToCancel");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return mixedProgsToCancel();
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) throws IOException {
                failed = true;
                return null;
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testLongProgramToCancelExternal() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_WAIT, "longProgramToCancelExternal", true);
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(200);
            }

            @Override
            public Void after(Path workingDir, ExecutionReport report) throws IOException {
                return null;
            }
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testFilesReadBytes() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_WAIT, "filesReadBytes");
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
                        System.out.println("i : " + i);
                        byte[] bytes = Files.readAllBytes(workingDir.resolve(i + ".txt"));
                        byte[] bytes1 = Files.readAllBytes(workingDir.resolve("echo_" + i + ".out"));
                        byte[] bytes2 = Files.readAllBytes(workingDir.resolve("echo_" + i + ".err"));
                        System.out.println(bytes.length);
                        System.out.println(bytes1.length);
                        System.out.println(bytes2.length);
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
    public void testMd5sumLargeFile() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_WAIT, "md5sumLargeFile");
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
                    failed = true;
                    System.out.println("  actuel:" + actual2GMd5.get(0));
                    System.out.println("expected:" + expected2GMd5);
                }
                if (!Objects.equals(actual4GMd5.get(0), expected4GMd5)) {
                    failed = true;
                    System.out.println("  actuel:" + actual4GMd5.get(0));
                    System.out.println("expected:" + expected4GMd5);
                }
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
    public void makeSlurmBusy() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_WAIT, "deadline");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return CommandExecutionsTestFactory.makeSlurmBusy();
            }
        };
        baseTest(testAttribute, supplier);
    }

    // 1. Make slurm busy if necessary
    // 2. Wait 1 mins
    @Test
    public void testDeadline() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_WAIT, "deadline", true);
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10);
            }
        };
        ComputationParametersBuilder builder = new ComputationParametersBuilder();
        builder.setDeadline("longProgram", 12);

        baseTest(testAttribute, supplier, builder.build());
    }

    // sacctmgr show qos
    @Test
    public void testQos() throws InterruptedException {
        String qos = "itesla"; //TODO the qos name should be configured
        testQos(qos);
    }

    @Test
    public void testInvalidQos() {
        try {
            testQos("THIS_QOS_SHOULD_NOT_EXIST_IN_SLURM");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Invalid qos specification"));
        }
    }

    private void testQos(String qos) throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_WAIT, "qos");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10);
            }
        };
        ComputationParameters parameters = ComputationParameters.empty();
        SlurmComputationParameters slurmComputationParameters = new SlurmComputationParameters(parameters, qos);
        parameters.addExtension(SlurmComputationParameters.class, slurmComputationParameters);
        baseTest(testAttribute, supplier, parameters);
    }

    private void assertErrors(String testName, ExecutionReport report) {
        System.out.println("---------" + testName + "----------");
        System.out.println("Errors should exists:" + !report.getErrors().isEmpty());
        if (report.getErrors().isEmpty()) {
            failed = true;
        }
        System.out.println("------------------------------------");
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
        TO_WAIT,
        TO_CANCEL,
        TO_SHUTDOWN
    }
}
