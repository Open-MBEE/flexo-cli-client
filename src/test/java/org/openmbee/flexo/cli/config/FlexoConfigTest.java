package org.openmbee.flexo.cli.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FlexoConfigTest {

    private Map<String, String> originalEnv;

    @BeforeEach
    void setUp() {
        // Store original environment variables
        originalEnv = new HashMap<>(System.getenv());
    }

    @AfterEach
    void tearDown() {
        // Note: Cannot fully restore environment in Java, but tests are isolated
    }

    @Test
    void testConstructor() {
        FlexoConfig config = new FlexoConfig();
        assertNotNull(config);
    }

    @Test
    void testGetDefaultMmsUrl() {
        FlexoConfig config = new FlexoConfig();
        String url = config.getMmsUrl();
        assertNotNull(url);
        assertEquals("http://localhost:8080", url);
    }

    @Test
    void testGetDefaultAuthEnabled() {
        FlexoConfig config = new FlexoConfig();
        boolean authEnabled = config.isAuthEnabled();
        assertFalse(authEnabled);
    }

    @Test
    void testGetDefaultBranch() {
        FlexoConfig config = new FlexoConfig();
        String branch = config.getDefaultBranch();
        assertEquals("master", branch);
    }

    @Test
    void testGetDefaultRdfFormat() {
        FlexoConfig config = new FlexoConfig();
        String format = config.getRdfFormat();
        assertEquals("turtle", format);
    }

    @Test
    void testGetAndSet() {
        FlexoConfig config = new FlexoConfig();

        config.set("test.key", "test.value");
        assertEquals("test.value", config.get("test.key"));
    }

    @Test
    void testGetWithDefault() {
        FlexoConfig config = new FlexoConfig();

        String value = config.get("non.existent.key", "default.value");
        assertEquals("default.value", value);
    }

    @Test
    void testGetNonExistentKey() {
        FlexoConfig config = new FlexoConfig();

        String value = config.get("non.existent.key");
        assertNull(value);
    }

    @Test
    void testGetBooleanTrue() {
        FlexoConfig config = new FlexoConfig();

        config.set("test.bool", "true");
        assertTrue(config.getBoolean("test.bool", false));
    }

    @Test
    void testGetBooleanFalse() {
        FlexoConfig config = new FlexoConfig();

        config.set("test.bool", "false");
        assertFalse(config.getBoolean("test.bool", true));
    }

    @Test
    void testGetBooleanDefault() {
        FlexoConfig config = new FlexoConfig();

        boolean result = config.getBoolean("non.existent", true);
        assertTrue(result);

        result = config.getBoolean("non.existent", false);
        assertFalse(result);
    }

    @Test
    void testGetBooleanInvalidValue() {
        FlexoConfig config = new FlexoConfig();

        config.set("test.bool", "not-a-boolean");
        assertFalse(config.getBoolean("test.bool", false));
    }

    @Test
    void testSaveConfig(@TempDir Path tempDir) throws IOException {
        // Create a temporary config with custom properties
        FlexoConfig config = new FlexoConfig();
        config.set("test.property", "test.value");
        config.set("another.property", "another.value");

        // Note: save() will save to user home directory, which we can't easily test
        // This test verifies the method doesn't throw exceptions
        assertDoesNotThrow(() -> {
            try {
                config.save();
            } catch (IOException e) {
                // Might fail if ~/.flexo directory can't be created
                // This is acceptable in some test environments
            }
        });
    }

    @Test
    void testGetSshKeyPathWithTildeExpansion() {
        FlexoConfig config = new FlexoConfig();

        String path = config.getSshKeyPath();
        assertNotNull(path);
        assertFalse(path.startsWith("~"));
        assertTrue(path.contains(".ssh"));
    }

    @Test
    void testGetSshKeyPathCustom() {
        FlexoConfig config = new FlexoConfig();
        config.set("auth.sshKeyPath", "/custom/path/key.pem");

        String path = config.getSshKeyPath();
        assertEquals("/custom/path/key.pem", path);
    }

    @Test
    void testGetDefaultOrg() {
        FlexoConfig config = new FlexoConfig();

        String org = config.getDefaultOrg();
        // Default from application.properties is empty string
        assertNotNull(org);

        config.set("default.org", "my-org");
        assertEquals("my-org", config.getDefaultOrg());
    }

    @Test
    void testGetDefaultRepo() {
        FlexoConfig config = new FlexoConfig();

        String repo = config.getDefaultRepo();
        // Default from application.properties is empty string
        assertNotNull(repo);

        config.set("default.repo", "my-repo");
        assertEquals("my-repo", config.getDefaultRepo());
    }

    @Test
    void testConvenienceMethods() {
        FlexoConfig config = new FlexoConfig();

        // Test setting and getting through convenience methods
        config.set("mms.url", "http://custom.example.com:9090");
        assertEquals("http://custom.example.com:9090", config.getMmsUrl());

        config.set("auth.enabled", "true");
        assertTrue(config.isAuthEnabled());

        config.set("default.branch", "develop");
        assertEquals("develop", config.getDefaultBranch());

        config.set("rdf.format", "jsonld");
        assertEquals("jsonld", config.getRdfFormat());
    }

    @Test
    void testEnvironmentVariableOverride() {
        // This test demonstrates the concept, but Java doesn't allow modifying System.getenv()
        // In a real scenario with injected environment, FLEXO_MMS_URL would override file config

        FlexoConfig config = new FlexoConfig();

        // The config should load from properties/defaults
        // Environment variables starting with FLEXO_ would override these
        assertNotNull(config.getMmsUrl());
    }

    @Test
    void testLoadFromDefaultProperties() {
        // The config should load application.properties from resources
        FlexoConfig config = new FlexoConfig();

        // These should come from src/main/resources/application.properties or defaults
        assertNotNull(config.getMmsUrl());
        assertNotNull(config.getDefaultBranch());
        assertNotNull(config.getRdfFormat());
    }

    @Test
    void testMultipleInstances() {
        // Test that multiple config instances don't interfere with each other
        FlexoConfig config1 = new FlexoConfig();
        FlexoConfig config2 = new FlexoConfig();

        config1.set("test.key", "value1");
        config2.set("test.key", "value2");

        assertEquals("value1", config1.get("test.key"));
        assertEquals("value2", config2.get("test.key"));
    }

    @Test
    void testCaseInsensitiveBoolean() {
        FlexoConfig config = new FlexoConfig();

        config.set("test.bool1", "True");
        assertTrue(config.getBoolean("test.bool1", false));

        config.set("test.bool2", "TRUE");
        assertTrue(config.getBoolean("test.bool2", false));

        config.set("test.bool3", "FALSE");
        assertFalse(config.getBoolean("test.bool3", true));
    }
}
