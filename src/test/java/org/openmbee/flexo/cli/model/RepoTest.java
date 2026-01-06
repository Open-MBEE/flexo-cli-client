package org.openmbee.flexo.cli.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RepoTest {

    @Test
    void testNoArgConstructor() {
        Repo repo = new Repo();
        assertNotNull(repo);
        assertNull(repo.getId());
        assertNull(repo.getName());
        assertNull(repo.getOrgId());
        assertNull(repo.getEtag());
    }

    @Test
    void testParameterizedConstructor() {
        Repo repo = new Repo("repo-123", "my-repo", "org-456");
        assertNotNull(repo);
        assertEquals("repo-123", repo.getId());
        assertEquals("my-repo", repo.getName());
        assertEquals("org-456", repo.getOrgId());
        assertNull(repo.getEtag());
    }

    @Test
    void testSettersAndGetters() {
        Repo repo = new Repo();

        repo.setId("repo-789");
        assertEquals("repo-789", repo.getId());

        repo.setName("test-repo");
        assertEquals("test-repo", repo.getName());

        repo.setOrgId("org-123");
        assertEquals("org-123", repo.getOrgId());

        repo.setEtag("etag-xyz");
        assertEquals("etag-xyz", repo.getEtag());
    }

    @Test
    void testToString() {
        Repo repo = new Repo("id1", "repo1", "org1");

        String result = repo.toString();
        assertNotNull(result);
        assertTrue(result.contains("id1"));
        assertTrue(result.contains("repo1"));
        assertTrue(result.contains("org1"));
    }

    @Test
    void testToStringWithNullValues() {
        Repo repo = new Repo();
        String result = repo.toString();
        assertNotNull(result);
        assertTrue(result.contains("Repo"));
    }

    @Test
    void testSetNullValues() {
        Repo repo = new Repo("id", "name", "orgId");

        repo.setId(null);
        assertNull(repo.getId());

        repo.setName(null);
        assertNull(repo.getName());

        repo.setOrgId(null);
        assertNull(repo.getOrgId());

        repo.setEtag(null);
        assertNull(repo.getEtag());
    }
}
