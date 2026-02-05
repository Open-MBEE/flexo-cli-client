package org.openmbee.flexo.cli.plugin;

import org.openmbee.flexo.cli.FlexoCLI;
import org.openmbee.flexo.cli.client.AuthenticationHandler;
import org.openmbee.flexo.cli.client.FlexoMmsClient;
import org.openmbee.flexo.cli.config.FlexoConfig;

/**
 * Context object providing plugins with access to core CLI infrastructure.
 *
 * This allows plugins to:
 * - Read configuration (MMS URL, authentication settings, defaults)
 * - Create authenticated HTTP clients
 * - Access parent command options (--org, --repo, --verbose, etc.)
 * - Use consistent console output formatting
 */
public class PluginContext {
    private final FlexoConfig config;
    private final FlexoCLI parentCommand;

    public PluginContext(FlexoConfig config, FlexoCLI parentCommand) {
        this.config = config;
        this.parentCommand = parentCommand;
    }

    /**
     * Get the configuration object
     *
     * @return FlexoConfig with access to all settings
     */
    public FlexoConfig getConfig() {
        return config;
    }

    /**
     * Create an authenticated FlexoMmsClient for making API calls
     *
     * The client will use the configured authentication method (local mode or SSH keys)
     *
     * @return FlexoMmsClient ready for use
     */
    public FlexoMmsClient createClient() {
        AuthenticationHandler authHandler = new AuthenticationHandler(
                config.isAuthEnabled(),
                config.getSshKeyPath(),
                config.isLocalMode(),
                config.getLocalUser(),
                config.getLocalJwtSecret()
        );
        return new FlexoMmsClient(config.getMmsUrl(), authHandler);
    }

    /**
     * Get the organization ID from command line or config
     *
     * @return organization ID or null if not set
     */
    public String getOrgId() {
        String orgId = parentCommand.getOrgId();
        return orgId != null ? orgId : config.getDefaultOrg();
    }

    /**
     * Get the repository ID from command line or config
     *
     * @return repository ID or null if not set
     */
    public String getRepoId() {
        String repoId = parentCommand.getRepoId();
        return repoId != null ? repoId : config.getDefaultRepo();
    }

    /**
     * Check if verbose output is enabled
     *
     * @return true if --verbose flag was set
     */
    public boolean isVerbose() {
        return parentCommand.isVerbose();
    }

    /**
     * Check if colored output is disabled
     *
     * @return true if --no-color flag was set
     */
    public boolean isNoColor() {
        return parentCommand.isNoColor();
    }

    /**
     * Get the MMS service URL
     *
     * @return configured MMS URL
     */
    public String getMmsUrl() {
        return config.getMmsUrl();
    }
}
