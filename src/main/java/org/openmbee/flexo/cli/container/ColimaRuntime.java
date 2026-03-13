package org.openmbee.flexo.cli.container;

import java.io.File;

/**
 * Colima container runtime implementation.
 * Colima uses Docker CLI under the hood, so this wraps Docker commands with Colima context.
 */
public class ColimaRuntime extends AbstractContainerRuntime {
    
    private static final String[] COLIMA_PATHS = {
        "colima",                         // In PATH
        "/usr/local/bin/colima",          // macOS Homebrew
        "/opt/homebrew/bin/colima"        // macOS Apple Silicon Homebrew
    };
    
    private static final String[] DOCKER_PATHS = {
        "docker",
        "/usr/local/bin/docker",
        "/opt/homebrew/bin/docker"
    };
    
    private String colimaPath;
    private String dockerPath;
    private String[] composeCommand;
    
    public ColimaRuntime(boolean verbose) {
        super(verbose);
        detectColimaPaths();
    }
    
    private void detectColimaPaths() {
        // Find colima executable
        for (String path : COLIMA_PATHS) {
            if (isExecutableAvailable(path)) {
                this.colimaPath = path;
                break;
            }
        }
        
        if (colimaPath == null) {
            return;
        }
        
        // Check if Colima is running
        try {
            ProcessBuilder pb = new ProcessBuilder(colimaPath, "status");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            if (process.waitFor() != 0) {
                // Colima is not running
                return;
            }
        } catch (Exception e) {
            return;
        }
        
        // Find docker executable (Colima uses Docker CLI)
        for (String path : DOCKER_PATHS) {
            if (isExecutableAvailable(path)) {
                this.dockerPath = path;
                break;
            }
        }
        
        if (dockerPath == null) {
            return;
        }
        
        // Try "docker compose"
        try {
            ProcessBuilder pb = new ProcessBuilder(dockerPath, "compose", "version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            if (process.waitFor() == 0) {
                this.composeCommand = new String[] { dockerPath, "compose" };
                return;
            }
        } catch (Exception e) {
            // Fall through
        }
        
        // Try "docker-compose"
        if (isExecutableAvailable("docker-compose")) {
            this.composeCommand = new String[] { "docker-compose" };
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
        return "colima";
    }
    
    @Override
    protected String getExecutablePath() {
        return dockerPath; // Colima uses Docker CLI
    }
    
    @Override
    protected String[] getComposeCommand() {
        return composeCommand;
    }
    
    @Override
    public boolean isAvailable() {
        return colimaPath != null && dockerPath != null && composeCommand != null && super.isAvailable();
    }
    
    @Override
    public int getPriority() {
        return 95; // Colima is preferred over Podman but less than Docker
    }
}
