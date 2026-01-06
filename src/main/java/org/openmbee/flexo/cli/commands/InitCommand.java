package org.openmbee.flexo.cli.commands;

import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.openmbee.flexo.cli.FlexoCLI;
import org.openmbee.flexo.cli.client.AuthenticationHandler;
import org.openmbee.flexo.cli.client.FlexoMmsClient;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.openmbee.flexo.cli.util.ConsoleUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Init command - Initialize a local Flexo MMS instance with default org and repo
 */
@Command(
        name = "init",
        description = "Initialize local Flexo MMS with default org 'localorg' and repo 'localrepo'",
        mixinStandardHelpOptions = true
)
public class InitCommand implements Runnable {

    @ParentCommand
    private FlexoCLI parent;

    @Option(names = {"-b", "--branch"}, description = "Initial branch name (default: master)")
    private String branchId = "master";

    @Option(names = {"--force"}, description = "Force initialization even if resources exist")
    private boolean force = false;

    @Override
    public void run() {
        FlexoConfig config = FlexoCLI.getConfig();

        // Get org and repo from parent options or use defaults
        String orgId = parent.getOrgId() != null ? parent.getOrgId() : "localorg";
        String repoId = parent.getRepoId() != null ? parent.getRepoId() : "localrepo";

        ConsoleUtil.info("Initializing Flexo MMS at " + config.getMmsUrl());
        ConsoleUtil.info("This will:");
        ConsoleUtil.info("  1. Generate and load cluster configuration (users, policies)");
        ConsoleUtil.info("  2. Create org: " + orgId);
        ConsoleUtil.info("  3. Create repo: " + repoId);
        ConsoleUtil.info("  4. Create branch: " + branchId);

        // Create authentication handler
        AuthenticationHandler authHandler = new AuthenticationHandler(
                config.isAuthEnabled(),
                config.getSshKeyPath(),
                config.isLocalMode(),
                config.getLocalUser(),
                config.getLocalJwtSecret()
        );

        try (FlexoMmsClient client = new FlexoMmsClient(config.getMmsUrl(), authHandler)) {
            // Step 0: Generate and load cluster.trig
            generateAndLoadClusterConfig(client, config.getMmsUrl());

            // Step 1: Create organization
            createOrg(client, orgId);

            // Step 2: Create repository (which automatically creates the master branch)
            createRepo(client, orgId, repoId);

            ConsoleUtil.success("Initialization complete!");
            ConsoleUtil.info("Note: The default 'master' branch was created automatically with the repository");

            // Update configuration file with defaults
            updateConfigDefaults(config, orgId, repoId);

            ConsoleUtil.info("");
            ConsoleUtil.info("Configuration updated in ~/.flexo/config with:");
            ConsoleUtil.info("  default.org=" + orgId);
            ConsoleUtil.info("  default.repo=" + repoId);
            ConsoleUtil.info("");
            ConsoleUtil.info("You can now use the CLI without specifying org/repo:");
            ConsoleUtil.info("  flexo branch --list");
            ConsoleUtil.info("  flexo pull master");
            ConsoleUtil.info("  flexo push master --message \"My changes\" --input model.ttl");

        } catch (Exception e) {
            ConsoleUtil.error("Initialization failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    private void createOrg(FlexoMmsClient client, String orgId) throws Exception {
        ConsoleUtil.info("Creating organization '" + orgId + "'...");

        // Send empty RDF - the server will fill in the required properties
        String orgRdf = "";

        HttpPut request = new HttpPut(client.getBaseUrl() + "/orgs/" + orgId);
        request.setHeader("Content-Type", "text/turtle");
        request.setEntity(new StringEntity(orgRdf, ContentType.parse("text/turtle")));

        try {
            String response = client.executeRequest(request);
            ConsoleUtil.success("  Organization created");
            if (parent.isVerbose()) {
                ConsoleUtil.debug("Response: " + response);
            }
        } catch (Exception e) {
            if (e.getMessage().contains("409") || e.getMessage().contains("Conflict")) {
                ConsoleUtil.warn("  Organization already exists");
                if (!force) {
                    throw new Exception("Organization '" + orgId + "' already exists. Use --force to override.");
                }
            } else {
                throw e;
            }
        }
    }

    private void createRepo(FlexoMmsClient client, String orgId, String repoId) throws Exception {
        ConsoleUtil.info("Creating repository '" + repoId + "'...");

        // Send empty RDF - the server will fill in the required properties
        String repoRdf = "";

        HttpPut request = new HttpPut(client.getBaseUrl() + "/orgs/" + orgId + "/repos/" + repoId);
        request.setHeader("Content-Type", "text/turtle");
        request.setEntity(new StringEntity(repoRdf, ContentType.parse("text/turtle")));

        try {
            String response = client.executeRequest(request);
            ConsoleUtil.success("  Repository created");
            if (parent.isVerbose()) {
                ConsoleUtil.debug("Response: " + response);
            }
        } catch (Exception e) {
            if (e.getMessage().contains("409") || e.getMessage().contains("Conflict")) {
                ConsoleUtil.warn("  Repository already exists");
                if (!force) {
                    throw new Exception("Repository '" + repoId + "' already exists. Use --force to override.");
                }
            } else {
                throw e;
            }
        }
    }

    private void createBranch(FlexoMmsClient client, String orgId, String repoId, String branchId) throws Exception {
        ConsoleUtil.info("Creating branch '" + branchId + "'...");

        // Send empty RDF - the server will fill in the required properties
        String branchRdf = "";

        HttpPut request = new HttpPut(client.getBaseUrl() + "/orgs/" + orgId + "/repos/" + repoId + "/branches/" + branchId);
        request.setHeader("Content-Type", "text/turtle");
        request.setHeader("If-None-Match", "*"); // Create only if doesn't exist
        request.setEntity(new StringEntity(branchRdf, ContentType.parse("text/turtle")));

        try {
            String response = client.executeRequest(request);
            ConsoleUtil.success("  Branch created");
            if (parent.isVerbose()) {
                ConsoleUtil.debug("Response: " + response);
            }
        } catch (Exception e) {
            if (e.getMessage().contains("412") || e.getMessage().contains("Precondition")) {
                ConsoleUtil.warn("  Branch already exists");
                if (!force) {
                    throw new Exception("Branch '" + branchId + "' already exists. Use --force to override.");
                }
            } else if (e.getMessage().contains("400")) {
                // Try alternative approach - creating with a self-reference commit
                ConsoleUtil.warn("  Retrying with alternative method...");
                createBranchAlternative(client, orgId, repoId, branchId);
            } else {
                throw e;
            }
        }
    }

    private void createBranchAlternative(FlexoMmsClient client, String orgId, String repoId, String branchId) throws Exception {
        // Send empty RDF - the server will fill in the required properties
        String branchRdf = "";

        HttpPut request = new HttpPut(client.getBaseUrl() + "/orgs/" + orgId + "/repos/" + repoId + "/branches/" + branchId);
        request.setHeader("Content-Type", "text/turtle");
        request.setEntity(new StringEntity(branchRdf, ContentType.parse("text/turtle")));

        String response = client.executeRequest(request);
        ConsoleUtil.success("  Branch created (alternative method)");
        if (parent.isVerbose()) {
            ConsoleUtil.debug("Response: " + response);
        }
    }

    private void generateAndLoadClusterConfig(FlexoMmsClient client, String mmsUrl) throws Exception {
        ConsoleUtil.info("Generating cluster configuration...");

        // For local docker deployments, use internal service name
        String clusterBaseUrl = mmsUrl.contains("localhost") ? "http://layer1-service" : mmsUrl;

        // Generate cluster.trig using TypeScript deploy script
        ProcessBuilder pb = new ProcessBuilder(
                "npx", "ts-node", "src/main.ts", clusterBaseUrl
        );
        pb.directory(new java.io.File("../flexo-mms-layer1-service/deploy"));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder trigContent = new StringBuilder();

        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                trigContent.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("Failed to generate cluster.trig: process exited with code " + exitCode);
        }

        ConsoleUtil.success("  Cluster configuration generated");
        ConsoleUtil.info("Loading cluster configuration into Fuseki...");

        // Determine Fuseki URL from MMS URL (default: replace 8080 with 3030)
        // Use /ds/data without ?default to load all named graphs from TriG
        String fusekiUrl = mmsUrl.replace(":8080", ":3030").replaceFirst("http://([^/]+).*", "http://$1/ds/data");

        // POST the generated TriG to Fuseki
        org.apache.hc.client5.http.classic.methods.HttpPost post =
                new org.apache.hc.client5.http.classic.methods.HttpPost(fusekiUrl);
        post.setHeader("Content-Type", "application/trig");
        post.setEntity(new StringEntity(trigContent.toString(), ContentType.parse("application/trig")));

        try (org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient =
                org.apache.hc.client5.http.impl.classic.HttpClients.createDefault()) {
            try (org.apache.hc.client5.http.impl.classic.CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getCode();
                if (statusCode < 200 || statusCode >= 300) {
                    String body = response.getEntity() != null ?
                            org.apache.hc.core5.http.io.entity.EntityUtils.toString(response.getEntity()) : "";
                    throw new Exception("Failed to load cluster config into Fuseki: HTTP " + statusCode + " - " + body);
                }
            }
        }

        ConsoleUtil.success("  Cluster configuration loaded into Fuseki");
    }

    private void updateConfigDefaults(FlexoConfig config, String orgId, String repoId) throws Exception {
        ConsoleUtil.info("Updating configuration file...");

        // Set the default org and repo in the config
        config.set("default.org", orgId);
        config.set("default.repo", repoId);

        // Ensure local mode settings are set if not already present
        if (config.get("local.mode") == null) {
            config.set("local.mode", "true");
        }
        if (config.get("local.user") == null) {
            config.set("local.user", "root");
        }
        if (config.get("local.jwtSecret") == null) {
            config.set("local.jwtSecret", "devsecretpleasechangeinproduction1234567890");
        }
        if (config.get("default.branch") == null) {
            config.set("default.branch", "master");
        }

        // Save the config
        config.save();

        ConsoleUtil.success("  Configuration saved");
    }
}
