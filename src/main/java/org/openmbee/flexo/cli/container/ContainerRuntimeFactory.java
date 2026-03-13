package org.openmbee.flexo.cli.container;

import org.openmbee.flexo.cli.config.FlexoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Factory for creating and managing container runtime instances.
 * Automatically detects available container runtimes and selects the best one.
 */
public class ContainerRuntimeFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(ContainerRuntimeFactory.class);
    
    /**
     * Get the best available container runtime based on system detection and user preference.
     * 
     * @param config Flexo configuration (may contain user preference)
     * @param verbose Whether to enable verbose logging
     * @return The selected container runtime
     * @throws ContainerException if no container runtime is available
     */
    public static ContainerRuntime getRuntime(FlexoConfig config, boolean verbose) throws ContainerException {
        // Check if user has specified a preferred runtime
        String preferredRuntime = config != null ? config.get("container.runtime") : null;
        
        // Create all available runtime implementations
        List<ContainerRuntime> runtimes = Arrays.asList(
            new DockerRuntime(verbose),
            new ColimaRuntime(verbose),
            new PodmanRuntime(verbose)
        );
        
        // Filter to only available runtimes
        List<ContainerRuntime> availableRuntimes = runtimes.stream()
            .filter(runtime -> {
                boolean available = runtime.isAvailable();
                if (verbose && available) {
                    logger.debug("Container runtime '{}' is available", runtime.getName());
                }
                return available;
            })
            .collect(Collectors.toList());
        
        if (availableRuntimes.isEmpty()) {
            throw new ContainerException(
                "No container runtime detected. Please install one of:\n" +
                "  - Docker Desktop (https://www.docker.com/products/docker-desktop)\n" +
                "  - Podman (https://podman.io/)\n" +
                "  - Colima (https://github.com/abiosoft/colima)"
            );
        }
        
        // If user has a preference, try to use it
        if (preferredRuntime != null && !preferredRuntime.isEmpty()) {
            Optional<ContainerRuntime> preferred = availableRuntimes.stream()
                .filter(runtime -> runtime.getName().equalsIgnoreCase(preferredRuntime))
                .findFirst();
            
            if (preferred.isPresent()) {
                if (verbose) {
                    logger.info("Using preferred container runtime: {}", preferred.get().getName());
                }
                return preferred.get();
            } else {
                logger.warn("Preferred container runtime '{}' not available, using auto-detection", 
                    preferredRuntime);
            }
        }
        
        // Sort by priority (highest first) and select the best one
        ContainerRuntime selected = availableRuntimes.stream()
            .max(Comparator.comparingInt(ContainerRuntime::getPriority))
            .orElseThrow(() -> new ContainerException("No container runtime available"));
        
        if (verbose) {
            logger.info("Auto-detected container runtime: {}", selected.getName());
        }
        
        return selected;
    }
    
    /**
     * List all available container runtimes on the system
     */
    public static List<ContainerRuntime> listAvailableRuntimes(boolean verbose) {
        List<ContainerRuntime> runtimes = Arrays.asList(
            new DockerRuntime(verbose),
            new ColimaRuntime(verbose),
            new PodmanRuntime(verbose)
        );
        
        return runtimes.stream()
            .filter(ContainerRuntime::isAvailable)
            .sorted(Comparator.comparingInt(ContainerRuntime::getPriority).reversed())
            .collect(Collectors.toList());
    }
    
    /**
     * Check if any container runtime is available
     */
    public static boolean isAnyRuntimeAvailable(boolean verbose) {
        return !listAvailableRuntimes(verbose).isEmpty();
    }
}
