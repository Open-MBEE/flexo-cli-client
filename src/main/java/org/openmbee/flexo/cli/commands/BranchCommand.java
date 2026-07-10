package org.openmbee.flexo.cli.commands;

import org.openmbee.flexo.cli.FlexoCLI;
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
public class BranchCommand extends BaseCommand {

    @ParentCommand
    protected FlexoCLI parent;

    @Override
    protected FlexoCLI getParentCli() {
        return parent;
    }

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
    protected void executeCommand() throws Exception {
        FlexoConfig config = getConfig();

        // Get org and repo from options or config
        String orgId = getOrgId(config);
        String repoId = getRepoId(config);
        
        // Validate org and repo
        validateOrgAndRepo(orgId, repoId);

        try (FlexoMmsClient client = createClient(config, true)) {
            if (create) {
                createBranch(client, orgId, repoId);
            } else if (delete) {
                deleteBranch(client, orgId, repoId);
            } else {
                listBranches(client, orgId, repoId);
            }
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
            throw new CommandExecutionException("Branch name is required for creation", 1);
        }

        ConsoleUtil.info("Creating branch '" + branchName + "'...");

        Branch branch = client.createBranch(orgId, repoId, branchName, fromCommit);

        if (branch != null) {
            ConsoleUtil.success("Branch '" + branchName + "' created successfully");
            if (branch.getCommitId() != null) {
                ConsoleUtil.info("Points to commit: " + branch.getCommitId());
            }
        } else {
            throw new CommandExecutionException("Failed to create branch", 1);
        }
    }

    private void deleteBranch(FlexoMmsClient client, String orgId, String repoId) throws Exception {
        if (branchName == null || branchName.isEmpty()) {
            throw new CommandExecutionException("Branch name is required for deletion", 1);
        }

        throw new CommandExecutionException("Branch deletion is not yet implemented in the MMS API", 1);
    }
}
