package org.openmbee.flexo.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmbee.flexo.cli.config.FlexoConfig;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Permission;

import static org.junit.jupiter.api.Assertions.*;

class FlexoCLITest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private SecurityManager originalSecurityManager;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        originalSecurityManager = System.getSecurityManager();
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setSecurityManager(originalSecurityManager);
    }

    @Test
    void testRun() {
        FlexoCLI cli = new FlexoCLI();

        cli.run();

        String output = outContent.toString();
        assertTrue(output.contains("Flexo MMS CLI"));
        assertTrue(output.contains("Available commands"));
        assertTrue(output.contains("branch"));
        assertTrue(output.contains("pull"));
        assertTrue(output.contains("push"));
        assertTrue(output.contains("rm"));
        assertTrue(output.contains("merge"));
    }

    @Test
    void testGetOrgId() throws Exception {
        FlexoCLI cli = new FlexoCLI();

        Field orgIdField = FlexoCLI.class.getDeclaredField("orgId");
        orgIdField.setAccessible(true);
        orgIdField.set(cli, "test-org");

        assertEquals("test-org", cli.getOrgId());
    }

    @Test
    void testGetOrgIdNull() {
        FlexoCLI cli = new FlexoCLI();
        assertNull(cli.getOrgId());
    }

    @Test
    void testGetRepoId() throws Exception {
        FlexoCLI cli = new FlexoCLI();

        Field repoIdField = FlexoCLI.class.getDeclaredField("repoId");
        repoIdField.setAccessible(true);
        repoIdField.set(cli, "test-repo");

        assertEquals("test-repo", cli.getRepoId());
    }

    @Test
    void testGetRepoIdNull() {
        FlexoCLI cli = new FlexoCLI();
        assertNull(cli.getRepoId());
    }

    @Test
    void testIsVerbose() throws Exception {
        FlexoCLI cli = new FlexoCLI();

        Field verboseField = FlexoCLI.class.getDeclaredField("verbose");
        verboseField.setAccessible(true);
        verboseField.set(cli, true);

        assertTrue(cli.isVerbose());
    }

    @Test
    void testIsVerboseFalse() {
        FlexoCLI cli = new FlexoCLI();
        assertFalse(cli.isVerbose());
    }

    @Test
    void testIsNoColor() throws Exception {
        FlexoCLI cli = new FlexoCLI();

        Field noColorField = FlexoCLI.class.getDeclaredField("noColor");
        noColorField.setAccessible(true);
        noColorField.set(cli, true);

        assertTrue(cli.isNoColor());
    }

    @Test
    void testIsNoColorFalse() {
        FlexoCLI cli = new FlexoCLI();
        assertFalse(cli.isNoColor());
    }

    @Test
    void testGetConfig() {
        FlexoConfig config = FlexoCLI.getConfig();
        assertNotNull(config);
    }

    @Test
    void testMainMethod() {
        // Testing main() is tricky because it calls System.exit()
        // We'll use a security manager to catch the exit call
        System.setSecurityManager(new NoExitSecurityManager());

        try {
            FlexoCLI.main(new String[]{"--help"});
            fail("Expected SecurityException from System.exit()");
        } catch (SecurityException e) {
            // Expected - System.exit() was called
            assertTrue(e.getMessage().contains("System.exit"));
        }
    }

    @Test
    void testMainMethodNoArgs() {
        System.setSecurityManager(new NoExitSecurityManager());

        try {
            FlexoCLI.main(new String[]{});
            fail("Expected SecurityException");
        } catch (SecurityException e) {
            // When no subcommand is specified, it should call run() and exit with 0
            assertTrue(e.getMessage().contains("System.exit"));
        }
    }

    @Test
    void testConfigFieldInitialization() throws Exception {
        // Call main to initialize config
        System.setSecurityManager(new NoExitSecurityManager());

        try {
            FlexoCLI.main(new String[]{"--help"});
        } catch (SecurityException e) {
            // Expected
        }

        // Config should now be initialized
        FlexoConfig config = FlexoCLI.getConfig();
        assertNotNull(config);
    }

    @Test
    void testAllGetters() {
        FlexoCLI cli = new FlexoCLI();

        // Test all getters return without exception
        assertDoesNotThrow(() -> cli.getOrgId());
        assertDoesNotThrow(() -> cli.getRepoId());
        assertDoesNotThrow(() -> cli.isVerbose());
        assertDoesNotThrow(() -> cli.isNoColor());
    }

    @Test
    void testRunMultipleTimes() {
        FlexoCLI cli = new FlexoCLI();

        // Running multiple times should be safe
        cli.run();
        outContent.reset();
        cli.run();

        String output = outContent.toString();
        assertTrue(output.contains("Flexo MMS CLI"));
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
