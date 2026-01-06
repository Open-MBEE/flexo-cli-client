package org.openmbee.flexo.cli.model;

import java.time.Instant;

/**
 * Represents a commit in Flexo MMS
 */
public class Commit {
    private String id;
    private String message;
    private String parentId;
    private String createdBy;
    private Instant submitted;
    private String etag;

    public Commit() {
    }

    public Commit(String id, String message) {
        this.id = id;
        this.message = message;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getSubmitted() {
        return submitted;
    }

    public void setSubmitted(Instant submitted) {
        this.submitted = submitted;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    @Override
    public String toString() {
        return "Commit{id='" + id + "', message='" + message + "', parentId='" + parentId + "'}";
    }
}
