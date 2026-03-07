package org.openmbee.flexo.cli.commands;

/**
 * Exception thrown when Docker operations fail.
 */
public class DockerException extends CommandExecutionException {

    public DockerException(String message) {
        super(message, 1);
    }

    public DockerException(String message, Throwable cause) {
        super(message, cause, 1);
    }

    public DockerException(String message, String dockerCommand) {
        super(message + "\n  Command: " + dockerCommand, 1);
    }
}
