package com.powsybl.computation.slurm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Nicolas Rol {@literal <nicolas.rol at rte-france.com>}
 */
class SlurmCmdNonZeroExceptionTest {

    @Test
    void testMessage() {
        try {
            throw new SlurmCmdNonZeroException("test");
        } catch (SlurmCmdNonZeroException exception) {
            assertEquals("test", exception.getMessage());
        } catch (Exception exception) {
            fail();
        }
    }

    @Test
    void testCommandResult() {
        try {
            CommandResult commandResult = new CommandResult(0,
                "stdOut",
                "stdErr");
            throw new SlurmCmdNonZeroException(commandResult);
        } catch (SlurmCmdNonZeroException exception) {
            assertEquals("\nexitcode:0\nerr:stdErr\nout:stdOut", exception.getMessage());
        } catch (Exception exception) {
            fail();
        }
    }
}
