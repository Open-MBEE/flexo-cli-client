package org.openmbee.flexo.cli.commands;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class RmCommandTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
    }

    @Test
    void testRunDisplaysNotImplementedMessage() {
        RmCommand command = new RmCommand();

        // Command should throw CommandException with exit code 1
        CommandExecutionException exception = assertThrows(
            CommandExecutionException.class,
            () -> command.run()
        );

        assertEquals(1, exception.getExitCode());
        assertTrue(exception.getMessage().contains("not yet implemented"));

        String output = outContent.toString();
        assertTrue(output.contains("not yet fully implemented"));
        assertTrue(output.contains("Planned usage"));
        assertTrue(output.contains("flexo rm"));
    }

    @Test
    void testRunShowsUsageExamples() {
        RmCommand command = new RmCommand();

        assertThrows(CommandExecutionException.class, () -> command.run());

        String output = outContent.toString();
        assertTrue(output.contains("--iri"));
        assertTrue(output.contains("--pattern"));
        assertTrue(output.contains("--file"));
        assertTrue(output.contains("SPARQL"));
    }
}
