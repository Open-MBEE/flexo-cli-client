package org.openmbee.flexo.cli.config;

import org.openmbee.flexo.cli.model.Remote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

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
        return get("default.org", "");
    }

    public String getDefaultRepo() {
        return get("default.repo", "");
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
        String secret = get("local.jwtSecret");

        if (secret == null || secret.isEmpty()) {
            secret = "devsecretpleasechangeinproduction1234567890";
            set("local.jwtSecret", secret);
            try {
                save();
                logger.info("Set default JWT secret in configuration");
            } catch (IOException e) {
                logger.warn("Failed to save JWT secret to config: {}", e.getMessage());
            }
        }

        return secret;
    }
    
    /**
     * Generate a secure random JWT secret (64 characters, base64-encoded)
     */
    private String generateJwtSecret() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] bytes = new byte[48]; // 48 bytes = 64 base64 characters
        random.nextBytes(bytes);
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    // Remote management methods

    /**
     * Get all configured remotes
     */
    public Map<String, Remote> getRemotes() {
        Map<String, Remote> remotes = new LinkedHashMap<>();
        
        // Find all remote.* properties
        Set<String> remoteNames = new HashSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("remote.") && key.contains(".url")) {
                String remoteName = key.substring(7, key.indexOf(".url"));
                remoteNames.add(remoteName);
            }
        }
        
        // Build Remote objects
        for (String name : remoteNames) {
            Remote remote = new Remote();
            remote.setName(name);
            remote.setUrl(get("remote." + name + ".url"));
            remote.setAuthEnabled(get("remote." + name + ".authEnabled"));
            remote.setSshKeyPath(get("remote." + name + ".sshKeyPath"));
            remote.setLocalMode(get("remote." + name + ".localMode"));
            remote.setLocalUser(get("remote." + name + ".localUser"));
            remote.setLocalJwtSecret(get("remote." + name + ".localJwtSecret"));
            remotes.put(name, remote);
        }
        
        return remotes;
    }

    /**
     * Get a specific remote by name
     */
    public Remote getRemote(String name) {
        String url = get("remote." + name + ".url");
        if (url == null) {
            return null;
        }
        
        Remote remote = new Remote();
        remote.setName(name);
        remote.setUrl(url);
        remote.setAuthEnabled(get("remote." + name + ".authEnabled"));
        remote.setSshKeyPath(get("remote." + name + ".sshKeyPath"));
        remote.setLocalMode(get("remote." + name + ".localMode"));
        remote.setLocalUser(get("remote." + name + ".localUser"));
        remote.setLocalJwtSecret(get("remote." + name + ".localJwtSecret"));
        
        return remote;
    }

    /**
     * Add or update a remote
     */
    public void setRemote(Remote remote) {
        String prefix = "remote." + remote.getName();
        set(prefix + ".url", remote.getUrl());
        
        if (remote.getAuthEnabled() != null) {
            set(prefix + ".authEnabled", remote.getAuthEnabled());
        }
        if (remote.getSshKeyPath() != null) {
            set(prefix + ".sshKeyPath", remote.getSshKeyPath());
        }
        if (remote.getLocalMode() != null) {
            set(prefix + ".localMode", remote.getLocalMode());
        }
        if (remote.getLocalUser() != null) {
            set(prefix + ".localUser", remote.getLocalUser());
        }
        if (remote.getLocalJwtSecret() != null) {
            set(prefix + ".localJwtSecret", remote.getLocalJwtSecret());
        }
    }

    /**
     * Remove a remote
     */
    public void removeRemote(String name) {
        String prefix = "remote." + name;
        List<String> keysToRemove = properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(prefix + "."))
                .collect(Collectors.toList());
        
        for (String key : keysToRemove) {
            properties.remove(key);
        }
    }

    /**
     * Get the default remote name
     */
    public String getDefaultRemote() {
        return get("default.remote", "origin");
    }

    /**
     * Set the default remote name
     */
    public void setDefaultRemote(String remoteName) {
        set("default.remote", remoteName);
    }

    /**
     * Get remote URL by name, or fall back to legacy mms.url
     */
    public String getRemoteUrl(String remoteName) {
        if (remoteName == null || remoteName.isEmpty()) {
            remoteName = getDefaultRemote();
        }
        
        Remote remote = getRemote(remoteName);
        if (remote != null) {
            return remote.getUrl();
        }
        
        // Fall back to legacy mms.url for backward compatibility
        return getMmsUrl();
    }

    /**
     * Check if a remote with the given name exists
     */
    public boolean hasRemote(String name) {
        return get("remote." + name + ".url") != null;
    }
}
