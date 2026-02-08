package org.openmbee.flexo.cli.commands;

import org.openmbee.flexo.cli.FlexoCLI;
import org.openmbee.flexo.cli.client.AuthenticationHandler;
import org.openmbee.flexo.cli.client.FlexoMmsClient;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.openmbee.flexo.cli.model.Remote;
import org.openmbee.flexo.cli.util.ConsoleUtil;

/**
 * Base class for all commands that provides common functionality:
 * - Remote configuration resolution
 * - Authentication handler creation
 * - FlexoMmsClient creation
 * - Org/Repo validation
 * - Exception-based error handling
 * 
 * Subclasses should add @ParentCommand annotation to their parent field.
 */
public abstract class BaseCommand implements Runnable {

    // Subclasses must annotate this field with @ParentCommand
    protected FlexoCLI parent;

    /**
     * Exception thrown when command execution fails.
     * Contains exit code for proper CLI behavior.
     */
    public static class CommandException extends RuntimeException {
        private final int exitCode;

        public CommandException(String message, int exitCode) {
            super(message);
            this.exitCode = exitCode;
        }

        public CommandException(String message, Throwable cause, int exitCode) {
            super(message, cause);
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }
    }

    /**
     * Get the configuration from FlexoCLI.
     */
    protected FlexoConfig getConfig() {
        return FlexoCLI.getConfig();
    }

    /**
     * Resolve org ID from parent options or config.
     * @param config The configuration object
     * @return The resolved org ID
     */
    protected String getOrgId(FlexoConfig config) {
        return parent.getOrgId() != null ? parent.getOrgId() : config.getDefaultOrg();
    }

    /**
     * Resolve repo ID from parent options or config.
     * @param config The configuration object
     * @return The resolved repo ID
     */
    protected String getRepoId(FlexoConfig config) {
        return parent.getRepoId() != null ? parent.getRepoId() : config.getDefaultRepo();
    }

    /**
     * Validate that org ID and repo ID are present.
     * Throws CommandException if either is missing.
     */
    protected void validateOrgAndRepo(String orgId, String repoId) {
        if (orgId == null || orgId.isEmpty()) {
            throw new CommandException(
                "Organization ID is required. Use --org or set default.org in config", 1);
        }
        if (repoId == null || repoId.isEmpty()) {
            throw new CommandException(
                "Repository ID is required. Use --repo or set default.repo in config", 1);
        }
    }

    /**
     * Create a FlexoMmsClient with proper authentication configuration.
     * Uses local MMS instance by default with no authentication.
     * Override shouldUseRemoteUrl to true for pull/push commands that need remotes.
     *
     * @param config The configuration object
     * @return A new FlexoMmsClient instance
     */
    protected FlexoMmsClient createClient(FlexoConfig config) {
        return createClient(config, false);
    }

    /**
     * Create a FlexoMmsClient with optional remote configuration.
     *
     * @param config The configuration object
     * @param useRemote Whether to use remote configuration (for pull/push)
     * @return A new FlexoMmsClient instance
     */
    protected FlexoMmsClient createClient(FlexoConfig config, boolean useRemote) {
        String mmsUrl;
        boolean authEnabled;
        String sshKeyPath;
        boolean localMode;
        String localUser;
        String localJwtSecret;

        if (useRemote) {
            String remoteName = parent.getRemoteName() != null ?
                parent.getRemoteName() : config.getDefaultRemote();
            Remote remote = config.getRemote(remoteName);

            if (remote != null) {
                mmsUrl = remote.getUrl();
                authEnabled = remote.isAuthEnabledBoolean();
                sshKeyPath = remote.getSshKeyPath();
                localMode = remote.isLocalModeBoolean();
                localUser = remote.getLocalUser() != null ?
                    remote.getLocalUser() : config.getLocalUser();
                localJwtSecret = remote.getLocalJwtSecret() != null ?
                    remote.getLocalJwtSecret() : config.getLocalJwtSecret();
            } else {
                mmsUrl = config.getMmsUrl();
                authEnabled = config.isAuthEnabled();
                sshKeyPath = config.getSshKeyPath();
                localMode = config.isLocalMode();
                localUser = config.getLocalUser();
                localJwtSecret = config.getLocalJwtSecret();
            }
        } else {
            mmsUrl = config.getMmsUrl();
            String jwtSecret = config.getLocalJwtSecret();
            boolean hasAuth = jwtSecret != null && !jwtSecret.isEmpty();
            authEnabled = false;
            sshKeyPath = null;
            localMode = hasAuth;
            localUser = hasAuth ? config.getLocalUser() : null;
            localJwtSecret = hasAuth ? jwtSecret : null;
        }

        AuthenticationHandler authHandler = new AuthenticationHandler(
            authEnabled,
            sshKeyPath,
            localMode,
            localUser,
            localJwtSecret
        );

        return new FlexoMmsClient(mmsUrl, authHandler);
    }

    /**
     * Handle exceptions and convert to CommandException if needed.
     * Logs verbose output if enabled.
     */
    protected void handleError(Exception e) {
        String message = e instanceof CommandException ? 
            e.getMessage() : "Operation failed: " + e.getMessage();
        
        ConsoleUtil.error(message);
        
        if (parent.isVerbose()) {
            e.printStackTrace();
        }

        int exitCode = e instanceof CommandException ? 
            ((CommandException) e).getExitCode() : 1;
        
        throw new CommandException(message, e, exitCode);
    }

    /**
     * Template method for command execution.
     * Subclasses should override executeCommand instead of run.
     */
    @Override
    public void run() {
        try {
            executeCommand();
        } catch (CommandException e) {
            // Already handled, just propagate
            throw e;
        } catch (Exception e) {
            handleError(e);
        }
    }

    /**
     * Execute the command logic.
     * Subclasses should implement this method instead of run().
     */
    protected abstract void executeCommand() throws Exception;
}
