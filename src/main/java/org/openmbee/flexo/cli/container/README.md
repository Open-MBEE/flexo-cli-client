# Container Runtime Abstraction

This package provides a generic abstraction layer for container runtime operations, supporting multiple container technologies like Docker, Podman, and Colima.

## Overview

The container runtime abstraction allows the Flexo CLI to work with different container technologies without requiring Docker specifically. This makes the tool more flexible and accessible to users who prefer alternative container runtimes.

## Supported Runtimes

1. **Docker** - The default container runtime (priority: 100)
   - Supports both `docker compose` (v2) and `docker-compose` (v1)
   - Automatically detected on Linux, macOS, and Windows

2. **Colima** - Lightweight Docker alternative for macOS (priority: 95)
   - Uses Docker CLI under the hood
   - Requires Colima to be running
   - Ideal for macOS users who want a lighter alternative to Docker Desktop

3. **Podman** - Daemonless container engine (priority: 90)
   - Supports both `podman compose` and `podman-compose`
   - Great for Linux users and security-conscious environments
   - Compatible with Docker Compose files

## Architecture

### Core Interfaces

- **`ContainerRuntime`** - Main interface for container operations
- **`AbstractContainerRuntime`** - Base implementation with common functionality
- **`ContainerRuntimeFactory`** - Factory for detecting and selecting runtimes
- **`ContainerServiceHelper`** - High-level helper for service initialization

### Runtime Selection

The factory automatically selects the best available runtime based on:

1. **User preference** (via `container.runtime` config)
2. **Availability** (what's installed and working)
3. **Priority** (Docker > Colima > Podman)

## Usage

### Basic Usage

```java
import org.openmbee.flexo.cli.container.*;

// Get the best available runtime
ContainerRuntime runtime = ContainerRuntimeFactory.getRuntime(config, verbose);

// Start services from a compose file
File composeFile = new File("docker-compose.yml");
boolean success = runtime.composeUp(composeFile, Arrays.asList("service-name"));

// Check if a container is running
boolean running = runtime.isContainerRunning("my-container");

// Get container logs
String logs = runtime.getContainerLogs("my-container", 50);
```

### Using the Helper Class

```java
import org.openmbee.flexo.cli.container.*;

// Create helper (automatically selects runtime)
ContainerServiceHelper helper = new ContainerServiceHelper(config, verbose);

// Extract compose file from classpath
File composeFile = helper.extractComposeFile("docker-compose.yml", "prefix-");

// Start a service
helper.startService(composeFile, "my-service");

// Wait for service to be ready
helper.waitForServicePort("my-service", 8080, 30, 2000);

// Check if running
boolean running = helper.isServiceRunning("my-service");
```

### Migrating Existing Code

**Before** (hardcoded Docker):
```java
ProcessBuilder pb = new ProcessBuilder("docker", "compose", "-f", composeFile, "up", "-d");
Process process = pb.start();
```

**After** (runtime-agnostic):
```java
ContainerRuntime runtime = ContainerRuntimeFactory.getRuntime(config, verbose);
runtime.composeUp(composeFile, Collections.emptyList());
```

## Configuration

Users can specify their preferred container runtime in `~/.flexo/config`:

```properties
# Prefer Podman over Docker
container.runtime=podman

# Or use Colima
container.runtime=colima

# Or explicitly use Docker
container.runtime=docker
```

If not specified, the system will auto-detect the best available runtime.

## Command-Line Options

The `init` command can work without Docker:

```bash
# Auto-detect best available runtime
flexo init

# Skip container startup (assumes services already running)
flexo init --skip-docker

# Same for SysML v2 plugin
flexo sysml init
flexo sysml init --skip-docker
```

## Benefits

### For Users

1. **No Docker requirement** - Use Podman, Colima, or other OCI-compliant runtimes
2. **Better platform support** - Works on systems where Docker is restricted
3. **Flexibility** - Choose the runtime that works best for your environment
4. **Automatic detection** - Just works without configuration

### For Developers

1. **Single abstraction** - One interface for all container operations
2. **Easy testing** - Mock the ContainerRuntime interface
3. **Extensible** - Add new runtimes by implementing the interface
4. **Maintainable** - Container logic centralized in one place

## Adding New Runtimes

To add support for a new container runtime:

1. Create a new class extending `AbstractContainerRuntime`
2. Implement the required methods:
   - `getName()` - Runtime name
   - `getExecutablePath()` - Path to executable
   - `getComposeCommand()` - Compose command array
   - `getPriority()` - Selection priority
3. Add to `ContainerRuntimeFactory.getRuntime()`

Example:

```java
public class MyRuntime extends AbstractContainerRuntime {
    
    public MyRuntime(boolean verbose) {
        super(verbose);
    }
    
    @Override
    public String getName() {
        return "myruntime";
    }
    
    @Override
    protected String getExecutablePath() {
        return "/usr/local/bin/myruntime";
    }
    
    @Override
    protected String[] getComposeCommand() {
        return new String[] { "/usr/local/bin/myruntime", "compose" };
    }
    
    @Override
    public int getPriority() {
        return 50;
    }
}
```

## Testing

The abstraction makes testing easier:

```java
// Mock the runtime for testing
ContainerRuntime mockRuntime = mock(ContainerRuntime.class);
when(mockRuntime.isAvailable()).thenReturn(true);
when(mockRuntime.composeUp(any(), any())).thenReturn(true);

ContainerServiceHelper helper = new ContainerServiceHelper(mockRuntime, false);
helper.startService(composeFile, "test-service");

verify(mockRuntime).composeUp(composeFile, Collections.singletonList("test-service"));
```

## Troubleshooting

### No container runtime detected

**Error**: `No container runtime detected. Please install...`

**Solution**: Install one of:
- Docker Desktop: https://www.docker.com/products/docker-desktop
- Podman: https://podman.io/
- Colima: https://github.com/abiosoft/colima

### Preferred runtime not available

**Warning**: `Preferred container runtime 'podman' not available, using auto-detection`

**Solution**: Either:
1. Install the preferred runtime
2. Remove the `container.runtime` config to use auto-detection
3. Update the config to specify a different runtime

### Service not starting

**Error**: `Failed to start service...`

**Solution**:
1. Check if the runtime is running (e.g., `docker info`, `podman info`)
2. Check logs: `docker logs service-name` or `podman logs service-name`
3. Try running the compose command manually
4. Ensure compose file is valid

## Future Enhancements

Potential improvements:

1. **Kubernetes support** - Add support for local k8s (kind, k3s, minikube)
2. **Nerdctl support** - containerd CLI compatible with Docker
3. **Remote runtimes** - Support for remote container hosts
4. **Health checks** - Built-in health check mechanisms
5. **Resource limits** - Configure CPU/memory limits
6. **Network configuration** - Advanced networking options

## See Also

- [Docker Documentation](https://docs.docker.com/)
- [Podman Documentation](https://docs.podman.io/)
- [Colima Documentation](https://github.com/abiosoft/colima)
- [OCI Specification](https://opencontainers.org/)
