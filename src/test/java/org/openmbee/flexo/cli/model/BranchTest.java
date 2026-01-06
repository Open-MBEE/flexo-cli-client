package org.openmbee.flexo.cli.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BranchTest {

    @Test
    void testNoArgConstructor() {
        Branch branch = new Branch();
        assertNotNull(branch);
        assertNull(branch.getId());
        assertNull(branch.getName());
        assertNull(branch.getCommitId());
        assertNull(branch.getEtag());
        assertNull(branch.getParentCommit());
    }

    @Test
    void testParameterizedConstructor() {
        Branch branch = new Branch("branch-123", "main");
        assertNotNull(branch);
        assertEquals("branch-123", branch.getId());
        assertEquals("main", branch.getName());
        assertNull(branch.getCommitId());
        assertNull(branch.getEtag());
        assertNull(branch.getParentCommit());
    }

    @Test
    void testSettersAndGetters() {
        Branch branch = new Branch();

        branch.setId("branch-456");
        assertEquals("branch-456", branch.getId());

        branch.setName("develop");
        assertEquals("develop", branch.getName());

        branch.setCommitId("commit-789");
        assertEquals("commit-789", branch.getCommitId());

        branch.setEtag("etag-abc");
        assertEquals("etag-abc", branch.getEtag());

        branch.setParentCommit("parent-def");
        assertEquals("parent-def", branch.getParentCommit());
    }

    @Test
    void testToString() {
        Branch branch = new Branch("id1", "feature");
        branch.setCommitId("commit1");

        String result = branch.toString();
        assertNotNull(result);
        assertTrue(result.contains("id1"));
        assertTrue(result.contains("feature"));
        assertTrue(result.contains("commit1"));
    }

    @Test
    void testToStringWithNullValues() {
        Branch branch = new Branch();
        String result = branch.toString();
        assertNotNull(result);
        assertTrue(result.contains("Branch"));
    }

    @Test
    void testSetNullValues() {
        Branch branch = new Branch("id", "name");

        branch.setId(null);
        assertNull(branch.getId());

        branch.setName(null);
        assertNull(branch.getName());

        branch.setCommitId(null);
        assertNull(branch.getCommitId());

        branch.setEtag(null);
        assertNull(branch.getEtag());

        branch.setParentCommit(null);
        assertNull(branch.getParentCommit());
    }
}
