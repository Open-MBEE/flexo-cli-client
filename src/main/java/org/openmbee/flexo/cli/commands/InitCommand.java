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
 * Automatically starts Docker services and sets up the complete environment
 */
@Command(
        name = "init",
        description = "Initialize local Flexo MMS (starts Docker, creates org 'localorg' and repo 'localrepo')",
        mixinStandardHelpOptions = true
)
public class InitCommand implements Runnable {

    @ParentCommand
    private FlexoCLI parent;

    @Option(names = {"-b", "--branch"}, description = "Initial branch name (default: master)")
    private String branchId = "master";

    @Option(names = {"--force"}, description = "Force initialization even if resources exist")
    private boolean force = false;

    @Option(names = {"--skip-docker"}, description = "Skip Docker services startup (assumes services already running)")
    private boolean skipDocker = false;

    @Override
    public void run() {
        FlexoConfig config = FlexoCLI.getConfig();

        // Get org and repo from parent options or use defaults
        String orgId = parent.getOrgId() != null ? parent.getOrgId() : "localorg";
        String repoId = parent.getRepoId() != null ? parent.getRepoId() : "localrepo";

        ConsoleUtil.info("Initializing Flexo MMS at " + config.getMmsUrl());
        ConsoleUtil.info("This will:");
        if (!skipDocker) {
            ConsoleUtil.info("  0. Start Docker services (Fuseki and MMS Layer 1)");
        }
        ConsoleUtil.info("  1. Generate and load cluster configuration (users, policies)");
        ConsoleUtil.info("  2. Create org: " + orgId);
        ConsoleUtil.info("  3. Create repo: " + repoId);
        ConsoleUtil.info("     (master branch is created automatically by the service)");

        try {
            // Step 0: Start Docker services
            if (!skipDocker) {
                startDockerServices();
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
                // Step 1: Generate and load cluster.trig
                generateAndLoadClusterConfig(client, config.getMmsUrl());

                // Step 2: Create organization
                createOrg(client, orgId);

                // Step 3: Create repository (master branch is created automatically)
                createRepo(client, orgId, repoId);

                ConsoleUtil.success("Initialization complete!");

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
            }

        } catch (Exception e) {
            ConsoleUtil.error("Initialization failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    private void startDockerServices() throws Exception {
        ConsoleUtil.info("Starting Docker services...");

        java.io.File composeFile = extractDockerComposeFromClasspath();
        if (composeFile == null) {
            throw new Exception("flexo-mms-docker-compose.yml not found in classpath. " +
                    "Please ensure the application is properly packaged.");
        }

        ConsoleUtil.info("  Using docker-compose file: " + composeFile.getAbsolutePath());

        if (!isDockerAvailable()) {
            throw new Exception("Docker is not available. Please install Docker and ensure it's running.");
        }

        boolean success = runDockerCompose(composeFile);

        if (!success) {
            throw new Exception("Failed to start Docker services. Please check Docker logs:\n" +
                    "  docker compose logs\n" +
                    "  or: docker-compose logs");
        }

        ConsoleUtil.success("  Docker services started");
        ConsoleUtil.info("  Waiting for services to be ready...");

        waitForServices();
    }

    private boolean runDockerCompose(java.io.File composeFile) throws Exception {
        String[] commands = new String[]{"docker compose", "docker-compose"};

        for (String command : commands) {
            String[] cmdParts = command.split(" ");
            ProcessBuilder pb = new ProcessBuilder(
                    cmdParts[0], cmdParts[1], "-f", composeFile.getAbsolutePath(), "up", "-d"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (parent.isVerbose()) {
                        ConsoleUtil.debug("  " + line);
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return true;
            }
        }

        return false;
    }

    private java.io.File extractDockerComposeFromClasspath() throws Exception {
        // Load docker-compose file from classpath
        java.io.InputStream resourceStream = getClass().getClassLoader()
                .getResourceAsStream("flexo-mms-docker-compose.yml");
        
        if (resourceStream == null) {
            return null;
        }

        // Create temporary file
        java.io.File tempFile = java.io.File.createTempFile("flexo-mms-docker-compose-", ".yml");
        tempFile.deleteOnExit(); // Clean up on JVM exit

        // Copy resource to temporary file
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
             java.io.BufferedOutputStream bos = new java.io.BufferedOutputStream(fos)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = resourceStream.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            bos.flush();
        } finally {
            resourceStream.close();
        }

        if (parent.isVerbose()) {
            ConsoleUtil.debug("  Extracted docker-compose file to: " + tempFile.getAbsolutePath());
        }

        return tempFile;
    }

    private boolean isDockerAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void waitForServices() throws Exception {
        // Wait for Fuseki (port 3030) and MMS (port 8080) to be available
        int maxAttempts = 30;
        int attempt = 0;
        
        while (attempt < maxAttempts) {
            try {
                // Check Fuseki
                java.net.Socket fusekiSocket = new java.net.Socket();
                fusekiSocket.connect(new java.net.InetSocketAddress("localhost", 3030), 1000);
                fusekiSocket.close();

                // Check MMS
                java.net.Socket mmsSocket = new java.net.Socket();
                mmsSocket.connect(new java.net.InetSocketAddress("localhost", 8080), 1000);
                mmsSocket.close();

                // Both services are up
                ConsoleUtil.success("  Services are ready");
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt < maxAttempts) {
                    Thread.sleep(2000);
                    if (parent.isVerbose()) {
                        ConsoleUtil.debug("  Waiting for services... (attempt " + attempt + "/" + maxAttempts + ")");
                    }
                }
            }
        }

        throw new Exception("Services did not become ready within timeout. Please check Docker logs:\n" +
                "  docker logs layer1-service\n" +
                "  docker logs quad-store-server");
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

    private void createInitialBranch(FlexoMmsClient client, String orgId, String repoId, String branchId) throws Exception {
        ConsoleUtil.info("Creating initial branch '" + branchId + "'...");

        // First, create an empty model commit on the branch
        String branchUrl = client.getBaseUrl() + "/orgs/" + orgId + "/repos/" + repoId + "/branches/" + branchId;
        String graphUrl = branchUrl + "/graph";

        // Create empty RDF model
        String emptyModel = "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n";

        // PUT empty model to create initial commit
        HttpPut graphRequest = new HttpPut(graphUrl);
        graphRequest.setHeader("Content-Type", "text/turtle");
        graphRequest.setHeader("X-Commit-Message", "Initial commit");
        graphRequest.setEntity(new StringEntity(emptyModel, ContentType.parse("text/turtle")));

        try {
            String response = client.executeRequest(graphRequest);
            ConsoleUtil.success("  Branch '" + branchId + "' created with initial commit");
            if (parent.isVerbose()) {
                ConsoleUtil.debug("Response: " + response);
            }
        } catch (Exception e) {
            if (e.getMessage().contains("404") || e.getMessage().contains("Not Found")) {
                // Branch doesn't exist yet, need to create it first with a commit reference
                ConsoleUtil.warn("  Branch needs to be created first, trying alternative approach...");
                createBranchWithCommit(client, orgId, repoId, branchId);
            } else if (e.getMessage().contains("409") || e.getMessage().contains("Conflict")) {
                ConsoleUtil.warn("  Branch already exists");
                if (!force) {
                    throw new Exception("Branch '" + branchId + "' already exists. Use --force to override.");
                }
            } else {
                throw e;
            }
        }
    }

    private void createBranchWithCommit(FlexoMmsClient client, String orgId, String repoId, String branchId) throws Exception {
        // Create a self-referencing commit first by PUTting an empty graph
        // This creates both the branch and an initial commit atomically
        String graphUrl = client.getBaseUrl() + "/orgs/" + orgId + "/repos/" + repoId + "/branches/" + branchId + "/graph";

        String emptyModel = "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n";

        HttpPut request = new HttpPut(graphUrl);
        request.setHeader("Content-Type", "text/turtle");
        request.setHeader("X-Commit-Message", "Initial commit");
        request.setEntity(new StringEntity(emptyModel, ContentType.parse("text/turtle")));

        String response = client.executeRequest(request);
        ConsoleUtil.success("  Branch '" + branchId + "' created with initial commit");
        if (parent.isVerbose()) {
            ConsoleUtil.debug("Response: " + response);
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
        ConsoleUtil.info("Loading cluster configuration...");

        java.io.InputStream resourceStream = getClass().getClassLoader()
                .getResourceAsStream("cluster.trig");
        if (resourceStream == null) {
            throw new Exception("cluster.trig not found in classpath");
        }

        StringBuilder trigContent = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(resourceStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                trigContent.append(line).append("\n");
            }
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

        config.set("default.org", orgId);
        config.set("default.repo", repoId);

        config.save();

        ConsoleUtil.success("  Configuration saved");
    }
}
