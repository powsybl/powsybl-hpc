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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.powsybl.computation.slurm.CommandExecutionsTestFactory.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
@Ignore
public class SlurmUnitTests {

    static final Logger LOGGER = LoggerFactory.getLogger(SlurmUnitTests.class);
    static final ExecutionEnvironment EMPTY_ENV = new ExecutionEnvironment(Collections.emptyMap(), "unit_test_", false);

    ModuleConfig config;
    SlurmComputationConfig slurmConfig;

    volatile boolean failed = false;

    @Before
    public void setup() {
        YamlModuleConfigRepository configRepository = new YamlModuleConfigRepository(Paths.get("src/test/resources/config.yml"));
        config = configRepository.getModuleConfig("slurm-computation-manager")
                .orElseThrow(() -> new RuntimeException("Config.yaml is not good. Please recheck the config.yaml.example"));
        slurmConfig = generateSlurmConfigWithShortScontrolTime(config);
        // TODO prepare myapps if necessary
    }

    static SlurmComputationConfig.SshConfig generateSsh(ModuleConfig config) {
        return new SlurmComputationConfig.SshConfig(config.getStringProperty("hostname"), 22, config.getStringProperty("username"), config.getStringProperty("password"), 10, 5);
    }

    static SlurmComputationConfig generateSlurmConfig(ModuleConfig config) {
        return new SlurmComputationConfig(generateSsh(config), config.getStringProperty("remote-dir"),
                Paths.get(config.getStringProperty("local-dir")), 5, 4);
    }

    /**
     * Returns a config in which scontrol monitor runs every minute
     */
    static SlurmComputationConfig generateSlurmConfigWithShortScontrolTime(ModuleConfig config) {
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
                if (testAttribute.getTestName().endsWith("CancelExternal")) {
                    System.out.println("Go to cancel on server");
                    completableFuture.join();
                    System.out.println("Canceled:" + completableFuture.isCancelled());
                } else {
                    System.out.println("to cancel");
                    Thread.sleep(5000);
                    boolean cancel = completableFuture.cancel(true);
                    System.out.println("Canceled:" + cancel);
                    Assert.assertTrue(cancel);
                }
            } else if (testAttribute.getType() == Type.TO_WAIT) {
                System.out.println("to wait finish");
                if (testAttribute.getTestName().equals("deadline")) {
                    try {
                        completableFuture.join();
                    } catch (CompletionException exception) {
                        // ignored
                    }
                } else {
                    completableFuture.join();
                }
            } else if (testAttribute.getType() == Type.TO_SHUTDOWN) {
                System.out.println("Go shutdown JVM");
                completableFuture.join();
            } else {
                throw new AssertionError(testAttribute.getType() + " is not valid.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            failed = true;
        }
        // assert on main thread
        assertFalse(failed);
    }

    @Test
    public void testLongProgramToCancel() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_CANCEL, "longProgramToCancel");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgramToCancel();
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
        };
        baseTest(testAttribute, supplier);
    }

    @Test
    public void testLongProgramToCancelExternal() throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_CANCEL, "longProgramToCancelExternal", true);
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(200);
            }
        };
        baseTest(testAttribute, supplier);
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
    public void testParameters() throws InterruptedException {
        String qos = "itesla"; //TODO the qos name should be configured
        testParameters(qos);
    }

    @Test
    public void testInvalidQos() {
        try {
            testParameters("THIS_QOS_SHOULD_NOT_EXIST_IN_SLURM");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Invalid qos specification"));
        }
    }

    private void testParameters(String qos) throws InterruptedException {
        TestAttribute testAttribute = new TestAttribute(Type.TO_WAIT, "qos");
        Supplier<AbstractExecutionHandler<Void>> supplier = () -> new AbstractExecutionHandler<Void>() {
            @Override
            public List<CommandExecution> before(Path workingDir) {
                return longProgram(10);
            }
        };
        ComputationParameters parameters = new ComputationParametersBuilder().setTimeout("longProgram", 60).build();
        SlurmComputationParameters slurmComputationParameters = new SlurmComputationParameters(parameters, qos);
        parameters.addExtension(SlurmComputationParameters.class, slurmComputationParameters);
        baseTest(testAttribute, supplier, parameters);
    }

    private void assertErrors(String testName, ExecutionReport report) {
        System.out.println("---------" + testName + "----------");
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

    private enum Type {
        TO_WAIT,
        TO_CANCEL,
        TO_SHUTDOWN
    }
}
