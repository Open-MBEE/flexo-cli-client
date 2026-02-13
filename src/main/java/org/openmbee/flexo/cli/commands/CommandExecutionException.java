package org.openmbee.flexo.cli.commands;

/**
 * Base exception for CLI command errors.
 */
public class CommandExecutionException extends RuntimeException {
    private final int exitCode;

    public CommandExecutionException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    public CommandExecutionException(String message, Throwable cause, int exitCode) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    public int getExitCode() {
        return exitCode;
    }
}
