package org.openmbee.flexo.cli.commands;

/**
 * Exception thrown when a service operation fails.
 */
public class ServiceException extends CommandExecutionException {

    public ServiceException(String message) {
        super(message, 1);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause, 1);
    }

    public ServiceException(String message, int httpStatusCode) {
        super("HTTP " + httpStatusCode + ": " + message, 1);
    }

    public ServiceException(String message, int httpStatusCode, Throwable cause) {
        super("HTTP " + httpStatusCode + ": " + message, cause, 1);
    }
}
