# Flexo CLI Plugin Development Guide

## Overview

The Flexo CLI supports a plugin system that allows extending the CLI with additional commands. Plugins have full access to core CLI infrastructure including configuration, authentication, HTTP clients, and console utilities.

## Plugin Architecture

### Discovery and Loading

Plugins are discovered using Java's ServiceLoader mechanism:

1. Plugin JARs are placed in `~/.flexo/plugins/`
2. Each JAR contains `META-INF/services/org.openmbee.flexo.cli.plugin.FlexoPlugin`
3. The CLI loads plugins at startup and registers their commands

### Plugin Lifecycle

1. **Discovery**: PluginLoader scans `~/.flexo/plugins/` for JARs
2. **Loading**: ServiceLoader discovers FlexoPlugin implementations
3. **Initialization**: Each plugin's `initialize(PluginContext)` is called
4. **Registration**: Plugin commands are added as CLI subcommands
5. **Execution**: Plugin commands run with full access to PluginContext

## Core Plugin API

### FlexoPlugin Interface

```java
public interface FlexoPlugin {
    String getName();              // Command name (e.g., "sysml")
    String getDescription();       // Brief description for help text
    String getVersion();           // Plugin version
    void initialize(PluginContext context);  // Called once at startup
    CommandSpec getCommand();      // Returns picocli CommandSpec
}
```

### PluginContext

Provides plugins with access to core CLI resources:

```java
public class PluginContext {
    FlexoConfig getConfig();           // Configuration management
    FlexoMmsClient createClient();     // Authenticated HTTP client
    String getOrgId();                 // Org from --org or config
    String getRepoId();                // Repo from --repo or config
    boolean isVerbose();               // --verbose flag
    boolean isNoColor();               // --no-color flag
    String getMmsUrl();                // Configured MMS URL
}
```

### PluginCommand Base Class

Convenient base class for plugin commands:

```java
public abstract class PluginCommand implements Runnable {
    protected PluginContext context;

    protected FlexoMmsClient getClient();
    protected FlexoConfig getConfig();
    protected String getOrgId();
    protected String getRepoId();
    protected boolean isVerbose();

    protected void info(String message);
    protected void success(String message);
    protected void error(String message);
    protected void warn(String message);
    protected void debug(String message);
}
```

## Creating a Plugin

### 1. Project Structure

```
my-plugin/
├── build.gradle
├── src/main/
│   ├── java/com/example/myplugin/
│   │   ├── MyPlugin.java                  # FlexoPlugin implementation
│   │   ├── commands/
│   │   │   ├── MyCommand.java             # Base command
│   │   │   ├── SubCommand1.java           # Subcommand
│   │   │   └── SubCommand2.java           # Subcommand
│   │   └── client/
│   │       └── MyServiceClient.java       # HTTP client (optional)
│   └── resources/
│       └── META-INF/services/
│           └── org.openmbee.flexo.cli.plugin.FlexoPlugin
```

### 2. Build Configuration

**build.gradle**:
```gradle
plugins {
    id 'java'
}

group = 'com.example'
version = '1.0.0'

repositories {
    mavenCentral()
}

dependencies {
    // Core CLI (provided - not bundled)
    compileOnly files('/path/to/flexo-cli-client/build/libs/flexo-cli-client-0.1.0.jar')

    // Plugin dependencies
    implementation 'info.picocli:picocli:4.7.5'
    implementation 'org.apache.httpcomponents.client5:httpclient5:5.3'
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.0'
}

jar {
    archiveBaseName = 'flexo-my-plugin'

    // Include dependencies in JAR
    from {
        configurations.runtimeClasspath.collect {
            it.isDirectory() ? it : zipTree(it)
        }
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
```

### 3. Plugin Implementation

**MyPlugin.java**:
```java
package com.example.myplugin;

import org.openmbee.flexo.cli.plugin.*;
import picocli.CommandLine.Model.CommandSpec;

public class MyPlugin implements FlexoPlugin {
    private PluginContext context;

    @Override
    public String getName() {
        return "my";  // Command will be: flexo my
    }

    @Override
    public String getDescription() {
        return "My custom plugin";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;
    }

    @Override
    public CommandSpec getCommand() {
        MyCommand cmd = new MyCommand();
        cmd.setContext(context);
        return CommandSpec.forAnnotatedObject(cmd);
    }
}
```

**MyCommand.java**:
```java
package com.example.myplugin.commands;

import org.openmbee.flexo.cli.plugin.PluginCommand;
import picocli.CommandLine.Command;

@Command(
    name = "my",
    description = "My custom plugin commands",
    subcommands = {
        SubCommand1.class,
        SubCommand2.class
    }
)
public class MyCommand extends PluginCommand {
    @Override
    public void run() {
        info("Use 'flexo my <subcommand>' to execute plugin commands");
        info("Available subcommands: sub1, sub2");
    }
}
```

**SubCommand1.java**:
```java
package com.example.myplugin.commands;

import org.openmbee.flexo.cli.plugin.PluginCommand;
import org.openmbee.flexo.cli.client.FlexoMmsClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "sub1", description = "Example subcommand")
public class SubCommand1 extends PluginCommand {

    @Option(names = {"--param"}, description = "Example parameter")
    private String param = "default";

    @Override
    public void run() {
        try {
            debug("Executing sub1 command with param: " + param);

            // Access configuration
            String mmsUrl = getConfig().getMmsUrl();
            String orgId = getOrgId();
            String repoId = getRepoId();

            info("MMS URL: " + mmsUrl);
            info("Org: " + orgId);
            info("Repo: " + repoId);

            // Create authenticated client
            FlexoMmsClient client = getClient();

            // Make API calls...

            success("Command completed successfully!");

        } catch (Exception e) {
            error("Command failed: " + e.getMessage());
            if (isVerbose()) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
}
```

### 4. ServiceLoader Registration

Create: `src/main/resources/META-INF/services/org.openmbee.flexo.cli.plugin.FlexoPlugin`

```
com.example.myplugin.MyPlugin
```

### 5. Build and Install

```bash
# Build plugin JAR
./gradlew jar

# Install plugin
mkdir -p ~/.flexo/plugins
cp build/libs/flexo-my-plugin-1.0.0.jar ~/.flexo/plugins/

# Verify installation
flexo --help  # Should show 'my' command
flexo my --help
flexo my sub1 --param test
```

## Example: SysML v2 Plugin

For a complete example, see the SysML v2 plugin project structure:

```
flexo-cli-sysmlv2-plugin/
├── build.gradle
├── src/main/
│   ├── java/org/openmbee/flexo/sysmlv2/plugin/
│   │   ├── SysMLv2Plugin.java
│   │   ├── commands/
│   │   │   ├── SysMLCommand.java          # flexo sysml
│   │   │   ├── ProjectCommand.java        # flexo sysml project
│   │   │   │   ├── ProjectListCommand.java
│   │   │   │   ├── ProjectCreateCommand.java
│   │   │   │   ├── ProjectGetCommand.java
│   │   │   │   ├── ProjectUpdateCommand.java
│   │   │   │   └── ProjectDeleteCommand.java
│   │   │   ├── ElementCommand.java        # flexo sysml element
│   │   │   │   ├── ElementListCommand.java
│   │   │   │   ├── ElementGetCommand.java
│   │   │   │   └── ElementRootsCommand.java
│   │   │   ├── BranchCommand.java         # flexo sysml branch
│   │   │   ├── CommitCommand.java         # flexo sysml commit
│   │   │   ├── QueryCommand.java          # flexo sysml query
│   │   │   ├── TagCommand.java            # flexo sysml tag
│   │   │   └── RelationshipCommand.java   # flexo sysml relationship
│   │   └── client/
│   │       └── SysMLv2Client.java         # Wraps SysML v2 API
│   └── resources/
│       └── META-INF/services/
│           └── org.openmbee.flexo.cli.plugin.FlexoPlugin
```

The SysML v2 plugin would expose commands like:

```bash
# Project operations
flexo sysml project list
flexo sysml project create --name "My Project"
flexo sysml project get PROJECT_ID

# Element operations
flexo sysml element list --project PROJECT_ID --commit COMMIT_ID
flexo sysml element get PROJECT_ID COMMIT_ID ELEMENT_ID
flexo sysml element roots --project PROJECT_ID --commit COMMIT_ID

# Branch operations
flexo sysml branch list --project PROJECT_ID
flexo sysml branch create --project PROJECT_ID --name feature-x

# Query operations
flexo sysml query list --project PROJECT_ID
flexo sysml query create --project PROJECT_ID --query "..."
flexo sysml query results --project PROJECT_ID --query-id QUERY_ID

# All standard options work
flexo --verbose sysml project list
flexo --org myorg --repo myrepo sysml element list
```

## Best Practices

1. **Use PluginCommand Base Class**: Extends PluginCommand for convenient access to context
2. **Error Handling**: Always catch exceptions and provide clear error messages
3. **Verbose Output**: Use `debug()` for verbose output, respect `isVerbose()`
4. **Consistent Formatting**: Use `info()`, `success()`, `error()`, `warn()` for output
5. **Configuration**: Read settings from PluginContext, don't access files directly
6. **Authentication**: Use `createClient()` - authentication is handled automatically
7. **Resource Cleanup**: Close HTTP clients and other resources properly
8. **Testing**: Test plugin commands with unit and integration tests
9. **Documentation**: Provide clear help text and examples in command descriptions
10. **Versioning**: Use semantic versioning for plugin releases

## Debugging Plugins

Enable verbose logging:

```bash
flexo -v my sub1  # Shows plugin loading and debug messages
```

Check plugin directory:

```bash
ls -la ~/.flexo/plugins/
```

Plugin loading logs appear in console with `logger.info()` messages.

## FAQ

**Q: Can plugins access core CLI commands?**
A: No, plugins are isolated. They only have access to PluginContext resources.

**Q: Can plugins depend on other plugins?**
A: Not directly. Keep plugins independent.

**Q: How do I distribute my plugin?**
A: Distribute the JAR file. Users copy it to `~/.flexo/plugins/`.

**Q: Can I use Kotlin?**
A: Yes! The plugin API is language-agnostic. Kotlin, Groovy, Scala all work.

**Q: Do I need to rebuild the core CLI?**
A: No! Plugins are completely external. Just build your plugin JAR.

**Q: How do I update a plugin?**
A: Replace the JAR in `~/.flexo/plugins/` and restart the CLI.

**Q: Can plugins add global options?**
A: No. Plugins can only add subcommands. Global options are managed by core CLI.

## Support

For questions or issues with plugin development:
- Check this guide
- Review example plugins
- Create an issue on GitHub
- Consult the Flexo CLI source code

## Plugin API Reference

See source code for complete API documentation:
- `org.openmbee.flexo.cli.plugin.FlexoPlugin`
- `org.openmbee.flexo.cli.plugin.PluginContext`
- `org.openmbee.flexo.cli.plugin.PluginCommand`
- `org.openmbee.flexo.cli.plugin.PluginLoader`
