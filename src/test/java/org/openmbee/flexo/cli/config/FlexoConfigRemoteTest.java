package org.openmbee.flexo.cli.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openmbee.flexo.cli.model.Remote;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Remote configuration in FlexoConfig
 */
class FlexoConfigRemoteTest {

    @TempDir
    Path tempDir;

    private FlexoConfig config;

    @BeforeEach
    void setUp() {
        config = new FlexoConfig();
    }

    @Test
    void testAddRemote() {
        Remote remote = new Remote("origin", "http://localhost:8080");
        remote.setLocalMode("true");
        remote.setLocalUser("root");
        
        config.setRemote(remote);
        
        Remote retrieved = config.getRemote("origin");
        assertNotNull(retrieved);
        assertEquals("origin", retrieved.getName());
        assertEquals("http://localhost:8080", retrieved.getUrl());
        assertTrue(retrieved.isLocalModeBoolean());
        assertEquals("root", retrieved.getLocalUser());
    }

    @Test
    void testGetNonExistentRemote() {
        Remote remote = config.getRemote("nonexistent");
        assertNull(remote);
    }

    @Test
    void testHasRemote() {
        Remote remote = new Remote("test", "http://test.com");
        config.setRemote(remote);
        
        assertTrue(config.hasRemote("test"));
        assertFalse(config.hasRemote("nonexistent"));
    }

    @Test
    void testGetAllRemotes() {
        Remote remote1 = new Remote("origin", "http://localhost:8080");
        Remote remote2 = new Remote("production", "https://mms.example.com");
        
        config.setRemote(remote1);
        config.setRemote(remote2);
        
        Map<String, Remote> remotes = config.getRemotes();
        assertEquals(2, remotes.size());
        assertTrue(remotes.containsKey("origin"));
        assertTrue(remotes.containsKey("production"));
    }

    @Test
    void testRemoveRemote() {
        Remote remote = new Remote("test", "http://test.com");
        config.setRemote(remote);
        
        assertTrue(config.hasRemote("test"));
        
        config.removeRemote("test");
        
        assertFalse(config.hasRemote("test"));
        assertNull(config.getRemote("test"));
    }

    @Test
    void testUpdateRemoteUrl() {
        Remote remote = new Remote("origin", "http://localhost:8080");
        config.setRemote(remote);
        
        remote.setUrl("http://newhost:9090");
        config.setRemote(remote);
        
        Remote retrieved = config.getRemote("origin");
        assertEquals("http://newhost:9090", retrieved.getUrl());
    }

    @Test
    void testDefaultRemote() {
        // Default should be "origin"
        assertEquals("origin", config.getDefaultRemote());
        
        config.setDefaultRemote("production");
        assertEquals("production", config.getDefaultRemote());
    }

    @Test
    void testGetRemoteUrl() {
        Remote remote = new Remote("test", "http://test.com");
        config.setRemote(remote);
        
        assertEquals("http://test.com", config.getRemoteUrl("test"));
    }

    @Test
    void testGetRemoteUrlFallbackToDefault() {
        Remote remote = new Remote("origin", "http://localhost:8080");
        config.setRemote(remote);
        
        // When no name specified, should use default remote
        String url = config.getRemoteUrl(null);
        assertEquals("http://localhost:8080", url);
    }

    @Test
    void testGetRemoteUrlFallbackToLegacy() {
        // When remote doesn't exist, should fall back to mms.url
        String url = config.getRemoteUrl("nonexistent");
        // Should return the legacy mms.url (which defaults to localhost:8080)
        assertNotNull(url);
    }

    @Test
    void testRemoteWithAuthSettings() {
        Remote remote = new Remote("secure", "https://secure.example.com");
        remote.setAuthEnabled("true");
        remote.setSshKeyPath("/path/to/key");
        
        config.setRemote(remote);
        
        Remote retrieved = config.getRemote("secure");
        assertTrue(retrieved.isAuthEnabledBoolean());
        assertEquals("/path/to/key", retrieved.getSshKeyPath());
    }

    @Test
    void testMultipleRemotesWithDifferentAuth() {
        Remote local = new Remote("local", "http://localhost:8080");
        local.setLocalMode("true");
        local.setLocalUser("root");
        
        Remote prod = new Remote("production", "https://mms.example.com");
        prod.setLocalMode("false");
        prod.setAuthEnabled("true");
        prod.setSshKeyPath("~/.ssh/id_rsa");
        
        config.setRemote(local);
        config.setRemote(prod);
        
        Remote retrievedLocal = config.getRemote("local");
        assertTrue(retrievedLocal.isLocalModeBoolean());
        assertFalse(retrievedLocal.isAuthEnabledBoolean());
        
        Remote retrievedProd = config.getRemote("production");
        assertFalse(retrievedProd.isLocalModeBoolean());
        assertTrue(retrievedProd.isAuthEnabledBoolean());
    }
}
