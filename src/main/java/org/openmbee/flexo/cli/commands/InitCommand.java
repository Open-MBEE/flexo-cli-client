package org.openmbee.flexo.cli.commands;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.openmbee.flexo.cli.FlexoCLI;
import org.openmbee.flexo.cli.client.AuthenticationHandler;
import org.openmbee.flexo.cli.client.FlexoMmsClient;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.openmbee.flexo.cli.util.ConsoleUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(InitCommand.class);

    private static final String DOCKER = "docker";
    private static final String DOCKER_COMPOSE_FILE = "flexo-mms-docker-compose.yml";
    private static final String FUSEKI = "Fuseki";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_TRIG = "application/trig";
    private static final String CONTENT_TYPE_TURTLE = "text/turtle";
    private static final String MSG_WAITING_FOR = "  Waiting for ";

    private void waitForService(String name, int port, String logMessage, String errorMessage) throws InterruptedException, DockerException {
        int maxAttempts = 30;
        int attempt = 0;
        ConsoleUtil.info(MSG_WAITING_FOR + logMessage + "...");
        while (attempt < maxAttempts) {
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress("localhost", port), 1000);
                ConsoleUtil.success("  " + name + " is ready");
                return;
            } catch (java.net.ConnectException | java.net.SocketTimeoutException e) {
                attempt++;
                if (attempt >= maxAttempts) {
                    throw new DockerException(errorMessage);
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ie;
                }
                if (parent.isVerbose()) {
                    ConsoleUtil.debug(MSG_WAITING_FOR + name + "... (attempt " + attempt + "/" + maxAttempts + ")");
                }
            } catch (IOException e) {
                attempt++;
                if (attempt >= maxAttempts) {
                    throw new DockerException(errorMessage, e);
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ie;
                }
                if (parent.isVerbose()) {
                    ConsoleUtil.debug(MSG_WAITING_FOR + name + "... (attempt " + attempt + "/" + maxAttempts + ")");
                }
            }
        }
    }

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
            if (!skipDocker) {
                startFuseki();
                loadClusterConfig(config.getMmsUrl());
                startLayer1Service(config.getMmsUrl());
            }

            AuthenticationHandler authHandler = new AuthenticationHandler(
                    config.isAuthEnabled(),
                    config.getSshKeyPath(),
                    config.isLocalMode(),
                    config.getLocalUser(),
                    config.getLocalJwtSecret()
            );

            try (FlexoMmsClient client = new FlexoMmsClient(config.getMmsUrl(), authHandler)) {
                if (skipDocker) {
                    generateAndLoadClusterConfig(client, config.getMmsUrl());
                }

                createOrg(client, orgId);
                createRepo(client, orgId, repoId);

                ConsoleUtil.success("Initialization complete!");
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

        } catch (DockerException | ConfigurationException | ServiceException e) {
            ConsoleUtil.error("Initialization failed: " + e.getMessage());
            if (parent.isVerbose()) {
                logger.error("Initialization failed", e);
            }
            throw new CommandExecutionException("Initialization failed: " + e.getMessage(), e, e.getExitCode());
        } catch (Exception e) {
            ConsoleUtil.error("Initialization failed: " + e.getMessage());
            if (parent.isVerbose()) {
                logger.error("Initialization failed", e);
            }
            throw new CommandExecutionException("Initialization failed: " + e.getMessage(), e, 1);
        }
    }

    private void startFuseki() throws ConfigurationException, DockerException, InterruptedException, IOException {
        ConsoleUtil.info("Starting Fuseki (quad-store-server)...");

        java.io.File composeFile = extractDockerComposeFromClasspath();
        if (composeFile == null) {
            throw new ConfigurationException("DOCKER_COMPOSE_FILE not found in classpath. " +
                    "Please ensure the application is properly packaged.");
        }

        ConsoleUtil.info("  Using docker-compose file: " + composeFile.getAbsolutePath());

        if (!isDockerAvailable()) {
            throw new DockerException(DOCKER.substring(0, 1).toUpperCase() + DOCKER.substring(1) + " is not available. Please install Docker and ensure it's running.");
        }

        boolean success = runDockerComposeService(composeFile, "quad-store-server");

        if (!success) {
            throw new DockerException("Failed to start " + FUSEKI + ". Please check Docker logs:\n" +
                    "  " + DOCKER + " logs quad-store-server");
        }

        ConsoleUtil.success("  " + FUSEKI + " started");
        ConsoleUtil.info(MSG_WAITING_FOR + FUSEKI + " to be ready...");

        waitForFuseki();
    }

    private void startLayer1Service(String mmsUrl) throws ConfigurationException, DockerException, InterruptedException, IOException {
        ConsoleUtil.info("Starting layer1-service...");

        java.io.File composeFile = extractDockerComposeFromClasspath();
        if (composeFile == null) {
            throw new ConfigurationException("DOCKER_COMPOSE_FILE not found in classpath. " +
                    "Please ensure the application is properly packaged.");
        }

        boolean success = runDockerComposeService(composeFile, "layer1-service");

        if (!success) {
            throw new DockerException("Failed to start layer1-service. Please check Docker logs:\n" +
                    "  " + DOCKER + " logs layer1-service");
        }

        ConsoleUtil.success("  layer1-service started");
        ConsoleUtil.info(MSG_WAITING_FOR + "layer1-service to be ready...");

        waitForLayer1Service();

        ConsoleUtil.info("  Verifying layer1-service health...");
        waitForLayer1ServiceHealth(mmsUrl);
    }

    private void waitForLayer1ServiceHealth(String mmsUrl) throws InterruptedException {
        String healthUrl = mmsUrl + "/";
        int maxAttempts = 15;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                java.net.URL url = new java.net.URL(healthUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.getResponseCode();
                ConsoleUtil.success("  layer1-service is ready");
                return;
            } catch (Exception e) {
                // Ignore exceptions, retry
            }

            attempt++;
            if (attempt < maxAttempts) {
                Thread.sleep(2000);
                if (parent.isVerbose()) {
                    ConsoleUtil.debug(MSG_WAITING_FOR + "layer1-service... (attempt " + attempt + "/" + maxAttempts + ")");
                }
            }
        }

        ConsoleUtil.warn("  layer1-service check timed out, proceeding anyway...");
    }

    private boolean runDockerCompose(java.io.File composeFile) throws IOException, InterruptedException {
        String[][] commandVariants = new String[][] {
            new String[] { DOCKER, "compose" },
            new String[] { "docker-compose" }
        };

        for (String[] cmdParts : commandVariants) {
            ProcessBuilder pb = new ProcessBuilder(cmdParts);
            pb.command().add("-f");
            pb.command().add(composeFile.getAbsolutePath());
            pb.command().add("up");
            pb.command().add("-d");
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

    private java.io.File extractDockerComposeFromClasspath() throws ConfigurationException, IOException {
        // Load docker-compose file from classpath
        java.io.InputStream resourceStream = getClass().getClassLoader()
                .getResourceAsStream(DOCKER_COMPOSE_FILE);
        
        if (resourceStream == null) {
            return null;
        }

        // Create temporary file with secure permissions in system temp directory
        // Security considerations:
        // 1. Files.createTempFile() creates files with unique, unpredictable names to prevent
        //    symlink attacks and race conditions in the publicly writable temp directory
        // 2. On Unix/Linux/macOS: Explicitly set file permissions to rw------- (0600) at creation time
        //    to prevent other users from reading the docker-compose configuration
        // 3. On Windows: Set restrictive ACLs after creation to limit access to owner only
        // 4. File is marked for deletion on JVM exit to avoid leaving sensitive data in temp directory
        java.io.File tempFile;
        if (System.getProperty("os.name").toLowerCase().contains("unix") || 
            System.getProperty("os.name").toLowerCase().contains("linux") ||
            System.getProperty("os.name").toLowerCase().contains("mac")) {
            // On Unix-like systems, create file with restrictive permissions (owner read/write only)
            // This prevents other users from reading the file in the shared temp directory
            tempFile = java.nio.file.Files.createTempFile(
                "flexo-mms-docker-compose-",
                ".yml",
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")
                )
            ).toFile();
        } else {
            // On Windows and other systems, create file then set restrictive permissions
            tempFile = java.nio.file.Files.createTempFile(
                "flexo-mms-docker-compose-",
                ".yml"
            ).toFile();
            // Set file to be readable/writable only by owner, remove all other permissions
            tempFile.setReadable(false, false);  // Remove read for others
            tempFile.setReadable(true, true);    // Add read for owner
            tempFile.setWritable(false, false);  // Remove write for others
            tempFile.setWritable(true, true);    // Add write for owner
            tempFile.setExecutable(false, false); // Remove execute for all
        }
        tempFile.deleteOnExit(); // Clean up on JVM exit to avoid leaving sensitive data

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
            ProcessBuilder pb = new ProcessBuilder(DOCKER, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void waitForServices() throws InterruptedException, DockerException {
        waitForService(FUSEKI, 3030, FUSEKI + " (quad-store-server)", 
            FUSEKI + " did not become ready within timeout. Please check Docker logs:\n  " + DOCKER + " logs quad-store-server");
        waitForService("layer1-service", 8080, "layer1-service", 
            "layer1-service did not become ready within timeout. Please check Docker logs:\n  " + DOCKER + " logs layer1-service");
    }

    private void waitForFuseki() throws InterruptedException, DockerException {
        waitForService(FUSEKI, 3030, FUSEKI, 
            FUSEKI + " did not become ready within timeout. Please check Docker logs:\n  " + DOCKER + " logs quad-store-server");
    }

    private void waitForLayer1Service() throws InterruptedException, DockerException {
        waitForService("layer1-service", 8080, "layer1-service", 
            "layer1-service did not become ready within timeout. Please check Docker logs:\n  " + DOCKER + " logs layer1-service");
    }

    private boolean runDockerComposeService(java.io.File composeFile, String serviceName) throws IOException, InterruptedException {
        String[][] commandVariants = new String[][] {
            new String[] { DOCKER, "compose" },
            new String[] { "docker-compose" }
        };

        for (String[] cmdParts : commandVariants) {
            ProcessBuilder pb = new ProcessBuilder(cmdParts);
            pb.command().add("-f");
            pb.command().add(composeFile.getAbsolutePath());
            pb.command().add("up");
            pb.command().add("-d");
            pb.command().add(serviceName);
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

    private void loadClusterConfig(String mmsUrl) throws ConfigurationException, ServiceException, IOException, InterruptedException {
        ConsoleUtil.info("Loading cluster configuration into Fuseki...");

        // First verify Fuseki is available before attempting to load cluster config
        String fusekiUrl = mmsUrl.replace(":8080", ":3030").replaceFirst("http://([^/]+).*", "http://$1/ds/data");
        verifyFusekiAvailable(fusekiUrl);

        java.io.InputStream resourceStream = getClass().getClassLoader()
                .getResourceAsStream("cluster.trig");
        if (resourceStream == null) {
            throw new ConfigurationException("cluster.trig not found in classpath");
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
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                lastException = e;
                attempt++;
                if (parent.isVerbose()) {
                    ConsoleUtil.debug("  Attempt " + attempt + " failed: " + e.getMessage());
                }
            }
        }

        throw new ServiceException("Failed to load cluster config into Fuseki after " + maxAttempts + " attempts", lastException);
    }

    private void loadTrigToFuseki(String fusekiUrl, String trigContent) throws ServiceException, IOException {
        java.net.URL url = new java.net.URL(fusekiUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty(HEADER_CONTENT_TYPE, CONTENT_TYPE_TRIG);
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
            throw new ServiceException("HTTP " + statusCode + " - " + body, statusCode);
        }
    }

    private void waitForFusekiIndex() throws InterruptedException, ServiceException {
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
                // Ignore exceptions, retry
            }

            attempt++;
            if (attempt < maxAttempts) {
                Thread.sleep(1000);
            }
        }

        ConsoleUtil.warn("  Could not verify Fuseki index status, proceeding anyway...");
    }

    private void verifyFusekiAvailable(String fusekiUrl) throws ServiceException, InterruptedException {
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
                    ConsoleUtil.debug(MSG_WAITING_FOR + FUSEKI + " quadstore... (attempt " + attempt + "/" + maxAttempts + ")");
                }
            }
        }
        
        throw new ServiceException(FUSEKI + " quadstore is not available after " + maxAttempts + " attempts. " +
                "Please check Docker logs: " + DOCKER + " logs quad-store-server", lastException);
    }

    private void createOrg(FlexoMmsClient client, String orgId) throws ServiceException, IOException, ResourceAlreadyExistsException {
        ConsoleUtil.info("Creating organization '" + orgId + "'...");

        // Send empty RDF - the server will fill in the required properties
        String orgRdf = "";

        HttpPut request = new HttpPut(client.getBaseUrl() + "/orgs/" + orgId);
        request.setHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_TURTLE);
        request.setEntity(new StringEntity(orgRdf, ContentType.parse(CONTENT_TYPE_TURTLE)));

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
                    throw new ResourceAlreadyExistsException("Organization", orgId, "Use --force to override.");
                }
            } else {
                throw e;
            }
        }
    }

    private void createRepo(FlexoMmsClient client, String orgId, String repoId) throws ServiceException, IOException, ResourceAlreadyExistsException {
        ConsoleUtil.info("Creating repository '" + repoId + "'...");

        // Send empty RDF - the server will fill in the required properties
        String repoRdf = "";

        HttpPut request = new HttpPut(client.getBaseUrl() + "/orgs/" + orgId + "/repos/" + repoId);
        request.setHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_TURTLE);
        request.setEntity(new StringEntity(repoRdf, ContentType.parse(CONTENT_TYPE_TURTLE)));

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
                    throw new ResourceAlreadyExistsException("Repository", repoId, "Use --force to override.");
                }
            } else {
                throw e;
            }
        }
    }

    private void createInitialBranch(FlexoMmsClient client, String orgId, String repoId, String branchId) throws ServiceException, IOException, ResourceAlreadyExistsException {
        ConsoleUtil.info("Creating initial branch '" + branchId + "'...");

        // First, create an empty model commit on the branch
        String branchUrl = client.getBaseUrl() + "/orgs/" + orgId + "/repos/" + repoId + "/branches/" + branchId;
        String graphUrl = branchUrl + "/graph";

        // Create empty RDF model
        String emptyModel = "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n";

        // PUT empty model to create initial commit
        HttpPut graphRequest = new HttpPut(graphUrl);
        graphRequest.setHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_TURTLE);
        graphRequest.setHeader("X-Commit-Message", "Initial commit");
        graphRequest.setEntity(new StringEntity(emptyModel, ContentType.parse(CONTENT_TYPE_TURTLE)));

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
                    throw new ResourceAlreadyExistsException("Branch", branchId, "Use --force to override.");
                }
            } else {
                throw e;
            }
        }
    }

    private void createBranchWithCommit(FlexoMmsClient client, String orgId, String repoId, String branchId) throws ServiceException, IOException {
        // Create a self-referencing commit first by PUTting an empty graph
        // This creates both the branch and an initial commit atomically
        String graphUrl = client.getBaseUrl() + "/orgs/" + orgId + "/repos/" + repoId + "/branches/" + branchId + "/graph";

        String emptyModel = "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n";

        HttpPut request = new HttpPut(graphUrl);
        request.setHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_TURTLE);
        request.setHeader("X-Commit-Message", "Initial commit");
        request.setEntity(new StringEntity(emptyModel, ContentType.parse(CONTENT_TYPE_TURTLE)));

        String response = client.executeRequest(request);
        ConsoleUtil.success("  Branch '" + branchId + "' created with initial commit");
        if (parent.isVerbose()) {
            ConsoleUtil.debug("Response: " + response);
        }
    }

    private void createBranch(FlexoMmsClient client, String orgId, String repoId, String branchId) throws ServiceException, IOException, ResourceAlreadyExistsException {
        ConsoleUtil.info("Creating branch '" + branchId + "'...");

        // Send empty RDF - the server will fill in the required properties
        String branchRdf = "";

        HttpPut request = new HttpPut(client.getBaseUrl() + "/orgs/" + orgId + "/repos/" + repoId + "/branches/" + branchId);
        request.setHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_TURTLE);
        request.setHeader("If-None-Match", "*"); // Create only if doesn't exist
        request.setEntity(new StringEntity(branchRdf, ContentType.parse(CONTENT_TYPE_TURTLE)));

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
                    throw new ResourceAlreadyExistsException("Branch", branchId, "Use --force to override.");
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

    private void createBranchAlternative(FlexoMmsClient client, String orgId, String repoId, String branchId) throws ServiceException, IOException {
        // Send empty RDF - the server will fill in the required properties
        String branchRdf = "";

        HttpPut request = new HttpPut(client.getBaseUrl() + "/orgs/" + orgId + "/repos/" + repoId + "/branches/" + branchId);
        request.setHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_TURTLE);
        request.setEntity(new StringEntity(branchRdf, ContentType.parse(CONTENT_TYPE_TURTLE)));

        String response = client.executeRequest(request);
        ConsoleUtil.success("  Branch created (alternative method)");
        if (parent.isVerbose()) {
            ConsoleUtil.debug("Response: " + response);
        }
    }

    private void generateAndLoadClusterConfig(FlexoMmsClient client, String mmsUrl) throws ConfigurationException, ServiceException, IOException, InterruptedException, org.apache.hc.core5.http.ParseException {
        ConsoleUtil.info("Loading cluster configuration...");

        // Determine Fuseki URL from MMS URL (default: replace 8080 with 3030)
        // Use /ds/data without ?default to load all named graphs from TriG
        String fusekiUrl = mmsUrl.replace(":8080", ":3030").replaceFirst("http://([^/]+).*", "http://$1/ds/data");
        
        // Verify Fuseki is available before attempting to load cluster config
        verifyFusekiAvailable(fusekiUrl);

        java.io.InputStream resourceStream = getClass().getClassLoader()
                .getResourceAsStream("cluster.trig");
        if (resourceStream == null) {
            throw new ConfigurationException("cluster.trig not found in classpath");
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
        post.setHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_TRIG);
        post.setEntity(new StringEntity(trigContent.toString(), ContentType.parse(CONTENT_TYPE_TRIG)));

        try (org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient =
                org.apache.hc.client5.http.impl.classic.HttpClients.createDefault()) {
            try (org.apache.hc.client5.http.impl.classic.CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getCode();
                if (statusCode < 200 || statusCode >= 300) {
                    String body = response.getEntity() != null ?
                            org.apache.hc.core5.http.io.entity.EntityUtils.toString(response.getEntity()) : "";
                    throw new ServiceException("Failed to load cluster config into Fuseki: HTTP " + statusCode + " - " + body, statusCode);
                }
            }
        }

        ConsoleUtil.success("  Cluster configuration loaded into Fuseki");
    }

    private void updateConfigDefaults(FlexoConfig config, String orgId, String repoId) throws IOException {
        ConsoleUtil.info("Updating configuration file...");

        config.set("default.org", orgId);
        config.set("default.repo", repoId);

        config.save();

        ConsoleUtil.success("  Configuration saved");
    }
}
