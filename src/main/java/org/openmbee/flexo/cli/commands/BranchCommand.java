package org.openmbee.flexo.cli.commands;

import org.openmbee.flexo.cli.FlexoCLI;
import org.openmbee.flexo.cli.client.AuthenticationHandler;
import org.openmbee.flexo.cli.client.FlexoMmsClient;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.openmbee.flexo.cli.model.Branch;
import org.openmbee.flexo.cli.util.ConsoleUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.List;

/**
 * Branch command - List, create, or delete branches
 */
@Command(
        name = "branch",
        description = "List, create, or delete branches",
        mixinStandardHelpOptions = true
)
public class BranchCommand implements Runnable {

    @ParentCommand
    private FlexoCLI parent;

    @Option(names = {"-l", "--list"}, description = "List all branches")
    private boolean list = false;

    @Option(names = {"--create"}, description = "Create a new branch")
    private boolean create = false;

    @Option(names = {"-d", "--delete"}, description = "Delete a branch")
    private boolean delete = false;

    @Option(names = {"-f", "--from"}, description = "Base commit for new branch")
    private String fromCommit;

    @Parameters(index = "0", arity = "0..1", description = "Branch name")
    private String branchName;

    @Override
    public void run() {
        FlexoConfig config = FlexoCLI.getConfig();

        // Get org and repo from options or config
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
            if (create) {
                createBranch(client, orgId, repoId);
            } else if (delete) {
                deleteBranch(client, orgId, repoId);
            } else {
                listBranches(client, orgId, repoId);
            }
        } catch (Exception e) {
            ConsoleUtil.error("Branch operation failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    private void listBranches(FlexoMmsClient client, String orgId, String repoId) throws Exception {
        ConsoleUtil.info("Listing branches in " + orgId + "/" + repoId + "...");

        List<Branch> branches = client.listBranches(orgId, repoId);

        if (branches.isEmpty()) {
            ConsoleUtil.info("No branches found");
            return;
        }

        String[] headers = {"Branch", "Commit", "ETag"};
        String[][] rows = new String[branches.size()][3];

        for (int i = 0; i < branches.size(); i++) {
            Branch branch = branches.get(i);
            rows[i][0] = branch.getName() != null ? branch.getName() : branch.getId();
            rows[i][1] = branch.getCommitId() != null ? branch.getCommitId() : "N/A";
            rows[i][2] = branch.getEtag() != null ? branch.getEtag() : "N/A";
        }

        ConsoleUtil.printTable(headers, rows);
    }

    private void createBranch(FlexoMmsClient client, String orgId, String repoId) throws Exception {
        if (branchName == null || branchName.isEmpty()) {
            ConsoleUtil.error("Branch name is required for creation");
            System.exit(1);
        }

        ConsoleUtil.info("Creating branch '" + branchName + "'...");

        Branch branch = client.createBranch(orgId, repoId, branchName, fromCommit);

        if (branch != null) {
            ConsoleUtil.success("Branch '" + branchName + "' created successfully");
            if (branch.getCommitId() != null) {
                ConsoleUtil.info("Points to commit: " + branch.getCommitId());
            }
        } else {
            ConsoleUtil.error("Failed to create branch");
            System.exit(1);
        }
    }

    private void deleteBranch(FlexoMmsClient client, String orgId, String repoId) throws Exception {
        if (branchName == null || branchName.isEmpty()) {
            ConsoleUtil.error("Branch name is required for deletion");
            System.exit(1);
        }

        ConsoleUtil.warn("Branch deletion is not yet implemented in the MMS API");
        System.exit(1);
    }
}
