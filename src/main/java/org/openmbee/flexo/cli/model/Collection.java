package org.openmbee.flexo.cli.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a collection in Flexo MMS.
 *
 * A collection is a lightweight grouping of refs (branches, locks or
 * scratches) that can be queried as a single union graph.
 */
public class Collection {
    private String id;
    private String name;
    private String etag;
    private final List<String> collectedRefs = new ArrayList<>();

    public Collection() {
    }

    public Collection(String id, String name) {
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

    public List<String> getCollectedRefs() {
        return collectedRefs;
    }

    public void addCollectedRef(String refIri) {
        if (refIri != null) {
            collectedRefs.add(refIri);
        }
    }

    @Override
    public String toString() {
        return "Collection{id='" + id + "', name='" + name + "', collects=" + collectedRefs.size() + "}";
    }
}
