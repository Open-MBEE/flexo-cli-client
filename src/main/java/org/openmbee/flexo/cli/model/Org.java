package org.openmbee.flexo.cli.model;

/**
 * Represents an organization in Flexo MMS
 */
public class Org {
    private String id;
    private String name;
    private String etag;

    public Org() {
    }

    public Org(String id, String name) {
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

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    @Override
    public String toString() {
        return "Org{id='" + id + "', name='" + name + "'}";
    }
}
