package org.openmbee.flexo.cli.commands;

import org.openmbee.flexo.cli.FlexoCLI;
import org.openmbee.flexo.cli.client.FlexoMmsClient;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.openmbee.flexo.cli.model.Collection;
import org.openmbee.flexo.cli.util.ConsoleUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Collection command - Manage collections, lightweight groupings of refs
 * (branches, locks or scratches) that can be queried as a single union graph.
 *
 * Collections are a Flexo MMS Layer 1 feature that extends the git-style
 * interface with cross-ref querying.
 */
@Command(
        name = "collection",
        description = "Manage collections (groupings of refs queryable as a union)",
        mixinStandardHelpOptions = true,
        subcommands = {
                CollectionCommand.ListCommand.class,
                CollectionCommand.GetCommand.class,
                CollectionCommand.CreateCommand.class,
                CollectionCommand.QueryCommand.class
        }
)
public class CollectionCommand extends BaseCommand {

    @ParentCommand
    protected FlexoCLI parent;

    @Override
    protected FlexoCLI getParentCli() {
        return parent;
    }

    @Override
    protected void executeCommand() {
        // Default behavior: list collections
        ListCommand list = new ListCommand();
        list.parent = this;
        list.run();
    }

    /**
     * Base class for collection subcommands. Resolves the FlexoCLI root through
     * the CollectionCommand parent so global options (--org, --remote) apply.
     */
    abstract static class CollectionSubCommand extends BaseCommand {

        /** Subclasses expose their picocli-injected CollectionCommand parent. */
        protected abstract CollectionCommand getCollectionParent();

        @Override
        protected FlexoCLI getParentCli() {
            CollectionCommand cc = getCollectionParent();
            return cc != null ? cc.parent : null;
        }

        protected String requireOrg(FlexoConfig config) {
            String orgId = getOrgId(config);
            if (orgId == null || orgId.isEmpty()) {
                throw new CommandExecutionException(
                    "Organization ID is required. Use --org or set default.org in config", 1);
            }
            return orgId;
        }
    }

    /**
     * List all collections in the organization.
     */
    @Command(
            name = "list",
            aliases = {"ls"},
            description = "List all collections in the organization",
            mixinStandardHelpOptions = true
    )
    static class ListCommand extends CollectionSubCommand {
        @ParentCommand
        protected CollectionCommand parent;

        @Override
        protected CollectionCommand getCollectionParent() {
            return parent;
        }

        @Override
        protected void executeCommand() throws Exception {
            FlexoConfig config = getConfig();
            String orgId = requireOrg(config);

            try (FlexoMmsClient client = createClient(config, true)) {
                ConsoleUtil.info("Listing collections in " + orgId + "...");

                List<Collection> collections = client.listCollections(orgId);

                if (collections.isEmpty()) {
                    ConsoleUtil.info("No collections found");
                    return;
                }

                String[] headers = {"Collection", "Collects", "ETag"};
                String[][] rows = new String[collections.size()][3];

                for (int i = 0; i < collections.size(); i++) {
                    Collection collection = collections.get(i);
                    rows[i][0] = collection.getName() != null ? collection.getName() : collection.getId();
                    rows[i][1] = String.valueOf(collection.getCollectedRefs().size());
                    rows[i][2] = collection.getEtag() != null ? collection.getEtag() : "N/A";
                }

                ConsoleUtil.printTable(headers, rows);
            }
        }
    }

    /**
     * Get details of a single collection.
     */
    @Command(
            name = "get",
            description = "Show details of a collection",
            mixinStandardHelpOptions = true
    )
    static class GetCommand extends CollectionSubCommand {
        @ParentCommand
        protected CollectionCommand parent;

        @Override
        protected CollectionCommand getCollectionParent() {
            return parent;
        }

        @Parameters(index = "0", description = "Collection ID")
        private String collectionId;

        @Override
        protected void executeCommand() throws Exception {
            FlexoConfig config = getConfig();
            String orgId = requireOrg(config);

            try (FlexoMmsClient client = createClient(config, true)) {
                Collection collection = client.getCollection(orgId, collectionId);

                if (collection == null) {
                    throw new CommandExecutionException("Collection '" + collectionId + "' not found", 1);
                }

                ConsoleUtil.info("Collection: " + collection.getId());
                if (collection.getEtag() != null) {
                    ConsoleUtil.info("  ETag: " + collection.getEtag());
                }
                ConsoleUtil.info("  Collects (" + collection.getCollectedRefs().size() + "):");
                for (String ref : collection.getCollectedRefs()) {
                    ConsoleUtil.info("    " + ref);
                }
            }
        }
    }

    /**
     * Create a new collection from one or more refs.
     */
    @Command(
            name = "create",
            description = "Create a collection from one or more branches",
            mixinStandardHelpOptions = true
    )
    static class CreateCommand extends CollectionSubCommand {
        @ParentCommand
        protected CollectionCommand parent;

        @Override
        protected CollectionCommand getCollectionParent() {
            return parent;
        }

        @Parameters(index = "0", description = "Collection ID (slug)")
        private String collectionId;

        @Option(names = {"--ref"}, required = true, description = "Branch name to collect (repeatable)")
        private List<String> refs;

        @Override
        protected void executeCommand() throws Exception {
            FlexoConfig config = getConfig();
            String orgId = requireOrg(config);
            String repoId = getRepoId(config);
            validateOrgAndRepo(orgId, repoId);

            if (refs == null || refs.isEmpty()) {
                throw new CommandExecutionException("At least one --ref is required", 1);
            }

            try (FlexoMmsClient client = createClient(config, true)) {
                // Resolve each ref name to a full branch IRI under the current repo.
                String baseUrl = client.getBaseUrl();
                java.util.List<String> refIris = new java.util.ArrayList<>();
                for (String ref : refs) {
                    if (ref.startsWith("http://") || ref.startsWith("https://")) {
                        refIris.add(ref);
                    } else {
                        refIris.add(String.format("%s/orgs/%s/repos/%s/branches/%s",
                                baseUrl, orgId, repoId, ref));
                    }
                }

                ConsoleUtil.info("Creating collection '" + collectionId + "' in " + orgId
                        + " with " + refIris.size() + " ref(s)...");

                Collection collection = client.createCollection(orgId, collectionId, refIris);

                if (collection != null) {
                    ConsoleUtil.success("Collection '" + collectionId + "' created successfully");
                    ConsoleUtil.info("Collects " + collection.getCollectedRefs().size() + " ref(s)");
                } else {
                    throw new CommandExecutionException("Failed to create collection", 1);
                }
            }
        }
    }

    /**
     * Run a SPARQL query across the union of a collection's collected graphs.
     */
    @Command(
            name = "query",
            description = "Run a SPARQL query across a collection's union graph",
            mixinStandardHelpOptions = true
    )
    static class QueryCommand extends CollectionSubCommand {
        @ParentCommand
        protected CollectionCommand parent;

        @Override
        protected CollectionCommand getCollectionParent() {
            return parent;
        }

        @Parameters(index = "0", description = "Collection ID")
        private String collectionId;

        @Option(names = {"-q", "--query"}, description = "SPARQL query string")
        private String query;

        @Option(names = {"-i", "--input"}, description = "Read SPARQL query from file")
        private String inputFile;

        @Option(names = {"-o", "--output"}, description = "Output file (default: stdout)")
        private String outputFile;

        @Override
        protected void executeCommand() throws Exception {
            FlexoConfig config = getConfig();
            String orgId = requireOrg(config);

            String sparql = query;
            if ((sparql == null || sparql.isEmpty()) && inputFile != null && !inputFile.isEmpty()) {
                sparql = new String(Files.readAllBytes(Paths.get(inputFile)));
            }
            if (sparql == null || sparql.isEmpty()) {
                throw new CommandExecutionException(
                    "A SPARQL query is required. Use -q/--query or -i/--input", 1);
            }

            try (FlexoMmsClient client = createClient(config, true)) {
                ConsoleUtil.info("Querying collection '" + collectionId + "' in " + orgId + "...");

                String result = client.queryCollection(orgId, collectionId, sparql);

                if (outputFile != null && !outputFile.isEmpty()) {
                    Files.write(Paths.get(outputFile), result.getBytes());
                    ConsoleUtil.info("Saved to: " + outputFile);
                } else {
                    System.out.println(result);
                }
            }
        }
    }
}
