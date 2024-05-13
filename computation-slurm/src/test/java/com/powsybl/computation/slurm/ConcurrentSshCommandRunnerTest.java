package com.powsybl.computation.slurm;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.pastdev.jsch.DefaultSessionFactory;
import com.pastdev.jsch.SessionFactory;
import com.pastdev.jsch.command.CommandRunner;
import com.powsybl.computation.Command;
import com.powsybl.computation.SimpleCommandBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Nicolas Rol {@literal <nicolas.rol at rte-france.com>}
 */
class ConcurrentSshCommandRunnerTest {

    private FileSystem fileSystem;
    private Path localDir;

    @Test
    void test() throws IOException, JSchException {
        SlurmComputationConfig.SshConfig sshConfig = new SlurmComputationConfig.SshConfig("localhost", 12345, "username", "password", 3, 5);
        SessionFactory sessionFactory = mock(DefaultSessionFactory.class);
        Session session = mock(Session.class);
        when(sessionFactory.newSession()).thenReturn(session);
        ChannelExec channelExec = mock(ChannelExec.class);
        when(channelExec.isClosed()).thenReturn(true);
        when(sessionFactory.newSession().openChannel("exec")).thenReturn(channelExec);
        try (CommandRunner runner = new ConcurrentSshCommandRunner(sessionFactory, sshConfig.getMaxSshConnection(), sshConfig.getMaxRetry());
             CommandExecutor commandRunner = new SshCommandExecutor(runner)) {
            Command command = new SimpleCommandBuilder()
                .id("simpleCmdId")
                .program("sleep")
                .args("10s")
                .build();
            CommandResult commandResult = commandRunner.execute(command.toString(0));
            assertEquals(0, commandResult.getExitCode());
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    void testException() throws JSchException, IOException {
        SlurmComputationConfig.SshConfig sshConfig = new SlurmComputationConfig.SshConfig("localhost", 12345, "username", "password", 3, 5);
        SessionFactory sessionFactory = mock(DefaultSessionFactory.class);
        Session session = mock(Session.class);
        when(sessionFactory.newSession()).thenReturn(session);
        when(sessionFactory.newSession().openChannel("exec")).thenThrow();
        try (CommandRunner runner = new ConcurrentSshCommandRunner(sessionFactory, sshConfig.getMaxSshConnection(), sshConfig.getMaxRetry());
             CommandExecutor commandRunner = new SshCommandExecutor(runner)) {
            Command command = new SimpleCommandBuilder()
                .id("simpleCmdId")
                .program("sleep")
                .args("10s")
                .build();
            String commandAsString = command.toString(0);
            SlurmException slurmException = assertThrows(SlurmException.class, () -> commandRunner.execute(commandAsString));
            assertEquals("Max retry reached", slurmException.getMessage());
        }
    }
}
