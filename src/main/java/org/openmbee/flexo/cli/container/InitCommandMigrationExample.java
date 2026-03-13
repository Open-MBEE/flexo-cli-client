package org.openmbee.flexo.cli.commands;

/**
 * Example migration of InitCommand to use the new container runtime abstraction.
 * 
 * This demonstrates how to update the existing InitCommand.java to use the
 * ContainerServiceHelper instead of hardcoded Docker commands.
 * 
 * Key changes:
 * 1. Replace Docker-specific code with ContainerServiceHelper
 * 2. Remove hardcoded docker paths
 * 3. Let the factory auto-detect the best runtime
 * 4. Improve error messages to be runtime-agnostic
 */
public class InitCommandMigrationExample {
    
    /**
     * OLD CODE - Hardcoded Docker:
     * 
     * <pre>
     * private boolean isDockerAvailable() {
     *     try {
     *         ProcessBuilder pb = new ProcessBuilder("docker", "--version");
     *         pb.redirectErrorStream(true);
     *         Process process = pb.start();
     *         int exitCode = process.waitFor();
     *         return exitCode == 0;
     *     } catch (Exception e) {
     *         return false;
     *     }
     * }
     * </pre>
     * 
     * NEW CODE - Runtime abstraction:
     * 
     * <pre>
     * private boolean isContainerRuntimeAvailable() {
     *     return ContainerRuntimeFactory.isAnyRuntimeAvailable(parent.isVerbose());
     * }
     * </pre>
     */
    
    /**
     * OLD CODE - Hardcoded Docker Compose:
     * 
     * <pre>
     * private boolean runDockerCompose(File composeFile) throws Exception {
     *     String[] commands = new String[]{"docker compose", "docker-compose"};
     *     for (String command : commands) {
     *         String[] cmdParts = command.split(" ", -1);
     *         String[] args = cmdParts.length >= 2
     *             ? new String[]{cmdParts[0], cmdParts[1], "-f", composeFile.getAbsolutePath(), "up", "-d"}
     *             : new String[]{cmdParts[0], "-f", composeFile.getAbsolutePath(), "up", "-d"};
     *         ProcessBuilder pb = new ProcessBuilder(args);
     *         Process process = pb.start();
     *         int exitCode = process.waitFor();
     *         if (exitCode == 0) return true;
     *     }
     *     return false;
     * }
     * </pre>
     * 
     * NEW CODE - Runtime abstraction:
     * 
     * <pre>
     * private boolean runComposeUp(File composeFile) throws Exception {
     *     ContainerServiceHelper helper = new ContainerServiceHelper(
     *         FlexoCLI.getConfig(), 
     *         parent.isVerbose()
     *     );
     *     return helper.startService(composeFile, "layer1-service");
     * }
     * </pre>
     */
    
    /**
     * OLD CODE - Manual port waiting:
     * 
     * <pre>
     * private void waitForService(String name, int port) throws Exception {
     *     int maxAttempts = 30;
     *     int attempt = 0;
     *     while (attempt < maxAttempts) {
     *         try (Socket socket = new Socket()) {
     *             socket.connect(new InetSocketAddress("localhost", port), 1000);
     *             return;
     *         } catch (Exception e) {
     *             attempt++;
     *             if (attempt >= maxAttempts) throw new Exception("Timeout");
     *             Thread.sleep(2000);
     *         }
     *     }
     * }
     * </pre>
     * 
     * NEW CODE - Use helper:
     * 
     * <pre>
     * private void waitForService(String name, int port) throws Exception {
     *     ContainerServiceHelper helper = new ContainerServiceHelper(
     *         FlexoCLI.getConfig(),
     *         parent.isVerbose()
     *     );
     *     helper.waitForServicePort(name, port, 30, 2000);
     * }
     * </pre>
     */
    
    /**
     * COMPLETE EXAMPLE - Refactored startFuseki() method:
     */
    /*
    private void startFuseki() throws Exception {
        ConsoleUtil.info("Starting Fuseki (quad-store-server)...");
        
        // Create helper (auto-detects best runtime)
        ContainerServiceHelper helper = new ContainerServiceHelper(
            FlexoCLI.getConfig(),
            parent.isVerbose()
        );
        
        // Extract compose file
        File composeFile = helper.extractComposeFile(
            "flexo-mms-docker-compose.yml",
            "flexo-mms-docker-compose-"
        );
        
        ConsoleUtil.info("  Using compose file: " + composeFile.getAbsolutePath());
        ConsoleUtil.info("  Using container runtime: " + helper.getRuntimeInfo());
        
        // Start the service
        boolean success = helper.startService(composeFile, "quad-store-server");
        
        if (!success) {
            throw new Exception("Failed to start Fuseki. Please check logs:\n" +
                "  " + helper.getRuntime().getName() + " logs quad-store-server");
        }
        
        ConsoleUtil.success("  Fuseki started");
        
        // Wait for it to be ready
        helper.waitForServicePort("Fuseki", 3030, 30, 2000);
    }
    */
    
    /**
     * BENEFITS of the new approach:
     * 
     * 1. NO HARDCODED PATHS - Works with any OCI-compliant runtime
     * 2. AUTO-DETECTION - Automatically finds the best available runtime
     * 3. USER PREFERENCE - Respects container.runtime config
     * 4. BETTER ERRORS - Runtime-agnostic error messages
     * 5. LESS CODE - Helper handles all the complexity
     * 6. TESTABLE - Easy to mock for unit tests
     * 7. MAINTAINABLE - Container logic in one place
     * 8. EXTENSIBLE - Easy to add new runtimes
     */
    
    /**
     * MIGRATION STEPS:
     * 
     * For flexo-cli-client/src/main/java/org/openmbee/flexo/cli/commands/InitCommand.java:
     * 
     * 1. Add imports:
     *    import org.openmbee.flexo.cli.container.*;
     * 
     * 2. Replace isDockerAvailable():
     *    - Remove the hardcoded Docker check
     *    - Use: ContainerRuntimeFactory.isAnyRuntimeAvailable(verbose)
     * 
     * 3. Replace runDockerComposeService():
     *    - Create ContainerServiceHelper instance
     *    - Use: helper.startService(composeFile, serviceName)
     * 
     * 4. Update extractDockerComposeFromClasspath():
     *    - Keep the logic, or use: helper.extractComposeFile()
     * 
     * 5. Update error messages:
     *    - Replace "Docker" with runtime.getName()
     *    - Or use helper.getRuntimeInfo()
     * 
     * 6. Update waitForService():
     *    - Use: helper.waitForServicePort()
     * 
     * For flexo-cli-sysmlv2-plugin/src/main/java/.../InitCommand.java:
     * 
     * 1. Same steps as above
     * 2. Copy the container package to the plugin (or make it a shared library)
     * 3. Update imports to use the container abstraction
     */
}
