package org.openmbee.flexo.cli.commands;

import org.openmbee.flexo.cli.FlexoCLI;
import org.openmbee.flexo.cli.client.FlexoMmsClient;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.openmbee.flexo.cli.util.ConsoleUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Squash command - Collapse the linear commit path between two locks into a
 * single commit.
 *
 * Both references are locks; the source lock marks the start of the range and
 * the destination lock (which must point to the newer commit) receives the
 * squashed commit.
 */
@Command(
        name = "squash",
        description = "Squash the linear commit path between two locks into a single commit",
        mixinStandardHelpOptions = true
)
public class SquashCommand extends BaseCommand {

    @ParentCommand
    protected FlexoCLI parent;

    @Override
    protected FlexoCLI getParentCli() {
        return parent;
    }

    @Parameters(index = "0", description = "Source lock (start of the range)")
    private String srcLock;

    @Parameters(index = "1", description = "Destination lock (newer commit, receives the squash)")
    private String dstLock;

    @Override
    protected void executeCommand() throws Exception {
        FlexoConfig config = getConfig();

        String orgId = getOrgId(config);
        String repoId = getRepoId(config);

        validateOrgAndRepo(orgId, repoId);

        try (FlexoMmsClient client = createClient(config, true)) {
            ConsoleUtil.info("Squashing commits in " + orgId + "/" + repoId
                    + " between locks '" + srcLock + "' and '" + dstLock + "'...");

            String commitId = client.squash(orgId, repoId, srcLock, dstLock);

            ConsoleUtil.success("Squash completed successfully");
            ConsoleUtil.info("Commit: " + commitId);
        }
    }
}
