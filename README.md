# Flexo CLI Client

A git-style command-line interface for interacting with Flexo MMS Layer 1 Service.

## Features

- **Git-style commands**: `push`, `pull`, `branch`, `merge`, `rm`
- **RDF support**: Works with Turtle, JSON-LD, RDF/XML, N-Triples formats
- **SSH key authentication**: Supports SSH key-based JWT authentication (optional)
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

### 2. Initialize the cluster

Generate and load the initialization data:

```bash
# Generate cluster configuration
cd flexo-mms-layer1-service/deploy
npx ts-node src/main.ts http://layer1-service > ../src/test/resources/cluster.trig

# Load into Fuseki
curl -X POST http://localhost:3030/ds/data?default \
  -H "Content-Type: application/trig" \
  --data-binary @../src/test/resources/cluster.trig
```

### 3. Configure the CLI

```bash
mkdir -p ~/.flexo
cat > ~/.flexo/config << EOF
mms.url=http://localhost:8080
local.mode=true
local.user=root
local.jwtSecret=dev-secret-please-change-in-production
default.org=example
default.repo=example-repo
default.branch=master
rdf.format=turtle
EOF
```

## Usage

### Init Command

Initialize a local Flexo MMS instance with a default organization and repository. This is useful for quickly setting up a development environment.

```bash
# Initialize with defaults (org: localorg, repo: localrepo, branch: master)
flexo init

# Initialize with custom org/repo
flexo --org myorg --repo myrepo init

# Initialize with custom branch name
flexo init --branch main

# Force re-initialization if resources already exist
flexo init --force
```

After initialization, you can use the CLI immediately:

```bash
flexo --org localorg --repo localrepo branch --list
```

**Note:** There is a known issue with the MMS layer1 service (ConcurrentModificationException) that may cause initialization to fail. If this happens, the resources may have been partially created. You can use `--force` to retry, or create them manually using curl and the MMS API.

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

### Example 4: Using with different organizations

```bash
# Set defaults in config
echo "default.org=myorg" >> ~/.flexo/config
echo "default.repo=myrepo" >> ~/.flexo/config

# Now you can omit --org and --repo
flexo branch --list
flexo pull master
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

```bash
# Disable authentication for local development
echo "auth.enabled=false" >> ~/.flexo/config
```

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
- [Open-MBEE](https://github.com/Open-MBEE) - Model-Based Engineering Environment

## Support

For issues and questions:
- Create an issue on GitHub
- Check the [flexo-mms-layer1-service documentation](https://flexo-mms-deployment-guide.readthedocs.io/)
