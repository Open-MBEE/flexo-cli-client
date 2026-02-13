package org.openmbee.flexo.cli.commands;

/**
 * Exception thrown when attempting to create a resource that already exists.
 */
public class ResourceAlreadyExistsException extends CommandExecutionException {

    private final String resourceType;
    private final String resourceId;

    public ResourceAlreadyExistsException(String resourceType, String resourceId, String suggestion) {
        super(resourceType + " '" + resourceId + "' already exists. " + suggestion, 1);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}
