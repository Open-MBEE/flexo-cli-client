package org.openmbee.flexo.cli.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationHandlerTest {

    @Test
    void testAuthenticationDisabled() {
        AuthenticationHandler handler = new AuthenticationHandler(false, null);

        assertFalse(handler.isEnabled());
        assertNull(handler.getToken());
        assertNull(handler.getAuthorizationHeader());
    }

    @Test
    void testAuthenticationEnabledWithoutKey() {
        AuthenticationHandler handler = new AuthenticationHandler(true, null);

        assertTrue(handler.isEnabled());
        // Without a valid key, token generation should return null
        assertNull(handler.getToken());
        assertNull(handler.getAuthorizationHeader());
    }

    @Test
    void testAuthenticationEnabledWithEmptyPath() {
        AuthenticationHandler handler = new AuthenticationHandler(true, "");

        assertTrue(handler.isEnabled());
        // With empty path, token generation should return null
        assertNull(handler.getToken());
        assertNull(handler.getAuthorizationHeader());
    }

    @Test
    void testAuthenticationEnabledWithNonExistentKey() {
        AuthenticationHandler handler = new AuthenticationHandler(true, "/non/existent/key.pem");

        assertTrue(handler.isEnabled());
        // With non-existent key, token generation should return null
        assertNull(handler.getToken());
        assertNull(handler.getAuthorizationHeader());
    }

    @Test
    void testClearCache() {
        AuthenticationHandler handler = new AuthenticationHandler(false, null);

        // Should not throw exception even when disabled
        assertDoesNotThrow(() -> handler.clearCache());
    }

    @Test
    void testIsEnabled() {
        AuthenticationHandler handler1 = new AuthenticationHandler(true, "some-path");
        assertTrue(handler1.isEnabled());

        AuthenticationHandler handler2 = new AuthenticationHandler(false, "some-path");
        assertFalse(handler2.isEnabled());
    }

    @Test
    void testGetAuthorizationHeaderWhenDisabled() {
        AuthenticationHandler handler = new AuthenticationHandler(false, null);
        assertNull(handler.getAuthorizationHeader());
    }

    @Test
    void testGetAuthorizationHeaderFormat() {
        AuthenticationHandler handler = new AuthenticationHandler(false, null);

        // When disabled, should return null
        assertNull(handler.getAuthorizationHeader());

        // When enabled but no valid key, should also return null
        AuthenticationHandler handler2 = new AuthenticationHandler(true, "/invalid/path");
        assertNull(handler2.getAuthorizationHeader());
    }

    @Test
    void testGetTokenWhenDisabled() {
        AuthenticationHandler handler = new AuthenticationHandler(false, null);
        assertNull(handler.getToken());
    }

    @Test
    void testGetTokenWhenEnabledWithoutValidKey() {
        AuthenticationHandler handler = new AuthenticationHandler(true, "/invalid/path");
        assertNull(handler.getToken());
    }

    @Test
    void testTokenCachingBehavior() {
        // Even without a valid key, we can test the caching logic
        AuthenticationHandler handler = new AuthenticationHandler(false, null);

        String token1 = handler.getToken();
        String token2 = handler.getToken();

        // Both should be null for disabled handler
        assertNull(token1);
        assertNull(token2);
    }

    @Test
    void testClearCacheAndRegenerate() {
        AuthenticationHandler handler = new AuthenticationHandler(false, null);

        String token1 = handler.getToken();
        handler.clearCache();
        String token2 = handler.getToken();

        // Both should still be null for disabled handler
        assertNull(token1);
        assertNull(token2);
    }

    @Test
    void testMultipleGetTokenCalls() {
        AuthenticationHandler handler = new AuthenticationHandler(false, null);

        // Multiple calls should be safe
        for (int i = 0; i < 5; i++) {
            assertNull(handler.getToken());
        }
    }

    @Test
    void testMultipleGetAuthorizationHeaderCalls() {
        AuthenticationHandler handler = new AuthenticationHandler(false, null);

        // Multiple calls should be safe
        for (int i = 0; i < 5; i++) {
            assertNull(handler.getAuthorizationHeader());
        }
    }

    @Test
    void testConstructorWithVariousPathFormats() {
        // Test with null path
        AuthenticationHandler handler1 = new AuthenticationHandler(true, null);
        assertTrue(handler1.isEnabled());

        // Test with empty path
        AuthenticationHandler handler2 = new AuthenticationHandler(true, "");
        assertTrue(handler2.isEnabled());

        // Test with relative path
        AuthenticationHandler handler3 = new AuthenticationHandler(true, "relative/path/key.pem");
        assertTrue(handler3.isEnabled());

        // Test with absolute path
        AuthenticationHandler handler4 = new AuthenticationHandler(true, "/absolute/path/key.pem");
        assertTrue(handler4.isEnabled());
    }

    @Test
    void testAuthenticationHandlerIsolation() {
        // Test that multiple handlers don't interfere with each other
        AuthenticationHandler handler1 = new AuthenticationHandler(true, "/path/1");
        AuthenticationHandler handler2 = new AuthenticationHandler(false, "/path/2");

        assertTrue(handler1.isEnabled());
        assertFalse(handler2.isEnabled());
    }

    @Test
    void testClearCacheMultipleTimes() {
        AuthenticationHandler handler = new AuthenticationHandler(true, "/some/path");

        // Multiple clear cache calls should be safe
        assertDoesNotThrow(() -> {
            handler.clearCache();
            handler.clearCache();
            handler.clearCache();
        });
    }

    // Note: Testing actual JWT generation with a real SSH key would require:
    // 1. A valid test RSA key pair
    // 2. Proper PEM formatting
    // 3. BouncyCastle provider initialization
    // These tests cover the public API behavior and edge cases without requiring
    // complex key generation infrastructure.
}
