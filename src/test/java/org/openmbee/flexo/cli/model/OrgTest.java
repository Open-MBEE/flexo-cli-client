package org.openmbee.flexo.cli.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrgTest {

    @Test
    void testNoArgConstructor() {
        Org org = new Org();
        assertNotNull(org);
        assertNull(org.getId());
        assertNull(org.getName());
        assertNull(org.getEtag());
    }

    @Test
    void testParameterizedConstructor() {
        Org org = new Org("org-123", "ACME Corporation");
        assertNotNull(org);
        assertEquals("org-123", org.getId());
        assertEquals("ACME Corporation", org.getName());
        assertNull(org.getEtag());
    }

    @Test
    void testSettersAndGetters() {
        Org org = new Org();

        org.setId("org-456");
        assertEquals("org-456", org.getId());

        org.setName("Test Organization");
        assertEquals("Test Organization", org.getName());

        org.setEtag("etag-abc");
        assertEquals("etag-abc", org.getEtag());
    }

    @Test
    void testToString() {
        Org org = new Org("id1", "MyOrg");

        String result = org.toString();
        assertNotNull(result);
        assertTrue(result.contains("id1"));
        assertTrue(result.contains("MyOrg"));
    }

    @Test
    void testToStringWithNullValues() {
        Org org = new Org();
        String result = org.toString();
        assertNotNull(result);
        assertTrue(result.contains("Org"));
    }

    @Test
    void testSetNullValues() {
        Org org = new Org("id", "name");

        org.setId(null);
        assertNull(org.getId());

        org.setName(null);
        assertNull(org.getName());

        org.setEtag(null);
        assertNull(org.getEtag());
    }
}
