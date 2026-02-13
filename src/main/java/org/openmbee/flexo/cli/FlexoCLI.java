package org.openmbee.flexo.cli;

import org.openmbee.flexo.cli.commands.BranchCommand;
import org.openmbee.flexo.cli.commands.InitCommand;
import org.openmbee.flexo.cli.commands.MergeCommand;
import org.openmbee.flexo.cli.commands.PullCommand;
import org.openmbee.flexo.cli.commands.PushCommand;
import org.openmbee.flexo.cli.commands.RemoteCommand;
import org.openmbee.flexo.cli.commands.RmCommand;
import org.openmbee.flexo.cli.commands.BaseCommand;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.openmbee.flexo.cli.plugin.FlexoPlugin;
import org.openmbee.flexo.cli.plugin.PluginContext;
import org.openmbee.flexo.cli.plugin.PluginLoader;
import org.openmbee.flexo.cli.util.ConsoleUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

/**
 * Main entry point for Flexo CLI
 * Provides git-style commands for interacting with Flexo MMS
 */
@Command(
        name = "flexo",
        description = "Flexo MMS command-line interface",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        subcommands = {
                InitCommand.class,
                BranchCommand.class,
                PullCommand.class,
                PushCommand.class,
                RmCommand.class,
                MergeCommand.class,
                RemoteCommand.class,
                CommandLine.HelpCommand.class
        }
)
public class FlexoCLI implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(FlexoCLI.class);

    @Option(names = {"-c", "--config"}, description = "Configuration file path", scope = CommandLine.ScopeType.INHERIT)
    private String configFile;

    @Option(names = {"-v", "--verbose"}, description = "Verbose output", scope = CommandLine.ScopeType.INHERIT)
    private boolean verbose;

    @Option(names = {"--org"}, description = "Organization ID", scope = CommandLine.ScopeType.INHERIT)
    private String orgId;

    @Option(names = {"--repo"}, description = "Repository ID", scope = CommandLine.ScopeType.INHERIT)
    private String repoId;

    @Option(names = {"--remote"}, description = "Remote name (default: origin)", scope = CommandLine.ScopeType.INHERIT)
    private String remoteName;

    @Option(names = {"--no-color"}, description = "Disable colored output", scope = CommandLine.ScopeType.INHERIT)
    private boolean noColor;

    private static FlexoConfig config;

    public static void main(String[] args) {
        // Initialize configuration
        config = new FlexoConfig();

        // Create CLI instance
        FlexoCLI cliInstance = new FlexoCLI();

        // Create command line
        CommandLine commandLine = new CommandLine(cliInstance)
                .setCaseInsensitiveEnumValuesAllowed(true);

        // Load plugins
        PluginContext pluginContext = new PluginContext(config, cliInstance);
        List<FlexoPlugin> plugins = PluginLoader.loadPlugins(pluginContext);

        // Register plugin commands
        for (FlexoPlugin plugin : plugins) {
            try {
                commandLine.addSubcommand(plugin.getCommand());
                logger.debug("Registered plugin command: {}", plugin.getName());
            } catch (Exception e) {
                logger.error("Failed to register plugin {}: {}", plugin.getName(), e.getMessage());
            }
        }

        // Execute command and handle CommandException
        int exitCode;
        try {
            exitCode = commandLine.execute(args);
        } catch (BaseCommand.CommandException e) {
            // CommandException already logged error message
            exitCode = e.getExitCode();
        } catch (Exception e) {
            ConsoleUtil.error("Unexpected error: " + e.getMessage());
            logger.error("Unexpected error", e);
            exitCode = 1;
        }

        System.exit(exitCode);
    }

    @Override
    public void run() {
        // When no subcommand is specified, print help
        ConsoleUtil.info("Flexo MMS CLI - Use --help for available commands");
        ConsoleUtil.info("");
        ConsoleUtil.info("Available commands:");
        ConsoleUtil.info("  init    - Initialize local MMS with default org and repo");
        ConsoleUtil.info("  remote  - Manage remote MMS instances");
        ConsoleUtil.info("  branch  - List, create, or manage branches");
        ConsoleUtil.info("  pull    - Fetch model from a branch");
        ConsoleUtil.info("  push    - Commit model changes to a branch");
        ConsoleUtil.info("  rm      - Remove elements from the model");
        ConsoleUtil.info("  merge   - Merge changes between branches");
        ConsoleUtil.info("");
        ConsoleUtil.info("Use 'flexo <command> --help' for more information about a command");
    }

    public static FlexoConfig getConfig() {
        return config;
    }

    public static void setConfig(FlexoConfig testConfig) {
        config = testConfig;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getRepoId() {
        return repoId;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isNoColor() {
        return noColor;
    }

    public String getRemoteName() {
        return remoteName;
    }
}
