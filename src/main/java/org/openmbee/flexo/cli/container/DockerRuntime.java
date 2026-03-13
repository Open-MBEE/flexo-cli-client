package org.openmbee.flexo.cli.container;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Docker container runtime implementation.
 * Supports both "docker compose" (v2) and "docker-compose" (v1) commands.
 */
public class DockerRuntime extends AbstractContainerRuntime {
    
    private static final String[] DOCKER_PATHS = {
        "docker",                         // In PATH
        "/usr/bin/docker",                // Linux standard
        "/usr/local/bin/docker",          // macOS/Linux
        "/opt/homebrew/bin/docker",       // macOS Apple Silicon
        "C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe",  // Windows
        "/Applications/Docker.app/Contents/Resources/bin/docker"  // macOS Docker Desktop
    };
    
    private static final String[] DOCKER_COMPOSE_PATHS = {
        "docker-compose",                 // In PATH
        "/usr/bin/docker-compose",
        "/usr/local/bin/docker-compose",
        "/opt/homebrew/bin/docker-compose",
        "C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker-compose.exe"
    };
    
    private String dockerPath;
    private String[] composeCommand;
    
    public DockerRuntime(boolean verbose) {
        super(verbose);
        detectDockerPaths();
    }
    
    private void detectDockerPaths() {
        // Find docker executable
        for (String path : DOCKER_PATHS) {
            if (isExecutableAvailable(path)) {
                this.dockerPath = path;
                break;
            }
        }
        
        if (dockerPath == null) {
            return;
        }
        
        // Try "docker compose" (v2) first
        try {
            ProcessBuilder pb = new ProcessBuilder(dockerPath, "compose", "version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            if (process.waitFor() == 0) {
                this.composeCommand = new String[] { dockerPath, "compose" };
                return;
            }
        } catch (Exception e) {
            // Fall through to try standalone docker-compose
        }
        
        // Try standalone "docker-compose"
        for (String path : DOCKER_COMPOSE_PATHS) {
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
        return "docker";
    }
    
    @Override
    protected String getExecutablePath() {
        return dockerPath;
    }
    
    @Override
    protected String[] getComposeCommand() {
        return composeCommand;
    }
    
    @Override
    public boolean isAvailable() {
        return dockerPath != null && composeCommand != null && super.isAvailable();
    }
    
    @Override
    public int getPriority() {
        return 100; // Docker is the default, highest priority
    }
}
