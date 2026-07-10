package org.openmbee.flexo.cli.client;

import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.openmbee.flexo.cli.config.FlexoConfig;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientFactoryTest {

    // ---- createClient ----

    @Test
    void testCreateClientWithoutProxy() {
        FlexoConfig config = new FlexoConfig();
        HttpClientFactory factory = new HttpClientFactory(config);
        try (CloseableHttpClient client = factory.createClient()) {
            assertNotNull(client);
        } catch (Exception e) {
            fail("createClient should not throw: " + e.getMessage());
        }
    }

    @Test
    void testCreateClientWithProxyConfig() {
        FlexoConfig config = new FlexoConfig();
        config.set("proxy.https.url", "http://proxy.example.com:8080");
        HttpClientFactory factory = new HttpClientFactory(config);
        try (CloseableHttpClient client = factory.createClient()) {
            assertNotNull(client);
        } catch (Exception e) {
            fail("createClient with proxy should not throw: " + e.getMessage());
        }
    }

    @Test
    void testCreateClientWithProxyAndExclusions() {
        FlexoConfig config = new FlexoConfig();
        config.set("proxy.https.url", "http://proxy.example.com:8080");
        config.set("proxy.no", "localhost,.internal.corp");
        HttpClientFactory factory = new HttpClientFactory(config);
        try (CloseableHttpClient client = factory.createClient()) {
            assertNotNull(client);
        } catch (Exception e) {
            fail("createClient with exclusions should not throw: " + e.getMessage());
        }
    }

    @Test
    void testCreateClientWithInvalidProxyDoesNotThrow() {
        FlexoConfig config = new FlexoConfig();
        // Malformed port -> parseProxyUrl throws IllegalArgumentException, caught internally
        config.set("proxy.https.url", "http://proxy.example.com:notaport");
        HttpClientFactory factory = new HttpClientFactory(config);
        try (CloseableHttpClient client = factory.createClient()) {
            assertNotNull(client);
        } catch (Exception e) {
            fail("Invalid proxy should be swallowed, not thrown: " + e.getMessage());
        }
    }

    // ---- parseProxyUrl ----

    @Test
    void testParseProxyUrlFullUrl() {
        HttpHost host = HttpClientFactory.parseProxyUrl("http://proxy.example.com:8080");
        assertEquals("proxy.example.com", host.getHostName());
        assertEquals(8080, host.getPort());
        assertEquals("http", host.getSchemeName());
    }

    @Test
    void testParseProxyUrlHttpsDefaultPort() {
        HttpHost host = HttpClientFactory.parseProxyUrl("https://secure.example.com");
        assertEquals("secure.example.com", host.getHostName());
        assertEquals(443, host.getPort());
        assertEquals("https", host.getSchemeName());
    }

    @Test
    void testParseProxyUrlHttpDefaultPort() {
        HttpHost host = HttpClientFactory.parseProxyUrl("http://plain.example.com");
        assertEquals("plain.example.com", host.getHostName());
        assertEquals(80, host.getPort());
    }

    @Test
    void testParseProxyUrlHostPortOnly() {
        HttpHost host = HttpClientFactory.parseProxyUrl("proxy.example.com:3128");
        assertEquals("proxy.example.com", host.getHostName());
        assertEquals(3128, host.getPort());
        assertEquals("http", host.getSchemeName());
    }

    @Test
    void testParseProxyUrlHostOnlyDefaultsTo8080() {
        HttpHost host = HttpClientFactory.parseProxyUrl("proxy.example.com");
        assertEquals("proxy.example.com", host.getHostName());
        assertEquals(8080, host.getPort());
    }

    @Test
    void testParseProxyUrlWithCredentialsIgnoresUserInfoForHost() {
        HttpHost host = HttpClientFactory.parseProxyUrl("http://user:pass@proxy.example.com:8080");
        assertEquals("proxy.example.com", host.getHostName());
        assertEquals(8080, host.getPort());
    }

    @Test
    void testParseProxyUrlInvalidPortThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpClientFactory.parseProxyUrl("proxy.example.com:notaport"));
    }

    // ---- parseProxyCredentials ----

    @Test
    void testParseProxyCredentialsPresent() {
        UsernamePasswordCredentials creds =
                HttpClientFactory.parseProxyCredentials("http://alice:s3cret@proxy.example.com:8080");
        assertNotNull(creds);
        assertEquals("alice", creds.getUserName());
        assertEquals("s3cret", new String(creds.getUserPassword()));
    }

    @Test
    void testParseProxyCredentialsUrlEncoded() {
        UsernamePasswordCredentials creds =
                HttpClientFactory.parseProxyCredentials("http://alice:p%40ss%3Aword@proxy.example.com:8080");
        assertNotNull(creds);
        assertEquals("alice", creds.getUserName());
        assertEquals("p@ss:word", new String(creds.getUserPassword()));
    }

    @Test
    void testParseProxyCredentialsAbsent() {
        assertNull(HttpClientFactory.parseProxyCredentials("http://proxy.example.com:8080"));
    }

    @Test
    void testParseProxyCredentialsNoScheme() {
        assertNull(HttpClientFactory.parseProxyCredentials("proxy.example.com:8080"));
    }

    @Test
    void testParseProxyCredentialsNull() {
        assertNull(HttpClientFactory.parseProxyCredentials(null));
    }

    // ---- parseNoProxy ----

    @Test
    void testParseNoProxyMultiple() {
        Set<String> excluded = HttpClientFactory.parseNoProxy("localhost, .example.com ,192.168.1.1");
        assertEquals(3, excluded.size());
        assertTrue(excluded.contains("localhost"));
        assertTrue(excluded.contains(".example.com"));
        assertTrue(excluded.contains("192.168.1.1"));
    }

    @Test
    void testParseNoProxyEmpty() {
        assertTrue(HttpClientFactory.parseNoProxy("").isEmpty());
        assertTrue(HttpClientFactory.parseNoProxy(null).isEmpty());
    }

    // ---- shouldBypassProxy ----

    @Test
    void testShouldBypassExactMatch() {
        Set<String> excluded = HttpClientFactory.parseNoProxy("api.example.com");
        assertTrue(HttpClientFactory.shouldBypassProxy("api.example.com", excluded));
        assertTrue(HttpClientFactory.shouldBypassProxy("API.EXAMPLE.COM", excluded));
    }

    @Test
    void testShouldBypassDomainSuffixWithDot() {
        Set<String> excluded = HttpClientFactory.parseNoProxy(".example.com");
        assertTrue(HttpClientFactory.shouldBypassProxy("api.example.com", excluded));
        assertTrue(HttpClientFactory.shouldBypassProxy("www.example.com", excluded));
        assertFalse(HttpClientFactory.shouldBypassProxy("notexample.com", excluded));
    }

    @Test
    void testShouldBypassDomainSuffixWithoutDot() {
        Set<String> excluded = HttpClientFactory.parseNoProxy("example.com");
        assertTrue(HttpClientFactory.shouldBypassProxy("api.example.com", excluded));
        // Bare "example.com" only matches subdomains via ".example.com" suffix rule,
        // not the apex itself under the no-dot branch.
        assertFalse(HttpClientFactory.shouldBypassProxy("other.com", excluded));
    }

    @Test
    void testShouldBypassLocalhostVariations() {
        Set<String> excluded = HttpClientFactory.parseNoProxy("localhost");
        assertTrue(HttpClientFactory.shouldBypassProxy("localhost", excluded));
        assertTrue(HttpClientFactory.shouldBypassProxy("127.0.0.1", excluded));
        assertTrue(HttpClientFactory.shouldBypassProxy("::1", excluded));
    }

    @Test
    void testShouldNotBypassUnlistedHost() {
        Set<String> excluded = HttpClientFactory.parseNoProxy("localhost,.internal.corp");
        assertFalse(HttpClientFactory.shouldBypassProxy("public.example.com", excluded));
    }

    @Test
    void testShouldNotBypassWhenNoExclusions() {
        assertFalse(HttpClientFactory.shouldBypassProxy("any.host.com", Set.of()));
    }

    @Test
    void testShouldNotBypassNullHost() {
        Set<String> excluded = HttpClientFactory.parseNoProxy("localhost");
        assertFalse(HttpClientFactory.shouldBypassProxy(null, excluded));
    }

    // ---- CIDR ----

    @Test
    void testShouldBypassCidrIpv4InRange() {
        Set<String> excluded = HttpClientFactory.parseNoProxy("192.168.1.0/24");
        assertTrue(HttpClientFactory.shouldBypassProxy("192.168.1.50", excluded));
        assertTrue(HttpClientFactory.shouldBypassProxy("192.168.1.255", excluded));
    }

    @Test
    void testShouldNotBypassCidrIpv4OutOfRange() {
        Set<String> excluded = HttpClientFactory.parseNoProxy("192.168.1.0/24");
        assertFalse(HttpClientFactory.shouldBypassProxy("192.168.2.1", excluded));
    }

    @Test
    void testShouldBypassCidrNonByteAlignedPrefix() {
        // /28 -> 192.168.1.0-192.168.1.15
        Set<String> excluded = HttpClientFactory.parseNoProxy("192.168.1.0/28");
        assertTrue(HttpClientFactory.shouldBypassProxy("192.168.1.15", excluded));
        assertFalse(HttpClientFactory.shouldBypassProxy("192.168.1.16", excluded));
    }

    @Test
    void testCidrIsIpInCidrDirect() {
        assertTrue(HttpClientFactory.isIpInCidr("10.0.0.5", "10.0.0.0/8"));
        assertFalse(HttpClientFactory.isIpInCidr("11.0.0.5", "10.0.0.0/8"));
    }

    @Test
    void testCidrIpv6InRange() {
        assertTrue(HttpClientFactory.isIpInCidr("2001:db8::1", "2001:db8::/32"));
        assertFalse(HttpClientFactory.isIpInCidr("2001:dead::1", "2001:db8::/32"));
    }

    @Test
    void testCidrAddressFamilyMismatch() {
        // IPv4 host against IPv6 CIDR must not match
        assertFalse(HttpClientFactory.isIpInCidr("192.168.1.1", "2001:db8::/32"));
    }

    @Test
    void testCidrMalformedIsIgnored() {
        assertFalse(HttpClientFactory.isIpInCidr("192.168.1.1", "192.168.1.0/notaprefix"));
        assertFalse(HttpClientFactory.isIpInCidr("192.168.1.1", "192.168.1.0/40"));
    }
}
