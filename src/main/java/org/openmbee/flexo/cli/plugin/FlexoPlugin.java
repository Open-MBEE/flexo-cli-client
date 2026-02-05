package org.openmbee.flexo.cli.plugin;

import picocli.CommandLine.Model.CommandSpec;

/**
 * Interface for Flexo CLI plugins.
 *
 * Plugins extend the CLI with additional commands while sharing core infrastructure
 * (configuration, authentication, HTTP client, console utilities).
 *
 * Plugins are discovered via Java ServiceLoader mechanism. Each plugin JAR must include:
 * META-INF/services/org.openmbee.flexo.cli.plugin.FlexoPlugin
 *
 * Plugin JARs should be placed in: ~/.flexo/plugins/
 */
public interface FlexoPlugin {
    /**
     * Get the plugin name (used as the command name in CLI)
     *
     * @return plugin name (e.g., "sysml" for command "flexo sysml")
     */
    String getName();

    /**
     * Get a brief description of the plugin
     *
     * @return plugin description shown in help text
     */
    String getDescription();

    /**
     * Get the plugin version
     *
     * @return version string (e.g., "1.0.0")
     */
    String getVersion();

    /**
     * Initialize the plugin with access to core CLI infrastructure
     *
     * Called once during plugin loading before getCommand() is called.
     *
     * @param context provides access to config, authentication, HTTP client, etc.
     */
    void initialize(PluginContext context);

    /**
     * Get the picocli CommandSpec for this plugin's commands
     *
     * The returned CommandSpec will be registered as a subcommand of the main CLI.
     *
     * @return CommandSpec defining the plugin's command structure
     */
    CommandSpec getCommand();
}
