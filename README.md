# Flexo CLI Client

A git-style command-line interface for interacting with Flexo MMS Layer 1 Service.

## Features

- **Automated initialization**: One-command setup of local MMS instances
- **Git-style commands**: `init`, `push`, `pull`, `branch`, `merge`, `rm`
- **RDF support**: Works with Turtle, JSON-LD, RDF/XML, N-Triples formats
- **Local mode authentication**: Automatic authentication for local development
- **SSH key authentication**: Supports SSH key-based JWT authentication for production
- **Configuration management**: Simple configuration via `~/.flexo/config`
- **Local development**: Easy setup with docker-compose

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
local.mode=true
local.user=root
local.jwtSecret=dev-secret-please-change-in-production

# Default context
default.org=example-org
default.repo=example-repo
default.branch=master

# RDF format (turtle, jsonld, rdfxml, ntriples)
rdf.format=turtle

# Logging level
logging.level=INFO
```

You can also override configuration with environment variables:

```bash
export FLEXO_MMS_URL=http://localhost:8080
export FLEXO_DEFAULT_ORG=my-org
export FLEXO_DEFAULT_REPO=my-repo
```

## Local Development Setup

### 1. Start the MMS services

From the project root:

```bash
docker-compose -f docker-compose.local.yml up -d
```

This starts:
- **Fuseki** (quad-store) on port 3030
- **Flexo MMS Layer 1 Service** on port 8080

### 2. Initialize the MMS

Use the `init` command to set up everything automatically:

```bash
cd flexo-cli-client
./gradlew installDist
./build/install/flexo/bin/flexo init
```

This single command will:
- Generate cluster configuration (users, policies)
- Load it into Fuseki
- Create the default org and repo
- Set up the master branch

### 3. Configure the CLI (Optional)

```bash
mkdir -p ~/.flexo
cat > ~/.flexo/config << EOF
mms.url=http://localhost:8080
local.mode=true
local.user=root
local.jwtSecret=devsecretpleasechangeinproduction1234567890
default.org=localorg
default.repo=localrepo
default.branch=master
rdf.format=turtle
EOF
```

## Usage

### Init Command

Initialize a local Flexo MMS instance with a default organization and repository. This command automates the complete setup process for local development.

**What it does:**
1. Generates cluster configuration with default users (`root`, `admin`, `anon`)
2. Loads access control policies into Fuseki triplestore
3. Creates default organization: `localorg`
4. Creates default repository: `localrepo`
5. Automatically creates the `master` branch

```bash
# Initialize with defaults (org: localorg, repo: localrepo)
flexo init

# Initialize with custom org/repo
flexo --org myorg --repo myrepo init

# Force re-initialization if resources already exist
flexo init --force
```

After initialization, you can immediately use the CLI:

```bash
# List branches
flexo --org localorg --repo localrepo branch --list

# Set defaults in config for convenience
echo "default.org=localorg" >> ~/.flexo/config
echo "default.repo=localrepo" >> ~/.flexo/config

# Now you can omit --org and --repo
flexo branch --list
```

**Prerequisites:**
- Docker services must be running (see Local Development Setup)
- The `flexo-mms-layer1-service/deploy` directory must be accessible (for generating cluster.trig)
- Node.js and `ts-node` must be available for cluster generation

### Global Options

```bash
flexo [OPTIONS] COMMAND [ARGS...]

Options:
  --org <org-id>        Organization ID
  --repo <repo-id>      Repository ID
  -v, --verbose         Verbose output
  --no-color            Disable colored output
  -c, --config <file>   Configuration file path
  -h, --help            Show help message
  --version             Show version
```

### Branch Command

List, create, or manage branches.

```bash
# List all branches
flexo branch --list

# Create a new branch
flexo branch --create my-feature

# Create a branch from specific commit
flexo branch --create my-feature --from <commit-id>

# With context options
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

### Example 1: Create a new branch and push changes

```bash
# Create a branch
flexo --org myorg --repo myrepo branch --create feature-xyz

# Push some RDF data
flexo --org myorg --repo myrepo push feature-xyz \
  --message "Initial commit" \
  --input my-model.ttl
```

### Example 2: Pull, modify, and push

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

### Example 3: Merge feature branch

```bash
# Create diff between branches
flexo merge feature --target master --no-commit

# Review changes, then push merged result
# (manual merge workflow)
```

### Example 4: Complete workflow from scratch

```bash
# 1. Start Docker services
docker-compose -f docker-compose.local.yml up -d

# 2. Initialize MMS
cd flexo-cli-client
./gradlew installDist
./build/install/flexo/bin/flexo init

# 3. Configure defaults
echo "default.org=localorg" >> ~/.flexo/config
echo "default.repo=localrepo" >> ~/.flexo/config

# 4. Use the CLI
flexo branch --list
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
flexo sysml project get --project PROJECT_ID

# List elements
flexo sysml element list --project PROJECT_ID --commit COMMIT_ID

# Query relationships
flexo sysml relationship list --project PROJECT_ID --commit COMMIT_ID ELEMENT_ID
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
local.jwtSecret=dev-secret-please-change-in-production
```

The local mode:
- Uses the hardcoded `root` user from `/home/han/IdeaProjects/Open-MBEE/flexo-mms-layer1-service/src/main/resources/cluster.trig`
- Generates HMAC-based JWT tokens automatically
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

### Connection refused

```bash
# Check if services are running
docker ps

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
echo "local.jwtSecret=devsecretpleasechangeinproduction1234567890" >> ~/.flexo/config
```

If you get 401/403 errors, ensure:
1. The cluster configuration has been loaded into Fuseki (`flexo init` does this)
2. The JWT secret matches your docker-compose.local.yml configuration
3. Local mode is enabled in your config

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

# Make sure org and repo are correct
flexo --org yourorg --repo yourrepo branch --list

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
