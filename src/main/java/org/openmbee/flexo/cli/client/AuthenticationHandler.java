package org.openmbee.flexo.cli.client;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Security;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Handles SSH key-based JWT authentication for Flexo MMS
 * Also supports local mode with hardcoded user and HMAC-based JWT
 */
public class AuthenticationHandler {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationHandler.class);

    static {
        // Add BouncyCastle as security provider
        Security.addProvider(new BouncyCastleProvider());
    }

    private final boolean enabled;
    private final String sshKeyPath;
    private final boolean localMode;
    private final String localUser;
    private final String localJwtSecret;
    private PrivateKey privateKey;
    private String cachedToken;
    private Instant tokenExpiry;

    public AuthenticationHandler(boolean enabled, String sshKeyPath) {
        this(enabled, sshKeyPath, false, null, null);
    }

    public AuthenticationHandler(boolean enabled, String sshKeyPath, boolean localMode, String localUser, String localJwtSecret) {
        this.enabled = enabled;
        this.sshKeyPath = sshKeyPath;
        this.localMode = localMode;
        this.localUser = localUser;
        this.localJwtSecret = localJwtSecret;

        // Validate JWT secret in local mode
        if (localMode) {
            validateJwtSecret(localJwtSecret);
        }

        if (enabled && !localMode) {
            loadPrivateKey();
        }
    }

    /**
     * Validate JWT secret for security
     */
    private void validateJwtSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            logger.error("SECURITY WARNING: local.jwtSecret is not configured. JWT authentication will fail.");
            logger.error("Please set local.jwtSecret in ~/.flexo/config");
            return;
        }

        if (secret.length() < 32) {
            logger.warn("SECURITY WARNING: local.jwtSecret is too short (minimum 32 characters recommended)");
        }

        // Check for common weak secrets
        String[] weakSecrets = {
            "devsecret",
            "secret",
            "password",
            "changeme",
            "test",
            "dev",
            "local"
        };

        String lowerSecret = secret.toLowerCase();
        for (String weak : weakSecrets) {
            if (lowerSecret.contains(weak)) {
                logger.warn("SECURITY WARNING: local.jwtSecret appears to contain weak or default values");
                logger.warn("Please use a strong, randomly generated secret for production use");
                break;
            }
        }
    }

    /**
     * Load SSH private key from file
     */
    private void loadPrivateKey() {
        if (sshKeyPath == null || sshKeyPath.isEmpty()) {
            logger.warn("SSH key path not configured");
            return;
        }

        try (PEMParser pemParser = new PEMParser(new FileReader(sshKeyPath))) {
            Object object = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

            if (object instanceof PEMKeyPair) {
                KeyPair keyPair = converter.getKeyPair((PEMKeyPair) object);
                this.privateKey = keyPair.getPrivate();
                logger.info("Loaded SSH private key from: {}", sshKeyPath);
            } else if (object instanceof KeyPair) {
                this.privateKey = ((KeyPair) object).getPrivate();
                logger.info("Loaded SSH private key from: {}", sshKeyPath);
            } else {
                logger.error("Unsupported key format in file: {}", sshKeyPath);
            }
        } catch (IOException e) {
            logger.error("Failed to load SSH private key: {}", e.getMessage());
        }
    }

    /**
     * Get JWT token for authentication
     * Returns cached token if still valid, otherwise generates new one
     */
    public String getToken() {
        if (!enabled && !localMode) {
            return null;
        }

        // Return cached token if still valid
        if (cachedToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }

        // Generate new token (local mode or SSH key mode)
        return generateToken();
    }

    /**
     * Generate a new JWT token signed with SSH private key or HMAC secret
     */
    private String generateToken() {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plus(1, ChronoUnit.HOURS);

            String token;

            if (localMode) {
                // Local mode: generate HMAC-based JWT with hardcoded user
                if (localJwtSecret == null || localJwtSecret.isEmpty()) {
                    logger.error("Cannot generate token: local JWT secret not configured");
                    return null;
                }

                String username = localUser != null ? localUser : "root";

                token = Jwts.builder()
                        .issuer("http://localhost:8080/")
                        .audience().add("flexo-mms").and()
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expiry))
                        .claim("username", username)
                        .claim("groups", new java.util.ArrayList<>())
                        .signWith(Keys.hmacShaKeyFor(localJwtSecret.getBytes()))
                        .compact();

                logger.debug("Generated local mode JWT token for user '{}', expires at: {}", username, expiry);
            } else {
                // SSH key mode: generate RSA-based JWT
                if (privateKey == null) {
                    logger.error("Cannot generate token: private key not loaded");
                    return null;
                }

                token = Jwts.builder()
                        .subject("flexo-cli-user")
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expiry))
                        .claim("scope", "flexo:mms")
                        .signWith(privateKey)
                        .compact();

                logger.debug("Generated SSH key JWT token, expires at: {}", expiry);
            }

            // Cache token
            this.cachedToken = token;
            this.tokenExpiry = expiry;

            return token;

        } catch (Exception e) {
            logger.error("Failed to generate JWT token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get Authorization header value
     */
    public String getAuthorizationHeader() {
        if (!enabled && !localMode) {
            return null;
        }

        String token = getToken();
        if (token == null) {
            return null;
        }

        return "Bearer " + token;
    }

    /**
     * Clear cached token (force regeneration on next request)
     */
    public void clearCache() {
        this.cachedToken = null;
        this.tokenExpiry = null;
    }

    public boolean isEnabled() {
        return enabled || localMode;
    }
}
