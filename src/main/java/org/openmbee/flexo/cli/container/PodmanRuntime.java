package org.openmbee.flexo.cli.container;

import java.io.File;

/**
 * Podman container runtime implementation.
 * Supports both "podman-compose" and native "podman" compose commands.
 */
public class PodmanRuntime extends AbstractContainerRuntime {
    
    private static final String[] PODMAN_PATHS = {
        "podman",                         // In PATH
        "/usr/bin/podman",                // Linux standard
        "/usr/local/bin/podman",          // macOS/Linux
        "/opt/homebrew/bin/podman",       // macOS Homebrew
        "C:\\Program Files\\RedHat\\Podman\\podman.exe"  // Windows
    };
    
    private static final String[] PODMAN_COMPOSE_PATHS = {
        "podman-compose",                 // In PATH
        "/usr/bin/podman-compose",
        "/usr/local/bin/podman-compose",
        "/opt/homebrew/bin/podman-compose"
    };
    
    private String podmanPath;
    private String[] composeCommand;
    
    public PodmanRuntime(boolean verbose) {
        super(verbose);
        detectPodmanPaths();
    }
    
    private void detectPodmanPaths() {
        // Find podman executable
        for (String path : PODMAN_PATHS) {
            if (isExecutableAvailable(path)) {
                this.podmanPath = path;
                break;
            }
        }
        
        if (podmanPath == null) {
            return;
        }
        
        // Try "podman compose" (newer versions with native compose support)
        try {
            ProcessBuilder pb = new ProcessBuilder(podmanPath, "compose", "version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            if (process.waitFor() == 0) {
                this.composeCommand = new String[] { podmanPath, "compose" };
                return;
            }
        } catch (Exception e) {
            // Fall through to try podman-compose
        }
        
        // Try standalone "podman-compose"
        for (String path : PODMAN_COMPOSE_PATHS) {
            if (isExecutableAvailable(path)) {
                this.composeCommand = new String[] { path };
                return;
            }
        }
    }
    
    private boolean isExecutableAvailable(String path) {
        File file = new File(path);
        if (file.exists() && file.canExecute()) {
            return true;
        }
        
        // Try executing via PATH
        try {
            ProcessBuilder pb = new ProcessBuilder(path, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public String getName() {
        return "podman";
    }
    
    @Override
    protected String getExecutablePath() {
        return podmanPath;
    }
    
    @Override
    protected String[] getComposeCommand() {
        return composeCommand;
    }
    
    @Override
    public boolean isAvailable() {
        return podmanPath != null && composeCommand != null && super.isAvailable();
    }
    
    @Override
    public int getPriority() {
        return 90; // Podman is second choice after Docker
    }
}
