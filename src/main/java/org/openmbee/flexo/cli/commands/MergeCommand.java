package org.openmbee.flexo.cli.commands;

import org.openmbee.flexo.cli.FlexoCLI;
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
public class MergeCommand extends BaseCommand {

    @ParentCommand
    protected FlexoCLI parent;

    @Option(names = {"-s", "--source"}, description = "Source branch", required = true)
    private String sourceBranch;

    @Option(names = {"-t", "--target"}, description = "Target branch")
    private String targetBranch;

    @Option(names = {"--no-commit"}, description = "Create diff without committing")
    private boolean noCommit = false;

    @Parameters(index = "0", arity = "0..1", description = "Source branch (alternative to --source)")
    private String sourceBranchParam;

    @Override
    protected void executeCommand() throws Exception {
        FlexoConfig config = getConfig();

        // Determine source and target branches
        String source = sourceBranch != null ? sourceBranch : sourceBranchParam;
        if (source == null || source.isEmpty()) {
            throw new CommandExecutionException("Source branch is required. Use -s/--source", 1);
        }

        String target = targetBranch != null ? targetBranch : config.getDefaultBranch();
        if (target == null || target.isEmpty()) {
            throw new CommandExecutionException(
                "Target branch is required. Use -t/--target or set default.branch in config", 1);
        }

        // Get org and repo
        String orgId = getOrgId(config);
        String repoId = getRepoId(config);
        
        // Validate org and repo
        validateOrgAndRepo(orgId, repoId);

        try (FlexoMmsClient client = createClient(config)) {
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
        }
    }
}
