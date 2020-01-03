/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.powsybl.commons.io.WorkingDirectory;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class TaskStoreTest {

    private FileSystem fileSystem;
    private Path tmpPath;

    @Before
    public void setUp() {
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        tmpPath = fileSystem.getPath("/home/test/workingPath_12345");
    }
    @Test
    public void test() throws InterruptedException {
        TaskStore store = new TaskStore(1);
        CompletableFuture<String> cf = new CompletableFuture<>();
        // jimfs not support tmp dir
        WorkingDirectory workingDirectory = mock(WorkingDirectory.class);
        when(workingDirectory.toPath()).thenReturn(tmpPath);
        SlurmTask task = new SlurmTask(workingDirectory, Collections.emptyList(), cf);
        store.add(task);
        assertSame(task, store.getTask(cf).orElseThrow(RuntimeException::new));
        assertSame(cf, store.getCompletableFuture(task.getId()).orElseThrow(RuntimeException::new));
        assertFalse(store.isEmpty());
        cf.complete("done");
        TimeUnit.SECONDS.sleep(2);
        assertTrue(store.isEmpty());
    }
}
