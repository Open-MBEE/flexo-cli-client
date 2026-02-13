package org.openmbee.flexo.cli.commands;

import org.apache.jena.rdf.model.Model;
import org.openmbee.flexo.cli.FlexoCLI;
import org.openmbee.flexo.cli.client.FlexoMmsClient;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.openmbee.flexo.cli.util.ConsoleUtil;
import org.openmbee.flexo.cli.util.RdfParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.InputStream;

/**
 * Push command - Commit model changes to a branch
 */
@Command(
        name = "push",
        description = "Commit model changes to a branch",
        mixinStandardHelpOptions = true
)
public class PushCommand extends BaseCommand {

    @ParentCommand
    protected FlexoCLI parent;

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
    protected void executeCommand() throws Exception {
        FlexoConfig config = getConfig();

        // Determine branch name
        String branch = branchName != null ? branchName : branchParam;
        if (branch == null || branch.isEmpty()) {
            branch = config.getDefaultBranch();
        }

        if (branch == null || branch.isEmpty()) {
            throw new CommandExecutionException(
                "Branch name is required. Use -b/--branch or set default.branch in config", 1);
        }

        // Get org and repo
        String orgId = getOrgId(config);
        String repoId = getRepoId(config);
        
        // Validate org and repo
        validateOrgAndRepo(orgId, repoId);

        // Determine format
        String rdfFormat = format != null ? format : config.getRdfFormat();

        try (FlexoMmsClient client = createClient(config, true)) {
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
                throw new CommandExecutionException("No valid RDF data found in input", 1);
            }

            ConsoleUtil.info("Parsed model with " + model.size() + " statements");

            // Validate model
            if (!RdfParser.validate(model)) {
                throw new CommandExecutionException("Model validation failed", 1);
            }

            ConsoleUtil.info("Pushing to " + orgId + "/" + repoId + "/" + branch + "...");

            // Push model
            String commitId = client.putModel(orgId, repoId, branch, model, rdfFormat, commitMessage);

            ConsoleUtil.success("Model pushed successfully");
            if (commitId != null && !commitId.isEmpty()) {
                ConsoleUtil.info("Commit: " + commitId);
            }
        }
    }
}
