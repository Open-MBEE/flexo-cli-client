package org.openmbee.flexo.cli.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuration management for Flexo CLI.
 * Reads from multiple sources in order of precedence:
 * 1. Environment variables
 * 2. User config file (~/.flexo/config)
 * 3. Application default properties
 */
public class FlexoConfig {
    private static final Logger logger = LoggerFactory.getLogger(FlexoConfig.class);

    private static final String USER_CONFIG_DIR = ".flexo";
    private static final String USER_CONFIG_FILE = "config";
    private static final String DEFAULT_PROPERTIES = "/application.properties";

    private final Properties properties;

    public FlexoConfig() {
        this.properties = new Properties();
        loadConfiguration();
    }

    private void loadConfiguration() {
        // 1. Load default properties from resources
        try (InputStream defaultStream = getClass().getResourceAsStream(DEFAULT_PROPERTIES)) {
            if (defaultStream != null) {
                properties.load(defaultStream);
                logger.debug("Loaded default properties");
            }
        } catch (IOException e) {
            logger.warn("Could not load default properties: {}", e.getMessage());
        }

        // 2. Load user config file if it exists
        Path userConfigPath = getUserConfigPath();
        if (Files.exists(userConfigPath)) {
            try (InputStream userStream = Files.newInputStream(userConfigPath)) {
                properties.load(userStream);
                logger.debug("Loaded user config from: {}", userConfigPath);
            } catch (IOException e) {
                logger.warn("Could not load user config: {}", e.getMessage());
            }
        }

        // 3. Override with environment variables
        overrideWithEnvironment();
    }

    private void overrideWithEnvironment() {
        // Environment variables override file config
        // Format: FLEXO_MMS_URL -> mms.url
        System.getenv().forEach((key, value) -> {
            if (key.startsWith("FLEXO_")) {
                String propKey = key.substring(6).toLowerCase().replace('_', '.');
                properties.setProperty(propKey, value);
                logger.debug("Environment override: {} = {}", propKey, value);
            }
        });
    }

    private Path getUserConfigPath() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, USER_CONFIG_DIR, USER_CONFIG_FILE);
    }

    /**
     * Get a configuration property
     */
    public String get(String key) {
        return properties.getProperty(key);
    }

    /**
     * Get a configuration property with a default value
     */
    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Get a boolean configuration property
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Set a configuration property
     */
    public void set(String key, String value) {
        properties.setProperty(key, value);
    }

    /**
     * Save configuration to user config file
     */
    public void save() throws IOException {
        Path userConfigPath = getUserConfigPath();
        Files.createDirectories(userConfigPath.getParent());

        try (OutputStream out = Files.newOutputStream(userConfigPath)) {
            properties.store(out, "Flexo CLI Configuration");
            logger.info("Configuration saved to: {}", userConfigPath);
        }
    }

    // Convenience methods for common properties

    public String getMmsUrl() {
        return get("mms.url", "http://localhost:8080");
    }

    public boolean isAuthEnabled() {
        return getBoolean("auth.enabled", false);
    }

    public String getSshKeyPath() {
        String path = get("auth.sshKeyPath", "~/.ssh/id_rsa");
        if (path.startsWith("~")) {
            path = System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    public String getDefaultOrg() {
        return get("default.org");
    }

    public String getDefaultRepo() {
        return get("default.repo");
    }

    public String getDefaultBranch() {
        return get("default.branch", "master");
    }

    public String getRdfFormat() {
        return get("rdf.format", "turtle");
    }

    public boolean isLocalMode() {
        return getBoolean("local.mode", true);
    }

    public String getLocalUser() {
        return get("local.user", "root");
    }

    public String getLocalJwtSecret() {
        return get("local.jwtSecret", "dev-secret-please-change-in-production");
    }
}
