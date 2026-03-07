package org.openmbee.flexo.cli.commands;

import org.openmbee.flexo.cli.FlexoCLI;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.openmbee.flexo.cli.model.Remote;
import org.openmbee.flexo.cli.util.ConsoleUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.util.Map;

/**
 * Remote command - Manage remote MMS instances
 * Similar to git remote command
 */
@Command(
        name = "remote",
        description = "Manage remote MMS instances",
        mixinStandardHelpOptions = true,
        subcommands = {
                RemoteCommand.AddCommand.class,
                RemoteCommand.RemoveCommand.class,
                RemoteCommand.ListCommand.class,
                RemoteCommand.SetUrlCommand.class,
                RemoteCommand.ShowCommand.class,
                RemoteCommand.RenameCommand.class
        }
)
public class RemoteCommand implements Runnable {

    @ParentCommand
    private FlexoCLI parent;

    @Override
    public void run() {
        // Default behavior: list remotes
        new ListCommand().run();
    }

    /**
     * List all remotes
     */
    @Command(
            name = "list",
            aliases = {"ls", "-v"},
            description = "List all configured remotes",
            mixinStandardHelpOptions = true
    )
    static class ListCommand implements Runnable {
        @Override
        public void run() {
            FlexoConfig config = FlexoCLI.getConfig();
            Map<String, Remote> remotes = config.getRemotes();

            if (remotes.isEmpty()) {
                ConsoleUtil.info("No remotes configured");
                ConsoleUtil.info("");
                ConsoleUtil.info("Add a remote with: flexo remote add <name> <url>");
                return;
            }

            String defaultRemote = config.getDefaultRemote();
            ConsoleUtil.info("Configured remotes:");
            for (Remote remote : remotes.values()) {
                String marker = remote.getName().equals(defaultRemote) ? " *" : "";
                ConsoleUtil.info("  " + remote.getName() + marker + "\t" + remote.getUrl());
            }
        }
    }

    /**
     * Add a new remote
     */
    @Command(
            name = "add",
            description = "Add a new remote",
            mixinStandardHelpOptions = true
    )
    static class AddCommand implements Runnable {
        @Parameters(index = "0", description = "Remote name")
        private String name;

        @Parameters(index = "1", description = "Remote URL")
        private String url;

        @picocli.CommandLine.Option(names = {"--local-mode"}, description = "Enable local mode for this remote")
        private Boolean localMode;

        @picocli.CommandLine.Option(names = {"--local-user"}, description = "Local user for authentication")
        private String localUser;

        @picocli.CommandLine.Option(names = {"--local-jwt-secret"}, description = "JWT secret for local mode")
        private String localJwtSecret;

        @picocli.CommandLine.Option(names = {"--ssh-key"}, description = "SSH key path for authentication")
        private String sshKeyPath;

        @picocli.CommandLine.Option(names = {"--set-default"}, description = "Set as default remote")
        private boolean setDefault;

        @Override
        public void run() {
            FlexoConfig config = FlexoCLI.getConfig();

            // Check if remote already exists
            if (config.hasRemote(name)) {
                ConsoleUtil.error("Remote '" + name + "' already exists");
                ConsoleUtil.info("Use 'flexo remote set-url " + name + " <url>' to update URL");
                throw new CommandExecutionException("Remote '" + name + "' already exists", 1);
            }

            // Create and configure remote
            Remote remote = new Remote(name, url);
            
            if (localMode != null) {
                remote.setLocalMode(String.valueOf(localMode));
            }
            if (localUser != null) {
                remote.setLocalUser(localUser);
            }
            if (localJwtSecret != null) {
                remote.setLocalJwtSecret(localJwtSecret);
            }
            if (sshKeyPath != null) {
                remote.setSshKeyPath(sshKeyPath);
                remote.setAuthEnabled("true");
            }

            config.setRemote(remote);

            // Set as default if requested
            if (setDefault) {
                config.setDefaultRemote(name);
            }

            try {
                config.save();
                ConsoleUtil.success("Remote '" + name + "' added: " + url);
                if (setDefault) {
                    ConsoleUtil.info("Set as default remote");
                }
            } catch (IOException e) {
                ConsoleUtil.error("Failed to save configuration: " + e.getMessage());
                throw new CommandExecutionException("Failed to save configuration: " + e.getMessage(), e, 1);
            }
        }
    }

    /**
     * Remove a remote
     */
    @Command(
            name = "remove",
            aliases = {"rm"},
            description = "Remove a remote",
            mixinStandardHelpOptions = true
    )
    static class RemoveCommand implements Runnable {
        @Parameters(index = "0", description = "Remote name")
        private String name;

        @Override
        public void run() {
            FlexoConfig config = FlexoCLI.getConfig();

            if (!config.hasRemote(name)) {
                ConsoleUtil.error("Remote '" + name + "' does not exist");
                throw new CommandExecutionException("Remote '" + name + "' does not exist", 1);
            }

            config.removeRemote(name);

            try {
                config.save();
                ConsoleUtil.success("Remote '" + name + "' removed");
            } catch (IOException e) {
                ConsoleUtil.error("Failed to save configuration: " + e.getMessage());
                throw new CommandExecutionException("Failed to save configuration: " + e.getMessage(), e, 1);
            }
        }
    }

    /**
     * Set or update remote URL
     */
    @Command(
            name = "set-url",
            description = "Change the URL for a remote",
            mixinStandardHelpOptions = true
    )
    static class SetUrlCommand implements Runnable {
        @Parameters(index = "0", description = "Remote name")
        private String name;

        @Parameters(index = "1", description = "New URL")
        private String url;

        @Override
        public void run() {
            FlexoConfig config = FlexoCLI.getConfig();

            Remote remote = config.getRemote(name);
            if (remote == null) {
                ConsoleUtil.error("Remote '" + name + "' does not exist");
                ConsoleUtil.info("Use 'flexo remote add " + name + " <url>' to create it");
                throw new CommandExecutionException("Remote '" + name + "' does not exist", 1);
            }

            remote.setUrl(url);
            config.setRemote(remote);

            try {
                config.save();
                ConsoleUtil.success("Remote '" + name + "' URL updated: " + url);
            } catch (IOException e) {
                ConsoleUtil.error("Failed to save configuration: " + e.getMessage());
                throw new CommandExecutionException("Failed to save configuration: " + e.getMessage(), e, 1);
            }
        }
    }

    /**
     * Show details of a remote
     */
    @Command(
            name = "show",
            description = "Show details of a remote",
            mixinStandardHelpOptions = true
    )
    static class ShowCommand implements Runnable {
        @Parameters(index = "0", description = "Remote name")
        private String name;

        @Override
        public void run() {
            FlexoConfig config = FlexoCLI.getConfig();

            Remote remote = config.getRemote(name);
            if (remote == null) {
                ConsoleUtil.error("Remote '" + name + "' does not exist");
                throw new CommandExecutionException("Remote '" + name + "' does not exist", 1);
            }

            String defaultRemote = config.getDefaultRemote();
            boolean isDefault = name.equals(defaultRemote);

            ConsoleUtil.info("Remote: " + name + (isDefault ? " (default)" : ""));
            ConsoleUtil.info("  URL: " + remote.getUrl());
            ConsoleUtil.info("  Local Mode: " + (remote.isLocalModeBoolean() ? "enabled" : "disabled"));
            if (remote.getLocalUser() != null) {
                ConsoleUtil.info("  Local User: " + remote.getLocalUser());
            }
            if (remote.isAuthEnabledBoolean()) {
                ConsoleUtil.info("  Auth: enabled");
                if (remote.getSshKeyPath() != null) {
                    ConsoleUtil.info("  SSH Key: " + remote.getSshKeyPath());
                }
            }
        }
    }

    /**
     * Rename a remote
     */
    @Command(
            name = "rename",
            description = "Rename a remote",
            mixinStandardHelpOptions = true
    )
    static class RenameCommand implements Runnable {
        @Parameters(index = "0", description = "Old name")
        private String oldName;

        @Parameters(index = "1", description = "New name")
        private String newName;

        @Override
        public void run() {
            FlexoConfig config = FlexoCLI.getConfig();

            Remote remote = config.getRemote(oldName);
            if (remote == null) {
                ConsoleUtil.error("Remote '" + oldName + "' does not exist");
                throw new CommandExecutionException("Remote '" + oldName + "' does not exist", 1);
            }

            if (config.hasRemote(newName)) {
                ConsoleUtil.error("Remote '" + newName + "' already exists");
                throw new CommandExecutionException("Remote '" + newName + "' already exists", 1);
            }

            // Create new remote with new name
            remote.setName(newName);
            config.setRemote(remote);
            config.removeRemote(oldName);

            // Update default remote if needed
            if (oldName.equals(config.getDefaultRemote())) {
                config.setDefaultRemote(newName);
            }

            try {
                config.save();
                ConsoleUtil.success("Remote renamed: " + oldName + " -> " + newName);
            } catch (IOException e) {
                ConsoleUtil.error("Failed to save configuration: " + e.getMessage());
                throw new CommandExecutionException("Failed to save configuration: " + e.getMessage(), e, 1);
            }
        }
    }
}
