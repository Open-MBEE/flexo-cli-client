package org.openmbee.flexo.cli.model;

/**
 * Represents a repository in Flexo MMS
 */
public class Repo {
    private String id;
    private String name;
    private String orgId;
    private String etag;

    public Repo() {
    }

    public Repo(String id, String name, String orgId) {
        this.id = id;
        this.name = name;
        this.orgId = orgId;
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

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    @Override
    public String toString() {
        return "Repo{id='" + id + "', name='" + name + "', orgId='" + orgId + "'}";
    }
}
