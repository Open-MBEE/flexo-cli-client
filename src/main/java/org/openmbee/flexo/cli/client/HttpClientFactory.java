package org.openmbee.flexo.cli.client;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Factory for creating configured HTTP clients with proxy support.
 * Supports automatic proxy configuration from environment variables:
 * - HTTP_PROXY / http_proxy
 * - HTTPS_PROXY / https_proxy
 * - NO_PROXY / no_proxy
 */
public class HttpClientFactory {
    private static final Logger logger = LoggerFactory.getLogger(HttpClientFactory.class);

    private final FlexoConfig config;

    public HttpClientFactory(FlexoConfig config) {
        this.config = config;
    }

    /**
     * Create an HTTP client with proxy configuration from environment/config
     */
    public CloseableHttpClient createClient() {
        HttpClientBuilder builder = HttpClients.custom();

        // Configure timeouts
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                .setResponseTimeout(Timeout.ofSeconds(30))
                .build();
        builder.setDefaultRequestConfig(requestConfig);

        // Configure proxy if available
        configureProxy(builder);

        return builder.build();
    }

    /**
     * Configure proxy settings for the HTTP client
     */
    private void configureProxy(HttpClientBuilder builder) {
        String httpProxyUrl = config.getHttpProxyUrl();
        String httpsProxyUrl = config.getHttpsProxyUrl();
        String noProxy = config.getProxyExclusions();

        // Determine which proxy to use (prefer HTTPS proxy for HTTPS connections)
        String proxyUrl = httpsProxyUrl != null ? httpsProxyUrl : httpProxyUrl;

        if (proxyUrl == null || proxyUrl.isEmpty()) {
            logger.debug("No proxy configuration found");
            return;
        }

        try {
            HttpHost proxyHost = parseProxyUrl(proxyUrl);

            // Apply proxy credentials if the URL contains userinfo (user:pass@host)
            configureProxyCredentials(builder, proxyUrl, proxyHost);

            if (noProxy != null && !noProxy.isEmpty()) {
                // Parse NO_PROXY exclusions
                Set<String> excludedHosts = parseNoProxy(noProxy);
                logger.info("Configuring proxy {} with exclusions: {}", proxyHost, excludedHosts);
                
                // Create custom proxy route planner with exclusions
                DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxyHost) {
                    @Override
                    protected HttpHost determineProxy(HttpHost target, org.apache.hc.core5.http.protocol.HttpContext context) 
                            throws org.apache.hc.core5.http.HttpException {
                        // Check if target should bypass proxy
                        if (shouldBypassProxy(target.getHostName(), excludedHosts)) {
                            logger.debug("Bypassing proxy for host: {}", target.getHostName());
                            return null;
                        }
                        return super.determineProxy(target, context);
                    }
                };
                builder.setRoutePlanner(routePlanner);
            } else {
                logger.info("Configuring proxy: {}", proxyHost);
                builder.setProxy(proxyHost);
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid proxy URL '{}': {}", proxyUrl, e.getMessage());
        }
    }

    /**
     * Configure proxy authentication credentials if the proxy URL contains userinfo
     * in the form scheme://user:pass@host:port.
     */
    private void configureProxyCredentials(HttpClientBuilder builder, String proxyUrl, HttpHost proxyHost) {
        UsernamePasswordCredentials credentials = parseProxyCredentials(proxyUrl);
        if (credentials == null) {
            return;
        }
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(proxyHost), credentials);
        builder.setDefaultCredentialsProvider(credentialsProvider);
        logger.info("Configured proxy authentication for user: {}", credentials.getUserName());
    }

    /**
     * Extract username/password credentials from a proxy URL's userinfo section.
     * Returns null when no credentials are present or the URL has no scheme.
     */
    static UsernamePasswordCredentials parseProxyCredentials(String proxyUrl) {
        if (proxyUrl == null || !proxyUrl.contains("://")) {
            return null;
        }
        try {
            URI uri = new URI(proxyUrl);
            String userInfo = uri.getUserInfo();
            if (userInfo == null || userInfo.isEmpty()) {
                return null;
            }
            String[] parts = userInfo.split(":", 2);
            String user = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String pass = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            return new UsernamePasswordCredentials(user, pass.toCharArray());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * Parse proxy URL into HttpHost
     * Supports formats: http://proxy:8080, proxy:8080, proxy.example.com:3128
     */
    static HttpHost parseProxyUrl(String proxyUrl) {
        try {
            // Try parsing as full URL
            if (proxyUrl.contains("://")) {
                URI uri = new URI(proxyUrl);
                // A malformed authority (e.g. a non-numeric port) makes URI parsing
                // silently yield a null host/scheme instead of throwing.
                if (uri.getHost() == null || uri.getScheme() == null) {
                    throw new IllegalArgumentException("Invalid proxy URL format: " + proxyUrl);
                }
                int port = uri.getPort();
                if (port == -1) {
                    port = uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
                }
                return new HttpHost(uri.getScheme(), uri.getHost(), port);
            } else {
                // Parse as host:port
                String[] parts = proxyUrl.split(":", 2);
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 8080;
                return new HttpHost("http", host, port);
            }
        } catch (URISyntaxException | NumberFormatException e) {
            throw new IllegalArgumentException("Invalid proxy URL format: " + proxyUrl, e);
        }
    }

    /**
     * Parse NO_PROXY environment variable into set of excluded hosts
     * Supports comma-separated list: localhost,.example.com,192.168.1.0/24
     */
    static Set<String> parseNoProxy(String noProxy) {
        Set<String> excluded = new HashSet<>();
        if (noProxy != null && !noProxy.isEmpty()) {
            Arrays.stream(noProxy.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(excluded::add);
        }
        return excluded;
    }

    /**
     * Check if a host should bypass the proxy based on NO_PROXY rules
     */
    static boolean shouldBypassProxy(String host, Set<String> excludedHosts) {
        if (host == null || excludedHosts.isEmpty()) {
            return false;
        }

        String hostLower = host.toLowerCase();

        for (String excluded : excludedHosts) {
            String excludedLower = excluded.toLowerCase();

            // Exact match
            if (hostLower.equals(excludedLower)) {
                return true;
            }

            // Domain suffix match (e.g., .example.com matches api.example.com)
            if (excludedLower.startsWith(".") && hostLower.endsWith(excludedLower)) {
                return true;
            }

            // Domain suffix match without leading dot
            if (!excludedLower.startsWith(".") && hostLower.endsWith("." + excludedLower)) {
                return true;
            }

            // Localhost variations
            if (excludedLower.equals("localhost") && 
                (hostLower.equals("localhost") || hostLower.equals("127.0.0.1") || hostLower.equals("::1"))) {
                return true;
            }

            // CIDR notation for IP ranges (e.g., 192.168.1.0/24)
            if (excludedLower.contains("/") && isIpInCidr(hostLower, excludedLower)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check whether the given host resolves to an IP address contained in the
     * supplied CIDR range (e.g. 192.168.1.0/24 or 2001:db8::/32). Returns false
     * for malformed CIDR, unresolvable hosts, or an address-family mismatch.
     */
    static boolean isIpInCidr(String host, String cidr) {
        try {
            int slash = cidr.indexOf('/');
            String network = cidr.substring(0, slash);
            int prefixLen = Integer.parseInt(cidr.substring(slash + 1).trim());

            byte[] networkBytes = InetAddress.getByName(network).getAddress();
            byte[] hostBytes = InetAddress.getByName(host).getAddress();

            // Address families must match (both IPv4 or both IPv6)
            if (networkBytes.length != hostBytes.length) {
                return false;
            }
            int maxPrefix = networkBytes.length * 8;
            if (prefixLen < 0 || prefixLen > maxPrefix) {
                return false;
            }

            int fullBytes = prefixLen / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (networkBytes[i] != hostBytes[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLen % 8;
            if (remainingBits > 0) {
                int mask = 0xFF << (8 - remainingBits);
                if ((networkBytes[fullBytes] & mask) != (hostBytes[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException | UnknownHostException | IndexOutOfBoundsException e) {
            logger.debug("Ignoring malformed or unresolvable CIDR exclusion '{}' for host '{}'", cidr, host);
            return false;
        }
    }
}
