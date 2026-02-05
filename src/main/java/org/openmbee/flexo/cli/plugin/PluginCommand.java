package org.openmbee.flexo.cli.plugin;

import org.openmbee.flexo.cli.client.FlexoMmsClient;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.openmbee.flexo.cli.util.ConsoleUtil;

/**
 * Base class for plugin commands.
 *
 * Provides convenient access to plugin context resources:
 * - Configuration
 * - HTTP client
 * - Console utilities
 * - Parent command options
 *
 * Plugin commands should extend this class and implement the run() method.
 */
public abstract class PluginCommand implements Runnable {
    protected PluginContext context;

    /**
     * Set the plugin context.
     *
     * Called by the plugin system before command execution.
     *
     * @param context provides access to shared resources
     */
    public void setContext(PluginContext context) {
        this.context = context;
    }

    /**
     * Get an authenticated HTTP client for MMS API calls
     *
     * @return FlexoMmsClient ready for use
     */
    protected FlexoMmsClient getClient() {
        return context.createClient();
    }

    /**
     * Get the configuration
     *
     * @return FlexoConfig
     */
    protected FlexoConfig getConfig() {
        return context.getConfig();
    }

    /**
     * Get the organization ID
     *
     * @return organization ID from command line or config
     */
    protected String getOrgId() {
        return context.getOrgId();
    }

    /**
     * Get the repository ID
     *
     * @return repository ID from command line or config
     */
    protected String getRepoId() {
        return context.getRepoId();
    }

    /**
     * Check if verbose output is enabled
     *
     * @return true if --verbose flag was set
     */
    protected boolean isVerbose() {
        return context.isVerbose();
    }

    /**
     * Print an info message using consistent formatting
     *
     * @param message message to print
     */
    protected void info(String message) {
        ConsoleUtil.info(message);
    }

    /**
     * Print a success message using consistent formatting
     *
     * @param message message to print
     */
    protected void success(String message) {
        ConsoleUtil.success(message);
    }

    /**
     * Print an error message using consistent formatting
     *
     * @param message message to print
     */
    protected void error(String message) {
        ConsoleUtil.error(message);
    }

    /**
     * Print a warning message using consistent formatting
     *
     * @param message message to print
     */
    protected void warn(String message) {
        ConsoleUtil.warn(message);
    }

    /**
     * Print a debug message if verbose is enabled
     *
     * @param message message to print
     */
    protected void debug(String message) {
        if (isVerbose()) {
            ConsoleUtil.debug(message);
        }
    }
}
