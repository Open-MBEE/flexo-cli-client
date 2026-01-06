package org.openmbee.flexo.cli.model;

/**
 * Represents a branch in Flexo MMS
 */
public class Branch {
    private String id;
    private String name;
    private String commitId;
    private String etag;
    private String parentCommit;

    public Branch() {
    }

    public Branch(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCommitId() {
        return commitId;
    }

    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public String getParentCommit() {
        return parentCommit;
    }

    public void setParentCommit(String parentCommit) {
        this.parentCommit = parentCommit;
    }

    @Override
    public String toString() {
        return "Branch{id='" + id + "', name='" + name + "', commitId='" + commitId + "'}";
    }
}
