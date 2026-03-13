package org.openmbee.flexo.cli.container;

/**
 * Exception thrown when container runtime operations fail
 */
public class ContainerException extends Exception {
    
    private final int exitCode;
    
    public ContainerException(String message) {
        super(message);
        this.exitCode = 1;
    }
    
    public ContainerException(String message, Throwable cause) {
        super(message, cause);
        this.exitCode = 1;
    }
    
    public ContainerException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }
    
    public ContainerException(String message, Throwable cause, int exitCode) {
        super(message, cause);
        this.exitCode = exitCode;
    }
    
    public int getExitCode() {
        return exitCode;
    }
}
