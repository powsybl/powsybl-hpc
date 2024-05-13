package com.powsybl.computation.slurm;

import com.powsybl.computation.ComputationParameters;
import com.powsybl.computation.ExecutionEnvironment;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.powsybl.computation.slurm.CommandExecutionsTestFactory.md5sumLargeFile;
import static com.powsybl.computation.slurm.CommandResultTestFactory.simpleOutput;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Nicolas Rol {@literal <nicolas.rol at rte-france.com>}
 */
class SlurmCmdTest extends DefaultSlurmTaskTest {

    @Test
    void testException() throws IOException {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        when(commandExecutor.execute(startsWith("sbatch")))
            .thenReturn(new CommandResult(-1, "stdOut", "stdErr"))
            .thenReturn(simpleOutput("Submitted batch job 2"))
            .thenReturn(simpleOutput("Submitted batch job 5"))
            .thenReturn(simpleOutput("Submitted batch job 6"));
        SlurmTask task = new JobArraySlurmTask(mockScm(commandExecutor), mockWd(), md5sumLargeFile(), ComputationParameters.empty(), ExecutionEnvironment.createDefault());
        try {
            task.submit();
            fail();
        } catch (SlurmException exception) {
            String message = """
                com.powsybl.computation.slurm.SlurmCmdNonZeroException:\s
                exitcode:-1
                err:stdErr
                out:stdOut""";
            assertEquals(message, exception.getMessage());
        }
    }
}
