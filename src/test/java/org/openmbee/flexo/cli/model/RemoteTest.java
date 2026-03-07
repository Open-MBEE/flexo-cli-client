package org.openmbee.flexo.cli.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Remote model class
 */
class RemoteTest {

    @Test
    void testRemoteCreation() {
        Remote remote = new Remote("origin", "http://localhost:8080");
        
        assertEquals("origin", remote.getName());
        assertEquals("http://localhost:8080", remote.getUrl());
    }

    @Test
    void testRemoteWithAuthSettings() {
        Remote remote = new Remote("production", "https://mms.example.com");
        remote.setAuthEnabled("true");
        remote.setSshKeyPath("~/.ssh/id_rsa");
        
        assertTrue(remote.isAuthEnabledBoolean());
        assertEquals("~/.ssh/id_rsa", remote.getSshKeyPath());
    }

    @Test
    void testRemoteWithLocalMode() {
        Remote remote = new Remote("local", "http://localhost:8080");
        remote.setLocalMode("true");
        remote.setLocalUser("root");
        remote.setLocalJwtSecret("secret123");
        
        assertTrue(remote.isLocalModeBoolean());
        assertEquals("root", remote.getLocalUser());
        assertEquals("secret123", remote.getLocalJwtSecret());
    }

    @Test
    void testLocalModeDefaultTrue() {
        Remote remote = new Remote("test", "http://test.com");
        // Local mode should default to true when not set
        assertTrue(remote.isLocalModeBoolean());
    }

    @Test
    void testLocalModeExplicitFalse() {
        Remote remote = new Remote("test", "http://test.com");
        remote.setLocalMode("false");
        assertFalse(remote.isLocalModeBoolean());
    }

    @Test
    void testAuthEnabledDefaultFalse() {
        Remote remote = new Remote("test", "http://test.com");
        assertFalse(remote.isAuthEnabledBoolean());
    }

    @Test
    void testRemoteEquality() {
        Remote remote1 = new Remote("origin", "http://localhost:8080");
        Remote remote2 = new Remote("origin", "http://different-url.com");
        
        // Remotes are equal if names are equal, regardless of URL
        assertEquals(remote1, remote2);
    }

    @Test
    void testRemoteInequality() {
        Remote remote1 = new Remote("origin", "http://localhost:8080");
        Remote remote2 = new Remote("production", "http://localhost:8080");
        
        // Different names mean different remotes
        assertNotEquals(remote1, remote2);
    }

    @Test
    void testRemoteToString() {
        Remote remote = new Remote("origin", "http://localhost:8080");
        String str = remote.toString();
        
        assertTrue(str.contains("origin"));
        assertTrue(str.contains("http://localhost:8080"));
    }

    @Test
    void testRemoteHashCode() {
        Remote remote1 = new Remote("origin", "http://localhost:8080");
        Remote remote2 = new Remote("origin", "http://different-url.com");
        
        assertEquals(remote1.hashCode(), remote2.hashCode());
    }

    @Test
    void testRemoteHashCodeDifferent() {
        Remote remote1 = new Remote("origin", "http://localhost:8080");
        Remote remote2 = new Remote("production", "http://localhost:8080");
        
        assertNotEquals(remote1.hashCode(), remote2.hashCode());
    }

    @Test
    void testRemoteEqualsSame() {
        Remote remote = new Remote("test", "http://test.com");
        
        assertEquals(remote, remote);
    }

    @Test
    void testRemoteEqualsNull() {
        Remote remote = new Remote("test", "http://test.com");
        
        assertNotEquals(remote, null);
    }

    @Test
    void testRemoteEqualsDifferentClass() {
        Remote remote = new Remote("test", "http://test.com");
        
        assertNotEquals(remote, "not a remote");
    }

    @Test
    void testDefaultConstructor() {
        Remote remote = new Remote();
        
        assertNotNull(remote);
    }

    @Test
    void testSetAndGetAllFields() {
        Remote remote = new Remote();
        
        remote.setName("test");
        remote.setUrl("http://test.com");
        remote.setAuthEnabled("true");
        remote.setSshKeyPath("/path/to/key");
        remote.setLocalMode("true");
        remote.setLocalUser("user");
        remote.setLocalJwtSecret("secret");
        
        assertEquals("test", remote.getName());
        assertEquals("http://test.com", remote.getUrl());
        assertEquals("true", remote.getAuthEnabled());
        assertEquals("/path/to/key", remote.getSshKeyPath());
        assertEquals("true", remote.getLocalMode());
        assertEquals("user", remote.getLocalUser());
        assertEquals("secret", remote.getLocalJwtSecret());
    }
}
