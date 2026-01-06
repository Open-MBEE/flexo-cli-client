package org.openmbee.flexo.cli.commands;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.security.Permission;

import static org.junit.jupiter.api.Assertions.*;

class RmCommandTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private SecurityManager originalSecurityManager;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        originalSecurityManager = System.getSecurityManager();
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setSecurityManager(originalSecurityManager);
    }

    @Test
    void testRunDisplaysNotImplementedMessage() {
        // Set up security manager to catch System.exit
        System.setSecurityManager(new NoExitSecurityManager());

        RmCommand command = new RmCommand();

        try {
            command.run();
            fail("Expected SecurityException from System.exit()");
        } catch (SecurityException e) {
            // Expected - System.exit(1) was called
            String output = outContent.toString();
            assertTrue(output.contains("not yet fully implemented"));
            assertTrue(output.contains("Planned usage"));
            assertTrue(output.contains("flexo rm"));
        }
    }

    @Test
    void testRunShowsUsageExamples() {
        System.setSecurityManager(new NoExitSecurityManager());

        RmCommand command = new RmCommand();

        try {
            command.run();
            fail("Expected SecurityException");
        } catch (SecurityException e) {
            String output = outContent.toString();
            assertTrue(output.contains("--iri"));
            assertTrue(output.contains("--pattern"));
            assertTrue(output.contains("--file"));
            assertTrue(output.contains("SPARQL"));
        }
    }

    // Security manager that prevents System.exit
    private static class NoExitSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission perm) {
            // Allow everything
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            // Allow everything
        }

        @Override
        public void checkExit(int status) {
            super.checkExit(status);
            throw new SecurityException("System.exit(" + status + ") called");
        }
    }
}
