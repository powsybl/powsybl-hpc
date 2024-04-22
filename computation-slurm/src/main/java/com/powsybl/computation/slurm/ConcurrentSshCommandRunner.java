/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.pastdev.jsch.SessionFactory;
import com.pastdev.jsch.command.CommandRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
 */
class ConcurrentSshCommandRunner extends CommandRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentSshCommandRunner.class);

    private final Semaphore lock;
    private final int maxRetries;

    ConcurrentSshCommandRunner(SessionFactory factory, int maxSshConnections, int maxRetries) {
        super(factory);
        this.lock = new Semaphore(maxSshConnections - 2);
        this.maxRetries = maxRetries;
        LOGGER.trace("init ConcurrentCommandRunner");
    }

    private void sleepSeconds(int i) {
        if (i == 0) {
            return;
        }
        try {
            TimeUnit.SECONDS.sleep(i);
        } catch (InterruptedException e) {
            LOGGER.warn(e.toString(), e);
            Thread.currentThread().interrupt();
        }
    }

    private synchronized Session getSession() throws JSchException {
        return sessionManager.getSession();
    }

    @Override
    public CommandRunner.ChannelExecWrapper open(String command) throws JSchException {
        return getNonNullableChannel(command, null, null);
    }

    @Override
    public CommandRunner.ExecuteResult execute(String command) throws JSchException {

        ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
        ByteArrayOutputStream stdOut = new ByteArrayOutputStream();

        CommandRunner.ChannelExecWrapper channel = getNonNullableChannel(command, stdOut, stdErr);
        int exitCode = channel.close();

        return new CommandRunner.ExecuteResult(exitCode,
            stdOut.toString(UTF8),
            stdErr.toString(UTF8));
    }

    private CommandRunner.ChannelExecWrapper getNonNullableChannel(String command, OutputStream stdOut, OutputStream stdErr) {
        CommandRunner.ChannelExecWrapper channelExecWrapper = null;
        int tried = 0;
        while (channelExecWrapper == null) {
            try {
                sleepSeconds(tried);
                if (tried != 0) {
                    LOGGER.warn("Retried {} for command: {}", tried, command);
                }
                LOGGER.trace("Trying to acquire SSH lock, available permits = {}", lock.availablePermits());
                lock.acquireUninterruptibly();
                LOGGER.trace("SSH lock acquired, available permits = {}. Trying to create new SSH exec channel.", lock.availablePermits());
                //allows to acquire a new session in case previously used one was down
                Session session = getSession();
                channelExecWrapper = new AutoReleaseLockChannelWrapper(session, command, null, stdOut, stdErr);
            } catch (Exception e) {
                lock.release();
                LOGGER.trace("SSH lock released on exception, available permits = {}", lock.availablePermits());
                LOGGER.warn(e.toString() + " for " + command, e);
                if (tried == maxRetries) {
                    throw new SlurmException("Max retry reached");
                }
                tried++;
            }
        }
        return channelExecWrapper;
    }

    /**
     * Wraps a channel so that it releases a lock when closed.
     */
    private class AutoReleaseLockChannelWrapper extends CommandRunner.ChannelExecWrapper {

        private boolean closed;
        private int exitCode;

        AutoReleaseLockChannelWrapper(Session session, String command, InputStream stdIn, OutputStream stdOut, OutputStream stdErr) throws IOException, JSchException {
            super(session, command, stdIn, stdOut, stdErr);
            this.closed = false;
        }

        @Override
        public int close() {
            //close must be idem potent: check if channel has already been closed and released
            if (!closed) {
                exitCode = super.close();
                closed = true;
                lock.release();
                LOGGER.trace("SSH lock released on channel close, available permits = {}", lock.availablePermits());
            }
            return exitCode;
        }
    }
}
