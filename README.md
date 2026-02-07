# Flexo CLI Client

A git-style command-line interface for interacting with Flexo MMS Layer 1 Service.

## Features

- **Automated initialization**: One-command setup of local MMS instances
- **Git-style commands**: `init`, `push`, `pull`, `branch`, `merge`, `rm`, `remote`
- **Multiple remote origins**: Manage and work with multiple MMS servers
- **RDF support**: Works with Turtle, JSON-LD, RDF/XML, N-Triples formats
- **Local mode authentication**: Automatic authentication for local development
- **SSH key authentication**: Supports SSH key-based JWT authentication for production
- **Configuration management**: Simple configuration via `~/.flexo/config`
- **Local development**: Easy setup with docker-compose

## Quick Start

```bash
# 1. Build the CLI
./gradlew installDist

# 2. Initialize (automatically starts Docker services and sets up everything)
./build/install/flexo/bin/flexo init

# This command will:
#   - Start Docker services (Fuseki + MMS Layer 1)
#   - Generate and load cluster configuration
#   - Create organization: "localorg"
#   - Create repository: "localrepo"
#   - Create branch: "master"
#   - Configure defaults in ~/.flexo/config

# 3. Start using the CLI
flexo branch --list
flexo pull master --output model.ttl
```

**Note**: The `init` command requires Docker to be installed and running.

## Prerequisites

- Java 17 or later
- Gradle (included via wrapper)
- Docker and Docker Compose (for local MMS service)

## Building

Build the project using Gradle:

```bash
./gradlew build
```

Create distribution:

```bash
./gradlew installDist
```

The CLI will be available at `build/install/flexo/bin/flexo`

Alternatively, create a fat JAR:

```bash
./gradlew fatJar
```

The JAR will be at `build/libs/flexo-cli-client-0.1.0-all.jar`

## Installation

### Option 1: Add to PATH

```bash
# After running installDist
export PATH="$PATH:$(pwd)/build/install/flexo/bin"

# Or create a symlink
sudo ln -s $(pwd)/build/install/flexo/bin/flexo /usr/local/bin/flexo
```

### Option 2: Use the JAR directly

```bash
alias flexo='java -jar /path/to/flexo-cli-client-0.1.0-all.jar'
```

## Configuration

Create a configuration file at `~/.flexo/config`:

```properties
# MMS Layer 1 Service URL
mms.url=http://localhost:8080

# Authentication settings
auth.enabled=false
auth.sshKeyPath=~/.ssh/id_rsa

# Local development mode - uses hardcoded "root" user
# Enable this for local testing with docker-compose setup
# NOTE: JWT secrets are auto-generated on first use
local.mode=true
local.user=root

# Default context (matches what 'flexo init' creates)
default.org=localorg
default.repo=localrepo
default.branch=master

# RDF format (turtle, jsonld, rdfxml, ntriples)
rdf.format=turtle

# Logging level
logging.level=INFO
```

You can also override configuration with environment variables:

```bash
export FLEXO_MMS_URL=http://localhost:8080
export FLEXO_DEFAULT_ORG=localorg
export FLEXO_DEFAULT_REPO=localrepo
```

## Local Development Setup

The `flexo init` command automatically handles the complete setup process.

### Prerequisites

- Docker and Docker Compose installed and running
- Java 17 or later
- Node.js and `ts-node` (for cluster configuration generation)

### One-Command Setup

```bash
# From the flexo-cli-client directory
./gradlew installDist
./build/install/flexo/bin/flexo init
```

This single command will:
1. **Start Docker services**: Launches Fuseki (port 3030) and Flexo MMS Layer 1 (port 8080)
2. **Generate cluster configuration**: Creates users (`root`, `admin`, `anon`) and access policies
3. **Load configuration into Fuseki**: Sets up the triplestore with user permissions
4. **Create organization**: `localorg`
5. **Create repository**: `localrepo`
6. **Create initial branch**: `master`
7. **Update ~/.flexo/config**: Sets defaults for convenient CLI usage

After initialization completes, you're ready to use the CLI immediately!

## Usage

### Two Ways to Use Flexo CLI

**Option 1: Using Config Defaults (Recommended)**

Set up your defaults once:
```bash
echo "default.org=localorg" >> ~/.flexo/config
echo "default.repo=localrepo" >> ~/.flexo/config
echo "default.branch=master" >> ~/.flexo/config
```

Then use short commands:
```bash
flexo branch --list
flexo pull master --output model.ttl
flexo push master --message "Update" --input model.ttl
```

**Option 2: Explicit Context**

Specify org/repo for each command:
```bash
flexo --org myorg --repo myrepo branch --list
flexo --org myorg --repo myrepo pull master --output model.ttl
flexo --org myorg --repo myrepo push master --message "Update" --input model.ttl
```

💡 **Tip**: Use config defaults for day-to-day work, explicit context when working with multiple projects.

### Init Command

Initialize a local Flexo MMS instance with automatic Docker service startup. This command automates the complete setup process for local development.

**What it does:**
1. Starts Docker services (Fuseki triplestore and MMS Layer 1 service)
2. Generates cluster configuration with default users (`root`, `admin`, `anon`)
3. Loads access control policies into Fuseki triplestore
4. Creates default organization: `localorg`
5. Creates default repository: `localrepo`
6. Automatically creates the `master` branch
7. Updates ~/.flexo/config with sensible defaults

```bash
# Initialize with defaults (org: localorg, repo: localrepo)
flexo init

# Initialize with custom org/repo (for your own projects)
flexo --org myorg --repo myrepo init

# Force re-initialization if resources already exist
flexo init --force

# Skip Docker startup if services are already running
flexo init --skip-docker
```

After initialization, you can immediately use the CLI:

```bash
# List branches (using defaults from init)
flexo branch --list

# Or explicitly specify org/repo
flexo --org localorg --repo localrepo branch --list

# Defaults are already set in ~/.flexo/config, so you can omit flags
flexo branch --list
flexo pull master --output model.ttl
```

**Prerequisites:**
- Docker and Docker Compose must be installed and running
- The `flexo-mms-layer1-service/deploy` directory must be accessible (for generating cluster.trig)
- Node.js and `ts-node` must be available for cluster generation

**Troubleshooting:**
- If Docker services fail to start, check `docker ps` and logs: `docker logs layer1-service`
- If you already have services running, use `--skip-docker` flag
- To stop services: `docker-compose down` (the CLI manages the compose file automatically)

### Global Options

```bash
flexo [OPTIONS] COMMAND [ARGS...]

Options:
  --org <org-id>        Organization ID
  --repo <repo-id>      Repository ID
  --remote <name>       Remote name (default: origin)
  -v, --verbose         Verbose output
  --no-color            Disable colored output
  -c, --config <file>   Configuration file path
  -h, --help            Show help message
  --version             Show version
```

### Remote Command

Manage multiple remote MMS instances, similar to git remotes.

```bash
# List all configured remotes
flexo remote
flexo remote list

# Add a new remote
flexo remote add <name> <url>

# Add a remote with local mode enabled
flexo remote add local http://localhost:8080 --local-mode true --local-user root

# Add a remote with SSH authentication
flexo remote add production https://mms.example.com --ssh-key ~/.ssh/id_rsa

# Set as default remote
flexo remote add staging https://staging.example.com --set-default

# Show remote details
flexo remote show <name>

# Update remote URL
flexo remote set-url <name> <new-url>

# Rename a remote
flexo remote rename <old-name> <new-name>

# Remove a remote
flexo remote remove <name>
```

**Remote Configuration:**

Remotes are stored in `~/.flexo/config` with the following format:

```properties
# Default remote
default.remote=origin

# Remote: origin
remote.origin.url=http://localhost:8080
remote.origin.localMode=true
remote.origin.localUser=root
remote.origin.localJwtSecret=dev-secret

# Remote: production
remote.production.url=https://mms.example.com
remote.production.authEnabled=true
remote.production.sshKeyPath=~/.ssh/id_rsa
remote.production.localMode=false
```

### Branch Command

List, create, or manage branches.

```bash
# List all branches (uses defaults from config)
flexo branch --list

# Create a new branch
flexo branch --create feature-xyz

# Create a branch from specific commit
flexo branch --create feature-xyz --from <commit-id>

# With explicit context (when not using config defaults)
flexo --org myorg --repo myrepo branch --list
```

### Pull Command

Fetch model from a branch.

```bash
# Pull from default branch to stdout
flexo pull

# Pull from specific branch
flexo pull --branch master

# Pull to file
flexo pull --branch master --output model.ttl

# Pull from a specific remote
flexo --remote production pull master

# Pull in JSON-LD format
flexo pull --format jsonld --output model.jsonld

# Alternative syntax
flexo pull master
```

### Push Command

Commit model changes to a branch.

```bash
# Push from file
flexo push --message "Add new elements" --input model.ttl

# Push from stdin
cat model.ttl | flexo push --message "Update model"

# Push to specific branch
flexo push --branch feature --message "New feature" --input changes.ttl

# Push to a specific remote
flexo --remote staging push master --message "Deploy to staging" --input model.ttl

# Push in different format
flexo push --format jsonld --message "Update" --input model.jsonld

# Alternative syntax
flexo push master --message "Update master" --input model.ttl
```

### Rm Command

Remove elements from the model (planned feature).

```bash
# Remove by IRI
flexo rm --iri http://example.org/element/123

# Remove using SPARQL pattern
flexo rm --pattern "DELETE WHERE { ?s ?p ?o }"

# Remove from file
flexo rm --file elements-to-remove.txt
```

*Note: This command is not yet fully implemented and requires SPARQL UPDATE support in the MMS API.*

### Merge Command

Merge changes between branches.

```bash
# Merge feature into current/default branch
flexo merge --source feature

# Merge into specific target
flexo merge --source feature --target master

# Create diff without committing
flexo merge --source feature --no-commit

# Alternative syntax
flexo merge feature
```

## Examples

### Example 1: Working with multiple remotes

```bash
# Add local and production remotes
flexo remote add local http://localhost:8080 --local-mode true --set-default
flexo remote add production https://mms.example.com --ssh-key ~/.ssh/id_rsa

# Set default context for local development
echo "default.org=localorg" >> ~/.flexo/config
echo "default.repo=localrepo" >> ~/.flexo/config

# List remotes
flexo remote list

# Pull from local (default)
flexo pull master --output model.ttl

# Push to production
flexo --remote production push master --message "Deploy to prod" --input model.ttl

# Compare branches across remotes
flexo --remote local pull master --output local-master.ttl
flexo --remote production pull master --output prod-master.ttl
```

### Example 2: Create a new branch and push changes

```bash
# Using default org/repo from config (localorg/localrepo)
# Create a branch
flexo branch --create feature-xyz

# Push some RDF data
flexo push feature-xyz \
  --message "Initial commit" \
  --input my-model.ttl

# Or specify custom org/repo explicitly
flexo --org myorg --repo myrepo branch --create feature-xyz
flexo --org myorg --repo myrepo push feature-xyz \
  --message "Initial commit" \
  --input my-model.ttl
```

### Example 3: Pull, modify, and push

```bash
# Pull current state
flexo pull master --output current.ttl

# Edit current.ttl with your changes
# ... edit file ...

# Push changes back
flexo push master \
  --message "Updated model with new elements" \
  --input current.ttl
```

### Example 4: Merge feature branch

```bash
# Create diff between branches
flexo merge feature --target master --no-commit

# Review changes, then push merged result
# (manual merge workflow)
```

### Example 5: Complete workflow from scratch

```bash
# 1. Build and initialize (automatically starts Docker)
cd flexo-cli-client
./gradlew installDist
./build/install/flexo/bin/flexo init

# This automatically:
#   - Starts Docker services (Fuseki + MMS)
#   - Creates: Organization (localorg), Repository (localrepo), Branch (master)
#   - Configures ~/.flexo/config with defaults

# 2. Verify setup
flexo branch --list
# Output: Branch    Commit    ETag
#         master    ...       ...

# 3. Work with the CLI
flexo pull master --output model.ttl
# Edit model.ttl...
flexo push master --message "My changes" --input model.ttl
```

## RDF Formats

The CLI supports the following RDF formats:

- **turtle** (default): Turtle format
- **jsonld**: JSON-LD
- **rdfxml**: RDF/XML
- **ntriples**: N-Triples
- **nquads**: N-Quads
- **trig**: TriG

Specify format with `--format` option or set default in config.

## Plugin System

Flexo CLI supports a plugin system that allows extending the CLI with additional commands. Plugins are JAR files that implement the FlexoPlugin interface and are loaded dynamically at startup.

### Using Plugins

Plugins are automatically loaded from the `~/.flexo/plugins/` directory:

```bash
# Create plugins directory
mkdir -p ~/.flexo/plugins

# Copy plugin JAR to plugins directory
cp my-plugin.jar ~/.flexo/plugins/

# Verify plugin is loaded
flexo --help  # Plugin commands will appear in the list
```

### Available Plugins

#### SysML v2 Plugin

The SysML v2 plugin provides commands for interacting with SysML v2 API services.

**Installation:**
```bash
# From the flexo-cli-sysmlv2-plugin directory
cd ../flexo-cli-sysmlv2-plugin
./gradlew jar
cp build/libs/flexo-cli-sysmlv2-plugin-1.0.0.jar ~/.flexo/plugins/
```

**Usage:**
```bash
# List projects
flexo sysml project list

# Get project details
flexo sysml project get --project myproject

# List elements
flexo sysml element list --project myproject --commit abc123

# Query relationships
flexo sysml relationship list --project myproject --commit abc123 element-id
```

See the [SysML v2 Plugin README](../flexo-cli-sysmlv2-plugin/README.md) for complete documentation.

### Developing Plugins

You can create your own plugins to extend the Flexo CLI with custom commands. Plugins have full access to:
- Configuration management
- HTTP client with authentication
- Console utilities
- Parent command options (--org, --repo, --verbose, etc.)

See [README-PLUGINS.md](README-PLUGINS.md) for a complete plugin development guide, including:
- Plugin architecture and API
- Step-by-step creation guide
- Example code
- Best practices

## Authentication

### Local Development Mode (Default)

For local development with the docker-compose setup, the CLI uses **local mode** by default. This automatically authenticates as the hardcoded `root` user defined in the MMS cluster initialization.

**Local mode is enabled by default with these settings:**

```properties
local.mode=true
local.user=root
# JWT secrets are auto-generated and saved to ~/.flexo/config on first use
```

The local mode:
- Uses the hardcoded `root` user from `/home/han/IdeaProjects/Open-MBEE/flexo-mms-layer1-service/src/main/resources/cluster.trig`
- Generates HMAC-based JWT tokens automatically with secure random secrets
- Auto-generates JWT secrets (64 characters, base64-encoded) on first use
- Works with locally deployed flexo-mms-layer1-service without additional authentication setup
- **Should NEVER be used in production environments**

To disable local mode for production deployments, set:

```properties
local.mode=false
```

### SSH Key-Based Authentication (Production)

For production deployments, the CLI supports SSH key-based JWT authentication:

1. Generate an SSH key pair (if you don't have one):

```bash
ssh-keygen -t rsa -b 4096 -f ~/.ssh/flexo_rsa
```

2. Disable local mode and enable SSH authentication in config:

```properties
local.mode=false
auth.enabled=true
auth.sshKeyPath=~/.ssh/flexo_rsa
```

3. Register your public key with the MMS service (configuration depends on deployment)

## Troubleshooting

### Docker services not starting

```bash
# Check if Docker is running
docker --version
docker ps

# If init fails, services are managed automatically by the CLI
# You can check service status
docker ps

# Then run init with --skip-docker
flexo init --skip-docker

# Check service logs
docker logs layer1-service
docker logs quad-store-server
```

### Connection refused

```bash
# Check if services are running
docker ps

# Restart services if needed (managed by CLI)
docker restart layer1-service quad-store-server

# Check service logs
docker logs layer1-service
docker logs quad-store-server
```

### Authentication errors

For local development, ensure local mode is enabled:

```bash
# Enable local mode (should be default)
echo "local.mode=true" >> ~/.flexo/config
echo "local.user=root" >> ~/.flexo/config
# JWT secret is auto-generated on first use - no manual configuration needed
```

If you get 401/403 errors, ensure:
1. The cluster configuration has been loaded into Fuseki (`flexo init` does this)
2. Local mode is enabled in your config
3. Check the logs with `flexo -v` for authentication details

### RDF parsing errors

```bash
# Validate your RDF file
rapper -i turtle -o ntriples your-file.ttl

# Use verbose mode for debugging
flexo -v push --input your-file.ttl --message "test"
```

### Branch not found

```bash
# List available branches
flexo branch --list

# Make sure you're using the correct org and repo
# If using defaults, check your config:
cat ~/.flexo/config | grep default

# If no branches exist, you may need to re-initialize
flexo init
```

### Empty triplestore

If you get errors about missing users or policies:

```bash
# Clear and re-initialize
curl -X POST http://localhost:3030/ds/update --data "DELETE WHERE { GRAPH ?g { ?s ?p ?o } }"
flexo init
```

## Development

### Project Structure

```
flexo-cli-client/
├── src/main/java/org/openmbee/flexo/cli/
│   ├── FlexoCLI.java              # Main entry point
│   ├── commands/                  # Command implementations
│   ├── client/                    # HTTP client & auth
│   ├── config/                    # Configuration management
│   ├── model/                     # Data models
│   └── util/                      # Utilities
└── src/test/java/                 # Unit tests
```

### Running tests

```bash
./gradlew test
```

### Security scanning

Run OWASP Dependency Check to scan for vulnerabilities:

```bash
./gradlew dependencyCheckAnalyze
```

View the report at `build/reports/dependency-check-report.html`

### Building documentation

```bash
./gradlew javadoc
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

See the main project LICENSE file.

## Related Projects

- [flexo-mms-layer1-service](../flexo-mms-layer1-service/) - The backend MMS service
- [flexo-cli-sysmlv2-plugin](../flexo-cli-sysmlv2-plugin/) - SysML v2 plugin for Flexo CLI
- [flexo-mms-sysmlv2](../flexo-mms-sysmlv2/) - SysML v2 API service
- [Open-MBEE](https://github.com/Open-MBEE) - Model-Based Engineering Environment

## Support

For issues and questions:
- Create an issue on GitHub
- Check the [flexo-mms-layer1-service documentation](https://flexo-mms-deployment-guide.readthedocs.io/)
