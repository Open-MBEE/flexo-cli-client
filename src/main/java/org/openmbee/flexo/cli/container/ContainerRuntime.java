package org.openmbee.flexo.cli.container;

import java.io.File;
import java.util.List;

/**
 * Interface for container runtime operations.
 * Supports Docker, Podman, Colima, and other OCI-compliant container runtimes.
 */
public interface ContainerRuntime {
    
    /**
     * Get the name of this container runtime (e.g., "docker", "podman", "colima")
     */
    String getName();
    
    /**
     * Check if this container runtime is available on the system
     */
    boolean isAvailable();
    
    /**
     * Get version information for debugging and compatibility checks
     */
    String getVersion() throws ContainerException;
    
    /**
     * Start services defined in a compose file
     * @param composeFile The compose file to use
     * @param serviceNames List of service names to start (empty = all services)
     * @return true if successful
     */
    boolean composeUp(File composeFile, List<String> serviceNames) throws ContainerException;
    
    /**
     * Stop services defined in a compose file
     * @param composeFile The compose file to use
     * @param serviceNames List of service names to stop (empty = all services)
     * @return true if successful
     */
    boolean composeDown(File composeFile, List<String> serviceNames) throws ContainerException;
    
    /**
     * Check if a container is running
     * @param containerName The name of the container
     * @return true if container is running
     */
    boolean isContainerRunning(String containerName) throws ContainerException;
    
    /**
     * Get logs from a container
     * @param containerName The name of the container
     * @param tail Number of lines to retrieve (0 = all)
     * @return Container logs
     */
    String getContainerLogs(String containerName, int tail) throws ContainerException;
    
    /**
     * Get the priority of this runtime (higher = preferred)
     * Used when multiple runtimes are available
     */
    int getPriority();
}
