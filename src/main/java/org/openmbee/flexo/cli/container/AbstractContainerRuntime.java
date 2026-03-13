package org.openmbee.flexo.cli.container;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for container runtime implementations.
 * Provides common functionality for executing container commands.
 */
public abstract class AbstractContainerRuntime implements ContainerRuntime {
    
    protected final boolean verbose;
    
    protected AbstractContainerRuntime(boolean verbose) {
        this.verbose = verbose;
    }
    
    /**
     * Get the path to the container runtime executable
     */
    protected abstract String getExecutablePath();
    
    /**
     * Get the command to run compose operations (e.g., ["docker", "compose"] or ["podman-compose"])
     */
    protected abstract String[] getComposeCommand();
    
    /**
     * Execute a command and return the exit code
     */
    protected int executeCommand(List<String> command, boolean captureOutput) throws ContainerException {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            if (verbose) {
                System.err.println("  [Container] Executing: " + String.join(" ", command));
            }
            
            Process process = pb.start();
            
            if (captureOutput || verbose) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (verbose) {
                            System.err.println("  [Container] " + line);
                        }
                    }
                }
            }
            
            if (!process.waitFor(5, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new ContainerException("Command timed out after 5 minutes");
            }
            
            return process.exitValue();
        } catch (IOException e) {
            throw new ContainerException("Failed to execute command: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ContainerException("Command interrupted", e);
        }
    }
    
    /**
     * Execute a command and capture output
     */
    protected String executeCommandWithOutput(List<String> command) throws ContainerException {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            if (verbose) {
                System.err.println("  [Container] Executing: " + String.join(" ", command));
            }
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (verbose) {
                        System.err.println("  [Container] " + line);
                    }
                }
            }
            
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ContainerException("Command timed out");
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new ContainerException("Command failed with exit code " + exitCode, exitCode);
            }
            
            return output.toString().trim();
        } catch (IOException e) {
            throw new ContainerException("Failed to execute command: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ContainerException("Command interrupted", e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        String execPath = getExecutablePath();
        if (execPath == null) {
            return false;
        }
        
        try {
            List<String> command = new ArrayList<>();
            command.add(execPath);
            command.add("--version");
            int exitCode = executeCommand(command, false);
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public String getVersion() throws ContainerException {
        String execPath = getExecutablePath();
        if (execPath == null) {
            throw new ContainerException(getName() + " executable not found");
        }
        
        List<String> command = new ArrayList<>();
        command.add(execPath);
        command.add("--version");
        
        return executeCommandWithOutput(command);
    }
    
    @Override
    public boolean composeUp(File composeFile, List<String> serviceNames) throws ContainerException {
        if (!composeFile.exists()) {
            throw new ContainerException("Compose file not found: " + composeFile.getAbsolutePath());
        }
        
        List<String> command = new ArrayList<>(Arrays.asList(getComposeCommand()));
        command.add("-f");
        command.add(composeFile.getAbsolutePath());
        command.add("up");
        command.add("-d");
        command.addAll(serviceNames);
        
        int exitCode = executeCommand(command, true);
        return exitCode == 0;
    }
    
    @Override
    public boolean composeDown(File composeFile, List<String> serviceNames) throws ContainerException {
        if (!composeFile.exists()) {
            throw new ContainerException("Compose file not found: " + composeFile.getAbsolutePath());
        }
        
        List<String> command = new ArrayList<>(Arrays.asList(getComposeCommand()));
        command.add("-f");
        command.add(composeFile.getAbsolutePath());
        command.add("down");
        command.addAll(serviceNames);
        
        int exitCode = executeCommand(command, true);
        return exitCode == 0;
    }
    
    @Override
    public boolean isContainerRunning(String containerName) throws ContainerException {
        String execPath = getExecutablePath();
        if (execPath == null) {
            throw new ContainerException(getName() + " executable not found");
        }
        
        try {
            List<String> command = new ArrayList<>();
            command.add(execPath);
            command.add("ps");
            command.add("--filter");
            command.add("name=" + containerName);
            command.add("--format");
            command.add("{{.Names}}");
            
            String output = executeCommandWithOutput(command);
            return output.contains(containerName);
        } catch (ContainerException e) {
            // If command fails, assume container is not running
            return false;
        }
    }
    
    @Override
    public String getContainerLogs(String containerName, int tail) throws ContainerException {
        String execPath = getExecutablePath();
        if (execPath == null) {
            throw new ContainerException(getName() + " executable not found");
        }
        
        List<String> command = new ArrayList<>();
        command.add(execPath);
        command.add("logs");
        if (tail > 0) {
            command.add("--tail");
            command.add(String.valueOf(tail));
        }
        command.add(containerName);
        
        return executeCommandWithOutput(command);
    }
}
