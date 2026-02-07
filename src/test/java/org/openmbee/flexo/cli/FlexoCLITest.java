package org.openmbee.flexo.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmbee.flexo.cli.config.FlexoConfig;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class FlexoCLITest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
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
        // Initialize config first by setting the static field
        try {
            Field configField = FlexoCLI.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(null, new FlexoConfig());
        } catch (Exception e) {
            fail("Could not initialize config: " + e.getMessage());
        }
        
        FlexoConfig config = FlexoCLI.getConfig();
        assertNotNull(config);
    }

    @Test
    void testCommandLineExecution() {
        // Test using CommandLine.execute() instead of main() to avoid System.exit()
        FlexoCLI cli = new FlexoCLI();
        CommandLine commandLine = new CommandLine(cli);

        // Execute with --help flag should return exit code 0
        int exitCode = commandLine.execute("--help");
        assertEquals(0, exitCode);

        String output = outContent.toString();
        assertTrue(output.contains("Usage:") || output.contains("flexo"));
    }

    @Test
    void testCommandLineExecutionNoArgs() {
        // Test with no arguments - should execute run() method
        FlexoCLI cli = new FlexoCLI();
        CommandLine commandLine = new CommandLine(cli);

        int exitCode = commandLine.execute();
        assertEquals(0, exitCode);

        String output = outContent.toString();
        assertTrue(output.contains("Flexo MMS CLI"));
    }

    @Test
    void testConfigFieldInitialization() throws Exception {
        // Test that config can be initialized
        Field configField = FlexoCLI.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(null, new FlexoConfig());

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

    @Test
    void testGetRemoteName() throws Exception {
        FlexoCLI cli = new FlexoCLI();

        Field remoteNameField = FlexoCLI.class.getDeclaredField("remoteName");
        remoteNameField.setAccessible(true);
        remoteNameField.set(cli, "origin");

        assertEquals("origin", cli.getRemoteName());
    }

    @Test
    void testGetRemoteNameNull() {
        FlexoCLI cli = new FlexoCLI();
        assertNull(cli.getRemoteName());
    }
}
