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

import java.io.OutputStream;

/**
 * Pull command - Fetch model from a branch
 */
@Command(
        name = "pull",
        description = "Fetch model from a branch",
        mixinStandardHelpOptions = true
)
public class PullCommand extends BaseCommand {

    @ParentCommand
    protected FlexoCLI parent;

    @Option(names = {"-b", "--branch"}, description = "Branch name")
    private String branchName;

    @Option(names = {"-o", "--output"}, description = "Output file (default: stdout)")
    private String outputFile;

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
        }
    }
}
