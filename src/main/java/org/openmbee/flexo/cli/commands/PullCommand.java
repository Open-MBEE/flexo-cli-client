package org.openmbee.flexo.cli.commands;

import org.apache.jena.rdf.model.Model;
import org.openmbee.flexo.cli.FlexoCLI;
import org.openmbee.flexo.cli.client.AuthenticationHandler;
import org.openmbee.flexo.cli.client.FlexoMmsClient;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.openmbee.flexo.cli.util.ConsoleUtil;
import org.openmbee.flexo.cli.util.RdfParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Pull command - Fetch model from a branch
 */
@Command(
        name = "pull",
        description = "Fetch model from a branch",
        mixinStandardHelpOptions = true
)
public class PullCommand implements Runnable {

    @ParentCommand
    private FlexoCLI parent;

    @Option(names = {"-b", "--branch"}, description = "Branch name")
    private String branchName;

    @Option(names = {"-o", "--output"}, description = "Output file (default: stdout)")
    private String outputFile;

    @Option(names = {"-f", "--format"}, description = "RDF format (turtle, jsonld, rdfxml, ntriples)")
    private String format;

    @Parameters(index = "0", arity = "0..1", description = "Branch name (alternative to --branch)")
    private String branchParam;

    @Override
    public void run() {
        FlexoConfig config = FlexoCLI.getConfig();

        // Determine branch name
        String branch = branchName != null ? branchName : branchParam;
        if (branch == null || branch.isEmpty()) {
            branch = config.getDefaultBranch();
        }

        if (branch == null || branch.isEmpty()) {
            ConsoleUtil.error("Branch name is required. Use -b/--branch or set default.branch in config");
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

        // Determine format
        String rdfFormat = format != null ? format : config.getRdfFormat();

        // Create authentication handler
        AuthenticationHandler authHandler = new AuthenticationHandler(
                config.isAuthEnabled(),
                config.getSshKeyPath(),
                config.isLocalMode(),
                config.getLocalUser(),
                config.getLocalJwtSecret()
        );

        try (FlexoMmsClient client = new FlexoMmsClient(config.getMmsUrl(), authHandler)) {
            ConsoleUtil.info("Pulling from " + orgId + "/" + repoId + "/" + branch + "...");

            // Fetch model
            Model model = client.getModel(orgId, repoId, branch, rdfFormat);

            if (model == null || model.size() == 0) {
                ConsoleUtil.warn("No model data found in branch '" + branch + "'");
                return;
            }

            ConsoleUtil.success("Fetched model with " + model.size() + " statements");

            // Write output
            if (outputFile != null && !outputFile.isEmpty()) {
                RdfParser.toFile(model, outputFile, rdfFormat);
                ConsoleUtil.info("Saved to: " + outputFile);
            } else {
                // Write to stdout
                try (OutputStream out = System.out) {
                    RdfParser.toStream(model, out, rdfFormat);
                }
            }

        } catch (Exception e) {
            ConsoleUtil.error("Pull failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
}
