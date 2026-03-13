package org.openmbee.flexo.cli.commands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmbee.flexo.cli.FlexoCLI;
import org.openmbee.flexo.cli.client.FlexoMmsClient;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.openmbee.flexo.cli.model.Remote;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for BaseCommand functionality
 */
class BaseCommandTest {

    @Mock
    private FlexoCLI mockParent;

    @Mock
    private FlexoConfig mockConfig;

    private TestCommand testCommand;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        testCommand = new TestCommand();
        // Inject mocked parent CLI into the test command
        testCommand.parent = mockParent;
    }

    @Test
    void testGetOrgId_FromParent() {
        when(mockParent.getOrgId()).thenReturn("test-org");
        
        String orgId = testCommand.getOrgId(mockConfig);
        
        assertEquals("test-org", orgId);
        verify(mockConfig, never()).getDefaultOrg();
    }

    @Test
    void testGetOrgId_FromConfig() {
        when(mockParent.getOrgId()).thenReturn(null);
        when(mockConfig.getDefaultOrg()).thenReturn("config-org");
        
        String orgId = testCommand.getOrgId(mockConfig);
        
        assertEquals("config-org", orgId);
    }

    @Test
    void testGetRepoId_FromParent() {
        when(mockParent.getRepoId()).thenReturn("test-repo");
        
        String repoId = testCommand.getRepoId(mockConfig);
        
        assertEquals("test-repo", repoId);
        verify(mockConfig, never()).getDefaultRepo();
    }

    @Test
    void testGetRepoId_FromConfig() {
        when(mockParent.getRepoId()).thenReturn(null);
        when(mockConfig.getDefaultRepo()).thenReturn("config-repo");
        
        String repoId = testCommand.getRepoId(mockConfig);
        
        assertEquals("config-repo", repoId);
    }

    @Test
    void testValidateOrgAndRepo_Success() {
        assertDoesNotThrow(() -> testCommand.validateOrgAndRepo("org", "repo"));
    }

    @Test
    void testValidateOrgAndRepo_MissingOrg() {
        CommandExecutionException exception = assertThrows(
            CommandExecutionException.class,
            () -> testCommand.validateOrgAndRepo(null, "repo")
        );
        
        assertTrue(exception.getMessage().contains("Organization ID is required"));
        assertEquals(1, exception.getExitCode());
    }

    @Test
    void testValidateOrgAndRepo_EmptyOrg() {
        CommandExecutionException exception = assertThrows(
            CommandExecutionException.class,
            () -> testCommand.validateOrgAndRepo("", "repo")
        );
        
        assertTrue(exception.getMessage().contains("Organization ID is required"));
    }

    @Test
    void testValidateOrgAndRepo_MissingRepo() {
        CommandExecutionException exception = assertThrows(
            CommandExecutionException.class,
            () -> testCommand.validateOrgAndRepo("org", null)
        );
        
        assertTrue(exception.getMessage().contains("Repository ID is required"));
        assertEquals(1, exception.getExitCode());
    }

    @Test
    void testValidateOrgAndRepo_EmptyRepo() {
        CommandExecutionException exception = assertThrows(
            CommandExecutionException.class,
            () -> testCommand.validateOrgAndRepo("org", "")
        );
        
        assertTrue(exception.getMessage().contains("Repository ID is required"));
    }

    @Test
    void testCreateClient_WithRemote() {
        // Setup remote configuration
        Remote mockRemote = new Remote();
        mockRemote.setUrl("http://remote.example.com");
        mockRemote.setAuthEnabled("true");
        mockRemote.setSshKeyPath("/path/to/key");
        mockRemote.setLocalMode("false");

        when(mockParent.getRemoteName()).thenReturn("origin");
        when(mockConfig.getDefaultRemote()).thenReturn("origin");
        when(mockConfig.getRemote("origin")).thenReturn(mockRemote);

        FlexoMmsClient client = testCommand.createClient(mockConfig, true);

        assertNotNull(client);
        verify(mockConfig).getRemote("origin");
    }

    @Test
    void testCreateClient_WithLegacyConfig() {
        when(mockParent.getRemoteName()).thenReturn(null);
        when(mockConfig.getDefaultRemote()).thenReturn(null);
        when(mockConfig.getRemote(null)).thenReturn(null);
        when(mockConfig.getMmsUrl()).thenReturn("http://localhost:8080");
        when(mockConfig.isAuthEnabled()).thenReturn(false);
        when(mockConfig.getSshKeyPath()).thenReturn(null);
        when(mockConfig.isLocalMode()).thenReturn(false);
        when(mockConfig.getLocalUser()).thenReturn("root");
        when(mockConfig.getLocalJwtSecret()).thenReturn("");
        
        FlexoMmsClient client = testCommand.createClient(mockConfig);
        
        assertNotNull(client);
        verify(mockConfig).getMmsUrl();
    }

    @Test
    void testCommandException_WithMessage() {
        CommandExecutionException exception = 
            new CommandExecutionException("Test error", 2);
        
        assertEquals("Test error", exception.getMessage());
        assertEquals(2, exception.getExitCode());
    }

    @Test
    void testCommandException_WithCause() {
        Exception cause = new RuntimeException("Root cause");
        CommandExecutionException exception = 
            new CommandExecutionException("Test error", cause, 3);
        
        assertEquals("Test error", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(3, exception.getExitCode());
    }

    @Test
    void testCreateClient_WithoutRemote() {
        when(mockConfig.getMmsUrl()).thenReturn("http://localhost:8080");
        when(mockConfig.getLocalJwtSecret()).thenReturn("");
        when(mockConfig.isLocalMode()).thenReturn(true);
        when(mockConfig.getLocalUser()).thenReturn("root");
        
        FlexoMmsClient client = testCommand.createClient(mockConfig, false);
        
        assertNotNull(client);
    }

    @Test
    void testCreateClient_WithRemote_NullRemote() {
        when(mockParent.getRemoteName()).thenReturn("origin");
        when(mockConfig.getDefaultRemote()).thenReturn("origin");
        when(mockConfig.getRemote("origin")).thenReturn(null);
        when(mockConfig.getMmsUrl()).thenReturn("http://localhost:8080");
        when(mockConfig.isAuthEnabled()).thenReturn(false);
        when(mockConfig.getSshKeyPath()).thenReturn(null);
        when(mockConfig.isLocalMode()).thenReturn(true);
        when(mockConfig.getLocalUser()).thenReturn("root");
        when(mockConfig.getLocalJwtSecret()).thenReturn("secret123456789012345678901234567890");
        
        FlexoMmsClient client = testCommand.createClient(mockConfig, true);
        
        assertNotNull(client);
    }

    @Test
    void testCreateClient_WithRemote_LocalMode() {
        Remote mockRemote = new Remote();
        mockRemote.setUrl("http://remote.example.com");
        mockRemote.setLocalMode("true");
        mockRemote.setLocalUser("remoteuser");
        mockRemote.setLocalJwtSecret("secret123456789012345678901234567890");
        
        when(mockParent.getRemoteName()).thenReturn("origin");
        when(mockConfig.getDefaultRemote()).thenReturn("origin");
        when(mockConfig.getRemote("origin")).thenReturn(mockRemote);
        
        FlexoMmsClient client = testCommand.createClient(mockConfig, true);
        
        assertNotNull(client);
    }

    @Test
    void testCreateClient_WithRemote_AuthEnabled() {
        Remote mockRemote = new Remote();
        mockRemote.setUrl("http://remote.example.com");
        mockRemote.setAuthEnabled("true");
        mockRemote.setSshKeyPath("/path/to/key");
        mockRemote.setLocalMode("false");
        
        when(mockParent.getRemoteName()).thenReturn("origin");
        when(mockConfig.getDefaultRemote()).thenReturn("origin");
        when(mockConfig.getRemote("origin")).thenReturn(mockRemote);
        
        FlexoMmsClient client = testCommand.createClient(mockConfig, true);
        
        assertNotNull(client);
    }

    @Test
    void testCreateClient_WithRemote_NullLocalUser() {
        Remote mockRemote = new Remote();
        mockRemote.setUrl("http://remote.example.com");
        mockRemote.setLocalMode("true");
        mockRemote.setLocalUser(null);
        mockRemote.setLocalJwtSecret("secret123456789012345678901234567890");
        
        when(mockParent.getRemoteName()).thenReturn("origin");
        when(mockConfig.getDefaultRemote()).thenReturn("origin");
        when(mockConfig.getRemote("origin")).thenReturn(mockRemote);
        when(mockConfig.getLocalUser()).thenReturn("defaultuser");
        
        FlexoMmsClient client = testCommand.createClient(mockConfig, true);
        
        assertNotNull(client);
    }

    @Test
    void testCreateClient_WithRemote_NullLocalJwtSecret() {
        Remote mockRemote = new Remote();
        mockRemote.setUrl("http://remote.example.com");
        mockRemote.setLocalMode("true");
        mockRemote.setLocalUser("user");
        mockRemote.setLocalJwtSecret(null);
        
        when(mockParent.getRemoteName()).thenReturn("origin");
        when(mockConfig.getDefaultRemote()).thenReturn("origin");
        when(mockConfig.getRemote("origin")).thenReturn(mockRemote);
        when(mockConfig.getLocalUser()).thenReturn("defaultuser");
        when(mockConfig.getLocalJwtSecret()).thenReturn("defaultsecret123456789012345678901234567890");
        
        FlexoMmsClient client = testCommand.createClient(mockConfig, true);
        
        assertNotNull(client);
    }

    @Test
    void testHandleError_CommandException() {
        CommandExecutionException exception = new CommandExecutionException("Test error", 1);
        
        assertThrows(CommandExecutionException.class, () -> {
            testCommand.handleError(exception);
        });
    }

    @Test
    void testHandleError_GenericException() {
        RuntimeException exception = new RuntimeException("Generic error");
        
        assertThrows(CommandExecutionException.class, () -> {
            testCommand.handleError(exception);
        });
    }

    @Test
    void testRun_Success() {
        testCommand.run();
        
        assertTrue(testCommand.wasExecuted);
    }

    @Test
    void testRun_CommandException() {
        testCommand.shouldThrowCommandException = true;
        
        assertThrows(CommandExecutionException.class, () -> {
            testCommand.run();
        });
    }

    @Test
    void testRun_GenericException() {
        testCommand.shouldThrowGenericException = true;
        
        assertThrows(CommandExecutionException.class, () -> {
            testCommand.run();
        });
    }

    @Test
    void testGetConfig() {
        FlexoCLI.setConfig(new FlexoConfig(false));
        
        FlexoConfig config = testCommand.getConfig();
        
        assertNotNull(config);
    }

    /**
     * Test implementation of BaseCommand for testing purposes
     */
    @CommandLine.Command(name = "test")
    static class TestCommand extends BaseCommand {
        // Simulated parent CLI instance for tests
        FlexoCLI parent;

        @Override
        protected FlexoCLI getParentCli() {
            return parent;
        }

        boolean wasExecuted = false;
        boolean shouldThrowCommandException = false;
        boolean shouldThrowGenericException = false;

        @Override
        protected void executeCommand() throws Exception {
            if (shouldThrowCommandException) {
                throw new CommandExecutionException("Command failed", 1);
            }
            if (shouldThrowGenericException) {
                throw new RuntimeException("Generic error");
            }
            wasExecuted = true;
        }
    }
}
