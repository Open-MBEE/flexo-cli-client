package org.openmbee.flexo.cli.commands;

import org.openmbee.flexo.cli.FlexoCLI;
import org.openmbee.flexo.cli.client.AuthenticationHandler;
import org.openmbee.flexo.cli.client.FlexoMmsClient;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.openmbee.flexo.cli.util.ConsoleUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Merge command - Merge changes between branches
 */
@Command(
        name = "merge",
        description = "Merge changes between branches",
        mixinStandardHelpOptions = true
)
public class MergeCommand implements Runnable {

    @ParentCommand
    private FlexoCLI parent;

    @Option(names = {"-s", "--source"}, description = "Source branch", required = true)
    private String sourceBranch;

    @Option(names = {"-t", "--target"}, description = "Target branch")
    private String targetBranch;

    @Option(names = {"--no-commit"}, description = "Create diff without committing")
    private boolean noCommit = false;

    @Parameters(index = "0", arity = "0..1", description = "Source branch (alternative to --source)")
    private String sourceBranchParam;

    @Override
    public void run() {
        FlexoConfig config = FlexoCLI.getConfig();

        // Determine source and target branches
        String source = sourceBranch != null ? sourceBranch : sourceBranchParam;
        if (source == null || source.isEmpty()) {
            ConsoleUtil.error("Source branch is required. Use -s/--source");
            System.exit(1);
        }

        String target = targetBranch != null ? targetBranch : config.getDefaultBranch();
        if (target == null || target.isEmpty()) {
            ConsoleUtil.error("Target branch is required. Use -t/--target or set default.branch in config");
            System.exit(1);
        }

        // Get org and repo
        String orgId = parent.getOrgId() != null ? parent.getOrgId() : config.getDefaultOrg();
        String repoId = parent.getRepoId() != null ? parent.getRepoId() : config.getDefaultRepo();

        if (orgId == null || orgId.isEmpty()) {
            ConsoleUtil.error("Organization ID is required. Use --org or set default.org in config");
            System.exit(1);
        }

        if (repoId == null || repoId.isEmpty()) {
            ConsoleUtil.error("Repository ID is required. Use --repo or set default.repo in config");
            System.exit(1);
        }

        // Create authentication handler
        AuthenticationHandler authHandler = new AuthenticationHandler(
                config.isAuthEnabled(),
                config.getSshKeyPath(),
                config.isLocalMode(),
                config.getLocalUser(),
                config.getLocalJwtSecret()
        );

        try (FlexoMmsClient client = new FlexoMmsClient(config.getMmsUrl(), authHandler)) {
            ConsoleUtil.info("Merging " + source + " into " + target + "...");

            // Create diff between branches
            String diffResult = client.createDiff(orgId, repoId, source, target);

            if (diffResult != null && !diffResult.isEmpty()) {
                ConsoleUtil.success("Diff created successfully");
                if (parent.isVerbose()) {
                    ConsoleUtil.debug("Diff result: " + diffResult);
                }

                if (!noCommit) {
                    ConsoleUtil.info("Applying merge (not yet implemented)...");
                    ConsoleUtil.warn("Automatic merge commit is not yet implemented");
                    ConsoleUtil.info("You can review the diff and manually apply changes");
                }
            } else {
                ConsoleUtil.warn("No differences found between branches");
            }

        } catch (Exception e) {
            ConsoleUtil.error("Merge failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
}
