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

import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Push command - Commit model changes to a branch
 */
@Command(
        name = "push",
        description = "Commit model changes to a branch",
        mixinStandardHelpOptions = true
)
public class PushCommand implements Runnable {

    @ParentCommand
    private FlexoCLI parent;

    @Option(names = {"-b", "--branch"}, description = "Branch name")
    private String branchName;

    @Option(names = {"-m", "--message"}, description = "Commit message", required = true)
    private String commitMessage;

    @Option(names = {"-i", "--input"}, description = "Input file (default: stdin)")
    private String inputFile;

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
            // Read input model
            Model model;
            if (inputFile != null && !inputFile.isEmpty()) {
                ConsoleUtil.info("Reading model from: " + inputFile);
                model = RdfParser.parseFile(inputFile, rdfFormat);
            } else {
                ConsoleUtil.info("Reading model from stdin...");
                try (InputStream in = System.in) {
                    model = RdfParser.parseStream(in, rdfFormat);
                }
            }

            if (model == null || model.size() == 0) {
                ConsoleUtil.error("No valid RDF data found in input");
                System.exit(1);
            }

            ConsoleUtil.info("Parsed model with " + model.size() + " statements");

            // Validate model
            if (!RdfParser.validate(model)) {
                ConsoleUtil.error("Model validation failed");
                System.exit(1);
            }

            ConsoleUtil.info("Pushing to " + orgId + "/" + repoId + "/" + branch + "...");

            // Push model
            String commitId = client.putModel(orgId, repoId, branch, model, rdfFormat, commitMessage);

            ConsoleUtil.success("Model pushed successfully");
            if (commitId != null && !commitId.isEmpty()) {
                ConsoleUtil.info("Commit: " + commitId);
            }

        } catch (Exception e) {
            ConsoleUtil.error("Push failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
}
