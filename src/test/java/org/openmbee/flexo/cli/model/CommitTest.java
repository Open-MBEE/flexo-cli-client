package org.openmbee.flexo.cli.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class CommitTest {

    @Test
    void testNoArgConstructor() {
        Commit commit = new Commit();
        assertNotNull(commit);
        assertNull(commit.getId());
        assertNull(commit.getMessage());
        assertNull(commit.getParentId());
        assertNull(commit.getCreatedBy());
        assertNull(commit.getSubmitted());
        assertNull(commit.getEtag());
    }

    @Test
    void testParameterizedConstructor() {
        Commit commit = new Commit("commit-123", "Initial commit");
        assertNotNull(commit);
        assertEquals("commit-123", commit.getId());
        assertEquals("Initial commit", commit.getMessage());
        assertNull(commit.getParentId());
        assertNull(commit.getCreatedBy());
        assertNull(commit.getSubmitted());
        assertNull(commit.getEtag());
    }

    @Test
    void testSettersAndGetters() {
        Commit commit = new Commit();

        commit.setId("commit-456");
        assertEquals("commit-456", commit.getId());

        commit.setMessage("Test commit");
        assertEquals("Test commit", commit.getMessage());

        commit.setParentId("parent-789");
        assertEquals("parent-789", commit.getParentId());

        commit.setCreatedBy("user123");
        assertEquals("user123", commit.getCreatedBy());

        Instant now = Instant.now();
        commit.setSubmitted(now);
        assertEquals(now, commit.getSubmitted());

        commit.setEtag("etag-xyz");
        assertEquals("etag-xyz", commit.getEtag());
    }

    @Test
    void testToString() {
        Commit commit = new Commit("id1", "Fix bug");
        commit.setParentId("parent1");

        String result = commit.toString();
        assertNotNull(result);
        assertTrue(result.contains("id1"));
        assertTrue(result.contains("Fix bug"));
        assertTrue(result.contains("parent1"));
    }

    @Test
    void testToStringWithNullValues() {
        Commit commit = new Commit();
        String result = commit.toString();
        assertNotNull(result);
        assertTrue(result.contains("Commit"));
    }

    @Test
    void testSetNullValues() {
        Commit commit = new Commit("id", "message");

        commit.setId(null);
        assertNull(commit.getId());

        commit.setMessage(null);
        assertNull(commit.getMessage());

        commit.setParentId(null);
        assertNull(commit.getParentId());

        commit.setCreatedBy(null);
        assertNull(commit.getCreatedBy());

        commit.setSubmitted(null);
        assertNull(commit.getSubmitted());

        commit.setEtag(null);
        assertNull(commit.getEtag());
    }
}
