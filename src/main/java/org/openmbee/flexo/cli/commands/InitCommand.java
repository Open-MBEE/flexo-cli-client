package org.openmbee.flexo.cli.commands;

import java.io.FileReader;
import java.io.FileWriter;
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
            // Step 1: Start Docker services
            if (!skipDocker) {
                startFuseki();
                loadClusterConfig(config.getMmsUrl());
                startLayer1Service(config.getMmsUrl());
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
                // Step 1: Generate and load cluster.trig (already done if skipDocker is false)
                if (skipDocker) {
                    generateAndLoadClusterConfig(client, config.getMmsUrl());
                }

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

    private void startFuseki() throws Exception {
        ConsoleUtil.info("Starting Fuseki (quad-store-server)...");

        java.io.File composeFile = extractDockerComposeFromClasspath();
        if (composeFile == null) {
            throw new Exception("flexo-mms-docker-compose.yml not found in classpath. " +
                    "Please ensure the application is properly packaged.");
        }

        ConsoleUtil.info("  Using docker-compose file: " + composeFile.getAbsolutePath());

        if (!isDockerAvailable()) {
            throw new Exception("Docker is not available. Please install Docker and ensure it's running.");
        }

        boolean success = runDockerComposeService(composeFile, "quad-store-server");

        if (!success) {
            throw new Exception("Failed to start Fuseki. Please check Docker logs:\n" +
                    "  docker logs quad-store-server");
        }

        ConsoleUtil.success("  Fuseki started");
        ConsoleUtil.info("  Waiting for Fuseki to be ready...");

        waitForFuseki();
    }

    private void startLayer1Service(String mmsUrl) throws Exception {
        ConsoleUtil.info("Starting layer1-service...");

        java.io.File composeFile = extractDockerComposeFromClasspath();
        if (composeFile == null) {
            throw new Exception("flexo-mms-docker-compose.yml not found in classpath. " +
                    "Please ensure the application is properly packaged.");
        }

        boolean success = runDockerComposeService(composeFile, "layer1-service");

        if (!success) {
            throw new Exception("Failed to start layer1-service. Please check Docker logs:\n" +
                    "  docker logs layer1-service");
        }

        ConsoleUtil.success("  layer1-service started");
        ConsoleUtil.info("  Waiting for layer1-service to be ready...");

        waitForLayer1Service();

        ConsoleUtil.info("  Verifying layer1-service health...");
        waitForLayer1ServiceHealth(mmsUrl);
    }

    private java.io.File modifyDockerComposeWithJwtSecret(java.io.File originalFile, String jwtSecret) throws Exception {
        java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"));
        java.io.File tempFile;
        if (System.getProperty("os.name").toLowerCase().contains("unix")) {
            tempFile = java.nio.file.Files.createTempFile(
                tempDir.toPath(),
                "flexo-mms-docker-compose-",
                ".yml",
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")
                )
            ).toFile();
        } else {
            tempFile = java.nio.file.Files.createTempFile(
                "flexo-mms-docker-compose-",
                ".yml"
            ).toFile();
            tempFile.setReadable(true, false);
            tempFile.setWritable(true, true);
            tempFile.setExecutable(true, false);
        }
        tempFile.deleteOnExit();

        StringBuilder content = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(originalFile))) {
            String line;
            boolean inLayer1Service = false;
            int indentLevel = 0;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("layer1-service:")) {
                    inLayer1Service = true;
                    indentLevel = line.indexOf("layer1-service");
                } else if (inLayer1Service && !line.trim().isEmpty() && !line.startsWith(" ")) {
                    inLayer1Service = false;
                }

                if (inLayer1Service && line.trim().startsWith("- JWT_SECRET=")) {
                    line = "      - JWT_SECRET=" + jwtSecret;
                } else if (inLayer1Service && line.trim().startsWith("- JWT_SECRET=${")) {
                    line = "      - JWT_SECRET=" + jwtSecret;
                }

                content.append(line).append("\n");
            }
        }

        try (java.io.FileWriter writer = new java.io.FileWriter(tempFile)) {
            writer.write(content.toString());
        }

        if (parent.isVerbose()) {
            ConsoleUtil.debug("  Modified docker-compose with JWT secret to: " + tempFile.getAbsolutePath());
        }

        return tempFile;
    }

    private void waitForLayer1ServiceHealth(String mmsUrl) throws Exception {
        String healthUrl = mmsUrl + "/health";
        int maxAttempts = 15;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                java.net.URL url = new java.net.URL(healthUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int statusCode = conn.getResponseCode();
                if (statusCode >= 200 && statusCode < 300) {
                    ConsoleUtil.success("  layer1-service health check passed");
                    return;
                }
            } catch (Exception e) {
            }

            attempt++;
            if (attempt < maxAttempts) {
                Thread.sleep(2000);
                if (parent.isVerbose()) {
                    ConsoleUtil.debug("  Waiting for layer1-service health... (attempt " + attempt + "/" + maxAttempts + ")");
                }
            }
        }

        ConsoleUtil.warn("  layer1-service health check timed out, proceeding anyway...");
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

        // Create temporary file with secure permissions
        java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"));
        java.io.File tempFile;
        if (System.getProperty("os.name").toLowerCase().contains("unix")) {
            tempFile = java.nio.file.Files.createTempFile(
                tempDir.toPath(),
                "flexo-mms-docker-compose-",
                ".yml",
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")
                )
            ).toFile();
        } else {
            tempFile = java.nio.file.Files.createTempFile(
                "flexo-mms-docker-compose-",
                ".yml"
            ).toFile();
            tempFile.setReadable(true, false);
            tempFile.setWritable(true, true);
            tempFile.setExecutable(true, false);
        }
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
        // Wait for Fuseki (port 3030) first
        int maxAttempts = 30;
        int attempt = 0;

        ConsoleUtil.info("  Waiting for Fuseki (quad-store-server)...");
        while (attempt < maxAttempts) {
            try {
                try (java.net.Socket fusekiSocket = new java.net.Socket()) {
                    fusekiSocket.connect(new java.net.InetSocketAddress("localhost", 3030), 1000);
                }
                ConsoleUtil.success("  Fuseki is ready");
                break;
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxAttempts) {
                    throw new Exception("Fuseki did not become ready within timeout. Please check Docker logs:\n" +
                            "  docker logs quad-store-server");
                }
                Thread.sleep(2000);
                if (parent.isVerbose()) {
                    ConsoleUtil.debug("  Waiting for Fuseki... (attempt " + attempt + "/" + maxAttempts + ")");
                }
            }
        }

        // Now wait for layer1-service (port 8080)
        attempt = 0;
        ConsoleUtil.info("  Waiting for layer1-service...");
        while (attempt < maxAttempts) {
            try {
                try (java.net.Socket mmsSocket = new java.net.Socket()) {
                    mmsSocket.connect(new java.net.InetSocketAddress("localhost", 8080), 1000);
                }
                ConsoleUtil.success("  Services are ready");
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxAttempts) {
                    throw new Exception("layer1-service did not become ready within timeout. Please check Docker logs:\n" +
                            "  docker logs layer1-service");
                }
                Thread.sleep(2000);
                if (parent.isVerbose()) {
                    ConsoleUtil.debug("  Waiting for layer1-service... (attempt " + attempt + "/" + maxAttempts + ")");
                }
            }
        }
    }

    private void waitForFuseki() throws Exception {
        int maxAttempts = 30;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                try (java.net.Socket fusekiSocket = new java.net.Socket()) {
                    fusekiSocket.connect(new java.net.InetSocketAddress("localhost", 3030), 1000);
                }
                ConsoleUtil.success("  Fuseki is ready");
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxAttempts) {
                    throw new Exception("Fuseki did not become ready within timeout. Please check Docker logs:\n" +
                            "  docker logs quad-store-server");
                }
                Thread.sleep(2000);
                if (parent.isVerbose()) {
                    ConsoleUtil.debug("  Waiting for Fuseki... (attempt " + attempt + "/" + maxAttempts + ")");
                }
            }
        }
    }

    private void waitForLayer1Service() throws Exception {
        int maxAttempts = 30;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                try (java.net.Socket mmsSocket = new java.net.Socket()) {
                    mmsSocket.connect(new java.net.InetSocketAddress("localhost", 8080), 1000);
                }
                ConsoleUtil.success("  layer1-service is ready");
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxAttempts) {
                    throw new Exception("layer1-service did not become ready within timeout. Please check Docker logs:\n" +
                            "  docker logs layer1-service");
                }
                Thread.sleep(2000);
                if (parent.isVerbose()) {
                    ConsoleUtil.debug("  Waiting for layer1-service... (attempt " + attempt + "/" + maxAttempts + ")");
                }
            }
        }
    }

    private boolean runDockerComposeService(java.io.File composeFile, String serviceName) throws Exception {
        String[] commands = new String[]{"docker compose", "docker-compose"};

        for (String command : commands) {
            String[] cmdParts = command.split(" ");
            ProcessBuilder pb = new ProcessBuilder(
                    cmdParts[0], cmdParts[1], "-f", composeFile.getAbsolutePath(), "up", "-d", serviceName
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

    private void loadClusterConfig(String mmsUrl) throws Exception {
        ConsoleUtil.info("Loading cluster configuration into Fuseki...");

        // First verify Fuseki is available before attempting to load cluster config
        String fusekiUrl = mmsUrl.replace(":8080", ":3030").replaceFirst("http://([^/]+).*", "http://$1/ds/data");
        verifyFusekiAvailable(fusekiUrl);

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

        ConsoleUtil.success("  Cluster configuration loaded");

        int maxAttempts = 5;
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxAttempts) {
            try {
                Thread.sleep(2000);
                loadTrigToFuseki(fusekiUrl, trigContent.toString());
                ConsoleUtil.success("  Cluster configuration loaded into Fuseki");
                waitForFusekiIndex();
                return;
            } catch (Exception e) {
                lastException = e;
                attempt++;
                if (parent.isVerbose()) {
                    ConsoleUtil.debug("  Attempt " + attempt + " failed: " + e.getMessage());
                }
            }
        }

        throw new Exception("Failed to load cluster config into Fuseki after " + maxAttempts + " attempts", lastException);
    }

    private void loadTrigToFuseki(String fusekiUrl, String trigContent) throws Exception {
        java.net.URL url = new java.net.URL(fusekiUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/trig");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        try (java.io.OutputStream os = conn.getOutputStream()) {
            byte[] input = trigContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int statusCode = conn.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            String body = "";
            try (java.io.InputStream is = conn.getErrorStream()) {
                if (is != null) {
                    body = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            throw new Exception("HTTP " + statusCode + " - " + body);
        }
    }

    private void waitForFusekiIndex() throws Exception {
        ConsoleUtil.info("  Ensuring Fuseki index is ready...");

        String fusekiUrl = "http://localhost:3030/ds/sparql";

        int maxAttempts = 10;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                org.apache.hc.client5.http.classic.methods.HttpGet get =
                        new org.apache.hc.client5.http.classic.methods.HttpGet(fusekiUrl + "?query=SELECT%20%3Fsubject%20WHERE%20%7B%3Fsubject%20%3Chttp%3A%2F%2Fwww.w3.org%2F1999%2F02%2F22-rdf-syntax-ns%23type%3E%20%3Fobject%7D%20LIMIT%201");
                get.setHeader("Accept", "application/sparql-results+xml");

                try (org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient =
                        org.apache.hc.client5.http.impl.classic.HttpClients.createDefault()) {
                    try (org.apache.hc.client5.http.impl.classic.CloseableHttpResponse response = httpClient.execute(get)) {
                        int statusCode = response.getCode();
                        if (statusCode >= 200 && statusCode < 300) {
                            ConsoleUtil.success("  Fuseki index is ready");
                            return;
                        }
                    }
                }
            } catch (Exception e) {
            }

            attempt++;
            if (attempt < maxAttempts) {
                Thread.sleep(1000);
            }
        }

        ConsoleUtil.warn("  Could not verify Fuseki index status, proceeding anyway...");
    }

    private void verifyFusekiAvailable(String fusekiUrl) throws Exception {
        ConsoleUtil.info("  Verifying Fuseki quadstore is available...");
        
        int maxAttempts = 10;
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < maxAttempts) {
            try {
                java.net.URL url = new java.net.URL(fusekiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                int statusCode = conn.getResponseCode();
                // Accept 200 (OK) or 404 (dataset exists but no data yet) as success
                if (statusCode >= 200 && statusCode < 500) {
                    ConsoleUtil.success("  Fuseki quadstore is available");
                    return;
                }
                
                lastException = new Exception("HTTP " + statusCode);
            } catch (Exception e) {
                lastException = e;
            }
            
            attempt++;
            if (attempt < maxAttempts) {
                Thread.sleep(2000);
                if (parent.isVerbose()) {
                    ConsoleUtil.debug("  Waiting for Fuseki quadstore... (attempt " + attempt + "/" + maxAttempts + ")");
                }
            }
        }
        
        throw new Exception("Fuseki quadstore is not available after " + maxAttempts + " attempts. " +
                "Please check Docker logs: docker logs quad-store-server", lastException);
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

        // Determine Fuseki URL from MMS URL (default: replace 8080 with 3030)
        // Use /ds/data without ?default to load all named graphs from TriG
        String fusekiUrl = mmsUrl.replace(":8080", ":3030").replaceFirst("http://([^/]+).*", "http://$1/ds/data");
        
        // Verify Fuseki is available before attempting to load cluster config
        verifyFusekiAvailable(fusekiUrl);

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
