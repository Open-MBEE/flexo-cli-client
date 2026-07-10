package org.openmbee.flexo.cli.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SquashCommandTest {

    @Test
    void testConstructor() {
        SquashCommand command = new SquashCommand();
        assertNotNull(command);
    }

    // Note: Full integration testing of commands requires static mocking of
    // FlexoCLI.getConfig(), System.exit() handling, and HTTP client mocking.
    // This test provides basic construction coverage.
}
