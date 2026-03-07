package org.openmbee.flexo.cli.commands;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmbee.flexo.cli.FlexoCLI;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.openmbee.flexo.cli.model.Remote;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class RemoteCommandTest {

    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private FlexoConfig testConfig;

    @BeforeEach
    void setUp() throws Exception {
        originalOut = System.out;
        originalErr = System.err;
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        System.setErr(new PrintStream(outputStream));

        testConfig = new FlexoConfig(false);
        FlexoCLI.setConfig(testConfig);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            throw e;
        }
    }

    @Test
    void testRemoteCommand_RunListsRemotes() throws Exception {
        RemoteCommand command = new RemoteCommand();
        command.run();
        String output = outputStream.toString();
        assertTrue(output.contains("No remotes configured") || output.contains("Configured remotes"));
    }

    @Test
    void testListCommand_NoRemotes() throws Exception {
        RemoteCommand.ListCommand command = new RemoteCommand.ListCommand();
        command.run();
        String output = outputStream.toString();
        assertTrue(output.contains("No remotes configured"));
    }

    @Test
    void testListCommand_WithRemotes() throws Exception {
        Remote remote = new Remote("origin", "http://localhost:8080");
        remote.setLocalMode("true");
        testConfig.setRemote(remote);

        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        RemoteCommand.ListCommand command = new RemoteCommand.ListCommand();
        command.run();
        String output = outputStream.toString();
        assertTrue(output.contains("origin"));
    }

    @Test
    void testListCommand_WithDefaultRemote() throws Exception {
        Remote remote = new Remote("origin", "http://localhost:8080");
        testConfig.setRemote(remote);
        testConfig.setDefaultRemote("origin");

        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        RemoteCommand.ListCommand command = new RemoteCommand.ListCommand();
        command.run();
        String output = outputStream.toString();
        assertTrue(output.contains("*"));
    }

    @Test
    void testAddCommand_NewRemote() throws Exception {
        RemoteCommand.AddCommand command = new RemoteCommand.AddCommand();
        setField(command, "name", "test-remote");
        setField(command, "url", "http://test.com");

        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        command.run();

        assertTrue(testConfig.hasRemote("test-remote"));
        assertEquals("http://test.com", testConfig.getRemote("test-remote").getUrl());
    }

    @Test
    void testAddCommand_WithLocalMode() throws Exception {
        RemoteCommand.AddCommand command = new RemoteCommand.AddCommand();
        setField(command, "name", "local-remote");
        setField(command, "url", "http://localhost:8080");
        setField(command, "localMode", true);
        setField(command, "localUser", "testuser");

        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        command.run();

        Remote remote = testConfig.getRemote("local-remote");
        assertTrue(remote.isLocalModeBoolean());
        assertEquals("testuser", remote.getLocalUser());
    }

    @Test
    void testAddCommand_WithSshKey() throws Exception {
        RemoteCommand.AddCommand command = new RemoteCommand.AddCommand();
        setField(command, "name", "ssh-remote");
        setField(command, "url", "http://ssh.example.com");
        setField(command, "sshKeyPath", "/path/to/key");

        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        command.run();

        Remote remote = testConfig.getRemote("ssh-remote");
        assertEquals("/path/to/key", remote.getSshKeyPath());
        assertTrue(remote.isAuthEnabledBoolean());
    }

    @Test
    void testAddCommand_WithSetDefault() throws Exception {
        RemoteCommand.AddCommand command = new RemoteCommand.AddCommand();
        setField(command, "name", "default-remote");
        setField(command, "url", "http://default.com");
        setField(command, "setDefault", true);

        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        command.run();

        assertEquals("default-remote", testConfig.getDefaultRemote());
    }

    @Test
    void testRemoveCommand_ExistingRemote() throws Exception {
        Remote remote = new Remote("to-remove", "http://remove.com");
        testConfig.setRemote(remote);

        RemoteCommand.RemoveCommand command = new RemoteCommand.RemoveCommand();
        setField(command, "name", "to-remove");

        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        command.run();

        assertFalse(testConfig.hasRemote("to-remove"));
    }

    @Test
    void testSetUrlCommand_ExistingRemote() throws Exception {
        Remote remote = new Remote("update-me", "http://old-url.com");
        testConfig.setRemote(remote);

        RemoteCommand.SetUrlCommand command = new RemoteCommand.SetUrlCommand();
        setField(command, "name", "update-me");
        setField(command, "url", "http://new-url.com");

        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        command.run();

        assertEquals("http://new-url.com", testConfig.getRemote("update-me").getUrl());
    }

    @Test
    void testShowCommand_ExistingRemote() throws Exception {
        Remote remote = new Remote("show-me", "http://show.com");
        remote.setLocalMode("true");
        remote.setLocalUser("showuser");
        testConfig.setRemote(remote);

        RemoteCommand.ShowCommand command = new RemoteCommand.ShowCommand();
        setField(command, "name", "show-me");

        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        command.run();

        String output = outputStream.toString();
        assertTrue(output.contains("show-me"));
        assertTrue(output.contains("http://show.com"));
    }

    @Test
    void testShowCommand_AsDefault() throws Exception {
        Remote remote = new Remote("default-remote", "http://default.com");
        testConfig.setRemote(remote);
        testConfig.setDefaultRemote("default-remote");

        RemoteCommand.ShowCommand command = new RemoteCommand.ShowCommand();
        setField(command, "name", "default-remote");

        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        command.run();

        String output = outputStream.toString();
        assertTrue(output.contains("(default)"));
    }

    @Test
    void testShowCommand_WithAuth() throws Exception {
        Remote remote = new Remote("auth-remote", "http://auth.com");
        remote.setAuthEnabled("true");
        remote.setSshKeyPath("/path/to/key");
        testConfig.setRemote(remote);

        RemoteCommand.ShowCommand command = new RemoteCommand.ShowCommand();
        setField(command, "name", "auth-remote");

        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        command.run();

        String output = outputStream.toString();
        assertTrue(output.contains("Auth: enabled"));
        assertTrue(output.contains("SSH Key:"));
    }

    @Test
    void testRenameCommand_Success() throws Exception {
        Remote remote = new Remote("old-name", "http://old.com");
        testConfig.setRemote(remote);

        RemoteCommand.RenameCommand command = new RemoteCommand.RenameCommand();
        setField(command, "oldName", "old-name");
        setField(command, "newName", "new-name");

        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        command.run();

        assertFalse(testConfig.hasRemote("old-name"));
        assertTrue(testConfig.hasRemote("new-name"));
        assertEquals("http://old.com", testConfig.getRemote("new-name").getUrl());
    }

    @Test
    void testRenameCommand_UpdatesDefaultRemote() throws Exception {
        Remote remote = new Remote("old-default", "http://default.com");
        testConfig.setRemote(remote);
        testConfig.setDefaultRemote("old-default");

        RemoteCommand.RenameCommand command = new RemoteCommand.RenameCommand();
        setField(command, "oldName", "old-default");
        setField(command, "newName", "new-default");

        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        command.run();

        assertEquals("new-default", testConfig.getDefaultRemote());
    }
}
