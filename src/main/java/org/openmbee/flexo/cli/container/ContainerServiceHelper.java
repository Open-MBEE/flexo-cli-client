package org.openmbee.flexo.cli.container;

import org.openmbee.flexo.cli.config.FlexoConfig;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

/**
 * Helper class for container-based service initialization.
 * Provides common functionality used by both flexo-cli-client and plugin InitCommands.
 */
public class ContainerServiceHelper {
    
    private final ContainerRuntime runtime;
    private final boolean verbose;
    
    public ContainerServiceHelper(FlexoConfig config, boolean verbose) throws ContainerException {
        this.runtime = ContainerRuntimeFactory.getRuntime(config, verbose);
        this.verbose = verbose;
    }
    
    public ContainerServiceHelper(ContainerRuntime runtime, boolean verbose) {
        this.runtime = runtime;
        this.verbose = verbose;
    }
    
    /**
     * Get the container runtime being used
     */
    public ContainerRuntime getRuntime() {
        return runtime;
    }
    
    /**
     * Extract a docker-compose file from classpath to a temporary location
     */
    public File extractComposeFile(String resourceName, String prefix) throws IOException {
        InputStream resourceStream = getClass().getClassLoader()
                .getResourceAsStream(resourceName);
        
        if (resourceStream == null) {
            throw new FileNotFoundException("Resource not found: " + resourceName);
        }
        
        // Create temporary file with secure permissions
        File tempFile;
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("unix") || os.contains("linux") || os.contains("mac")) {
            // On Unix-like systems, create file with restrictive permissions
            tempFile = Files.createTempFile(
                prefix,
                ".yml",
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")
                )
            ).toFile();
        } else {
            // On Windows, create file then set restrictive permissions
            tempFile = Files.createTempFile(prefix, ".yml").toFile();
            tempFile.setReadable(false, false);
            tempFile.setReadable(true, true);
            tempFile.setWritable(false, false);
            tempFile.setWritable(true, true);
            tempFile.setExecutable(false, false);
        }
        tempFile.deleteOnExit();
        
        // Copy resource to temporary file
        try (FileOutputStream fos = new FileOutputStream(tempFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = resourceStream.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            bos.flush();
        } finally {
            resourceStream.close();
        }
        
        if (verbose) {
            System.err.println("  Extracted compose file to: " + tempFile.getAbsolutePath());
        }
        
        return tempFile;
    }
    
    /**
     * Start a service using the compose file
     */
    public boolean startService(File composeFile, String serviceName) throws ContainerException {
        if (verbose) {
            System.err.println("  Starting service '" + serviceName + "' using " + runtime.getName() + "...");
        }
        
        return runtime.composeUp(composeFile, Collections.singletonList(serviceName));
    }
    
    /**
     * Stop a service using the compose file
     */
    public boolean stopService(File composeFile, String serviceName) throws ContainerException {
        if (verbose) {
            System.err.println("  Stopping service '" + serviceName + "' using " + runtime.getName() + "...");
        }
        
        return runtime.composeDown(composeFile, Collections.singletonList(serviceName));
    }
    
    /**
     * Wait for a service to be ready on a specific port
     */
    public void waitForServicePort(String serviceName, int port, int maxAttempts, int sleepMillis) 
            throws ContainerException, InterruptedException {
        
        if (verbose) {
            System.err.println("  Waiting for " + serviceName + " on port " + port + "...");
        }
        
        int attempt = 0;
        while (attempt < maxAttempts) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("localhost", port), 1000);
                if (verbose) {
                    System.err.println("  " + serviceName + " is ready");
                }
                return;
            } catch (IOException e) {
                attempt++;
                if (attempt >= maxAttempts) {
                    throw new ContainerException(
                        serviceName + " did not become ready within timeout. " +
                        "Please check logs: " + runtime.getName() + " logs " + serviceName
                    );
                }
                Thread.sleep(sleepMillis);
                if (verbose) {
                    System.err.println("  Waiting for " + serviceName + "... (attempt " + 
                        attempt + "/" + maxAttempts + ")");
                }
            }
        }
    }
    
    /**
     * Check if a container is running
     */
    public boolean isServiceRunning(String containerName) throws ContainerException {
        return runtime.isContainerRunning(containerName);
    }
    
    /**
     * Get logs from a container
     */
    public String getServiceLogs(String containerName, int tailLines) throws ContainerException {
        return runtime.getContainerLogs(containerName, tailLines);
    }
    
    /**
     * Get information about the container runtime being used
     */
    public String getRuntimeInfo() {
        try {
            return runtime.getName() + " " + runtime.getVersion();
        } catch (ContainerException e) {
            return runtime.getName() + " (version unknown)";
        }
    }
}
