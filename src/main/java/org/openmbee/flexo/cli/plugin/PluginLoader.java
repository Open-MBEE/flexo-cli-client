package org.openmbee.flexo.cli.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Discovers and loads Flexo CLI plugins from the plugin directory.
 *
 * Plugins are JAR files located in: ~/.flexo/plugins/
 *
 * Each plugin JAR must contain:
 * META-INF/services/org.openmbee.flexo.cli.plugin.FlexoPlugin
 *
 * This file lists the fully-qualified class names of FlexoPlugin implementations.
 */
public class PluginLoader {
    private static final Logger logger = LoggerFactory.getLogger(PluginLoader.class);
    private static final String PLUGIN_DIR = System.getProperty("user.home") + "/.flexo/plugins";

    /**
     * Load all plugins from the plugin directory
     *
     * @param context the plugin context to pass to each plugin
     * @return list of loaded plugins
     */
    public static List<FlexoPlugin> loadPlugins(PluginContext context) {
        List<FlexoPlugin> plugins = new ArrayList<>();

        File pluginDir = new File(PLUGIN_DIR);
        if (!pluginDir.exists() || !pluginDir.isDirectory()) {
            logger.debug("Plugin directory does not exist: {}", PLUGIN_DIR);
            return plugins;
        }

        File[] jarFiles = pluginDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            logger.debug("No plugin JARs found in: {}", PLUGIN_DIR);
            return plugins;
        }

        logger.debug("Found {} plugin JAR(s) in: {}", jarFiles.length, PLUGIN_DIR);

        // Create URLs for all JARs
        List<URL> jarUrls = new ArrayList<>();
        for (File jarFile : jarFiles) {
            try {
                jarUrls.add(jarFile.toURI().toURL());
                logger.debug("Added plugin JAR: {}", jarFile.getName());
            } catch (Exception e) {
                logger.warn("Failed to load plugin JAR {}: {}", jarFile.getName(), e.getMessage());
            }
        }

        if (jarUrls.isEmpty()) {
            return plugins;
        }

        try {
            // Create a class loader for all plugin JARs
            URLClassLoader pluginClassLoader = new URLClassLoader(
                    jarUrls.toArray(new URL[0]),
                    PluginLoader.class.getClassLoader()
            );

            // Use ServiceLoader to discover plugins
            ServiceLoader<FlexoPlugin> serviceLoader = ServiceLoader.load(FlexoPlugin.class, pluginClassLoader);

            for (FlexoPlugin plugin : serviceLoader) {
                try {
                    logger.info("Loading plugin: {} v{} - {}",
                            plugin.getName(),
                            plugin.getVersion(),
                            plugin.getDescription());

                    // Initialize the plugin
                    plugin.initialize(context);
                    plugins.add(plugin);

                    logger.debug("Successfully loaded plugin: {}", plugin.getName());
                } catch (Exception e) {
                    logger.error("Failed to initialize plugin {}: {}",
                            plugin.getClass().getName(),
                            e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load plugins: {}", e.getMessage(), e);
        }

        logger.info("Loaded {} plugin(s)", plugins.size());
        return plugins;
    }

    /**
     * Get the plugin directory path
     *
     * @return plugin directory path
     */
    public static String getPluginDirectory() {
        return PLUGIN_DIR;
    }
}
