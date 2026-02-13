package org.openmbee.flexo.cli.commands;

/**
 * Exception thrown when configuration is missing or invalid.
 */
public class ConfigurationException extends CommandExecutionException {

    public ConfigurationException(String message) {
        super(message, 1);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause, 1);
    }
}
