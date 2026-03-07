# Flexo CLI Client Demo Procedure

This document provides a complete walkthrough of all flexo-cli-client features.

## Known Issues

- **JSON-LD/RDFXML format pull** (`DEMO.md:160,163,449-453`): The layer1-service does not properly honor the Accept header for different RDF formats. Pulling with `--format jsonld` or `--format rdfxml` will fail because the server always returns Turtle format.
- **Merge/diff operations** (`DEMO.md:248,390-394`): The merge command with `--no-commit` and subsequent diff creation fails with "Not implemented (formulae, graph literals)" due to a TriG parsing issue in the layer1-service.

## Prerequisites

```bash
# Build the CLI
cd /path/to/flexo-cli-client
./gradlew installDist

# Add to PATH (optional)
export PATH="$PATH:$(pwd)/build/install/flexo/bin"
```

---

## Phase 1: Initialization and Setup

### 1.1 Initialize Local MMS Instance

```bash
# Initialize with defaults (org: localorg, repo: localrepo)
./build/install/flexo/bin/flexo init

# Or specify custom org/repo
./build/install/flexo/bin/flexo --org myorg --repo myrepo init

# Force re-initialization if resources exist
./build/install/flexo/bin/flexo init --force

# Skip Docker if services already running
./build/install/flexo/bin/flexo init --skip-docker
```

Expected output:
```
Initializing Flexo MMS at http://localhost:8080
This will:
  0. Start Docker services (Fuseki and MMS Layer 1)
  1. Generate and load cluster configuration (users, policies)
  2. Create org: localorg
  3. Create repo: localrepo
     (master branch is created automatically by the service)
Starting Fuseki (quad-store-server)...
  Using docker-compose file: /tmp/...
  Fuseki started
  Waiting for Fuseki to be ready...
  Fuseki is ready
Loading cluster configuration into Fuseki...
  Cluster configuration loaded
  Cluster configuration loaded into Fuseki
  Ensuring Fuseki index is ready...
  Fuseki index is ready
Starting layer1-service...
  layer1-service started
  Waiting for layer1-service to be ready...
  layer1-service is ready
  Verifying layer1-service health...
  layer1-service is ready
Creating organization 'localorg'...
  Organization created
Creating repository 'localrepo'...
  Repository created
Initialization complete!
Configuration updated in ~/.flexo/config with:
  default.org=localorg
  default.repo=localrepo
```

Note: Add a local remote manually for remote operations:
```bash
./build/install/flexo/bin/flexo remote add local http://localhost:8080 --set-default
```

---

## Phase 2: Branch Management

### 2.1 List Branches

```bash
./build/install/flexo/bin/flexo branch --list
# Or with explicit context
./build/install/flexo/bin/flexo --org localorg --repo localrepo branch --list
```

Expected:
```
Branch    Commit    ETag
master    ...       ...
```

### 2.2 Create a New Branch

```bash
# Create branch from master
./build/install/flexo/bin/flexo branch --create feature-xyz

# Create with explicit org/repo
./build/install/flexo/bin/flexo --org localorg --repo localrepo branch --create feature-xyz

# Create branch from specific source
./build/install/flexo/bin/flexo branch --create feature-abc --from master
```

### 2.3 Verify Branch Creation

```bash
./build/install/flexo/bin/flexo branch --list
```

Expected:
```
Branch      Commit    ETag
master      ...       ...
feature-xyz ...       ...
feature-abc ...       ...
```

---

## Phase 3: Pull (Read) Operations

### 3.1 Pull to stdout

```bash
# Pull from default branch
./build/install/flexo/bin/flexo pull

# Pull from specific branch
./build/install/flexo/bin/flexo pull --branch master

# Verbose output
./build/install/flexo/bin/flexo pull --branch master -v
```

### 3.2 Pull to File

```bash
# Pull to Turtle file (default format)
./build/install/flexo/bin/flexo pull --branch master --output model.ttl

# Pull to JSON-LD [Currently broken - server returns Turtle instead of JSON-LD]
./build/install/flexo/bin/flexo pull --branch master --format jsonld --output model.jsonld

# Pull to RDF/XML [Currently broken - server returns Turtle instead of RDF/XML]
./build/install/flexo/bin/flexo pull --branch feature-xyz --format rdfxml --output model.rdf

# Pull from specific remote
./build/install/flexo/bin/flexo --remote production pull master --output production-model.ttl
```

### 3.3 Alternative Syntax

```bash
./build/install/flexo/bin/flexo pull master
./build/install/flexo/bin/flexo pull master --output model.ttl
```

---

## Phase 4: Push (Write) Operations

### 4.1 Create Sample RDF Model

```bash
cat > /tmp/test-model.ttl << 'EOF'
@prefix ex: <http://example.org/> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

ex:Element1 a rdfs:Resource ;
    rdfs:label "Element 1" .

ex:Element2 a rdfs:Resource ;
    rdfs:label "Element 2" ;
    rdfs:comment "A sample element for demo" .
EOF
```

### 4.2 Push from File

```bash
# Push to default branch
./build/install/flexo/bin/flexo push --message "Add initial model" --input /tmp/test-model.ttl

# Push to specific branch
./build/install/flexo/bin/flexo push --branch feature-xyz \
    --message "Add feature elements" \
    --input /tmp/test-model.ttl

# Push to specific remote
./build/install/flexo/bin/flexo --remote staging push master \
    --message "Deploy to staging" \
    --input /tmp/test-model.ttl
```

### 4.3 Verify Push

```bash
# Pull from default branch to verify the push
./build/install/flexo/bin/flexo pull --branch master

# Pull from feature branch to verify
./build/install/flexo/bin/flexo pull --branch feature-xyz
```

Expected output:
```
Pulling from localorg/localrepo/master...
Fetched model with 5 statements
[RDF/Turtle output showing the pushed model]
```

### 4.4 Push Different RDF Formats

```bash
# Push JSON-LD
./build/install/flexo/bin/flexo push --format jsonld \
    --message "Add JSON-LD model" \
    --input model.jsonld

# Push RDF/XML
./build/install/flexo/bin/flexo push --format rdfxml \
    --message "Add RDF/XML model" \
    --input model.rdf
```

### 4.5 Push from stdin

```bash
cat /tmp/test-model.ttl | ./build/install/flexo/bin/flexo push --message "Push from stdin"
```

### 4.6 Alternative Syntax

```bash
./build/install/flexo/bin/flexo push master --message "Update master" --input /tmp/test-model.ttl
```

---

## Phase 5: Merge Operations

### 5.1 View Diff Between Branches

```bash
# Create diff without committing [Currently broken - TriG parsing error]
./build/install/flexo/bin/flexo merge --source feature-xyz --target master --no-commit

# Alternative syntax (requires --source flag)
./build/install/flexo/bin/flexo merge --source feature-xyz
```

Expected output:
```
Diff between feature-xyz and master:
[Diff output from server]
```

> **Known Issue**: The merge diff feature currently fails with "Not implemented (formulae, graph literals)" due to TriG parsing in the layer1-service.

### 5.2 Merge Changes

```bash
# Merge feature into master
./build/install/flexo/bin/flexo merge --source feature-xyz --target master

# Merge with custom source
./build/install/flexo/bin/flexo merge --source feature-abc
# (defaults to merging into master)
```

---

## Phase 6: Remote Management

### 6.1 List Remotes

```bash
./build/install/flexo/bin/flexo remote
./build/install/flexo/bin/flexo remote list
```

Expected (after adding local remote):
```
local *	http://localhost:8080
```

### 6.2 Add Remote

```bash
# Add local remote with local mode
./build/install/flexo/bin/flexo remote add local http://localhost:8080 \
    --local-mode \
    --local-user root \
    --set-default

# Add remote with SSH authentication
./build/install/flexo/bin/flexo remote add production https://mms.example.com \
    --ssh-key ~/.ssh/id_rsa \
    --auth-enabled true

# Add remote without setting as default
./build/install/flexo/bin/flexo remote add staging https://staging.example.com
```

### 6.3 Show Remote Details

```bash
./build/install/flexo/bin/flexo remote show local
./build/install/flexo/bin/flexo remote show production
```

Expected:
```
Remote: local (default)
  URL: http://localhost:8080
  Local Mode: enabled
```

### 6.4 Update Remote URL

```bash
./build/install/flexo/bin/flexo remote set-url staging https://new-staging.example.com
```

### 6.5 Rename Remote

```bash
./build/install/flexo/bin/flexo remote rename staging staging2
```

### 6.6 Remove Remote

```bash
./build/install/flexo/bin/flexo remote remove staging2
```

---

## Phase 7: Complete Workflow Demo

### 7.1 Setup

```bash
# Start fresh
./build/install/flexo/bin/flexo init --force

# Verify setup
./build/install/flexo/bin/flexo branch --list
```

### 7.2 Feature Branch Workflow

```bash
# 1. Create feature branch
./build/install/flexo/bin/flexo branch --create feature-login

# 2. Pull master to get baseline
./build/install/flexo/bin/flexo pull --branch master --output baseline.ttl

# 3. Create login model
cat > /tmp/login-model.ttl << 'EOF'
@prefix ex: <http://example.org/> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

ex:LoginPage a rdfs:Resource ;
    rdfs:label "Login Page" ;
    rdfs:comment "User authentication page" .

ex:UserService a rdfs:Resource ;
    rdfs:label "User Service" ;
    rdfs:comment "Handles user authentication" .
EOF

# 4. Push to feature branch
./build/install/flexo/bin/flexo push --branch feature-login \
    --message "Add login feature skeleton" \
    --input /tmp/login-model.ttl

# 5. List branches to verify
./build/install/flexo/bin/flexo branch --list

# 6. Pull from feature branch to verify
./build/install/flexo/bin/flexo pull --branch feature-login --output feature-login.ttl
```

### 7.3 Merge Feature into Master

```bash
# 1. View diff before merging [Currently broken - TriG parsing error]
./build/install/flexo/bin/flexo merge --source feature-login --target master --no-commit

# 2. Merge (if diff looks correct) [Currently broken - TriG parsing error]
./build/install/flexo/bin/flexo merge --source feature-login --target master

# 3. Verify master has the changes
./build/install/flexo/bin/flexo pull --branch master --output master-updated.ttl
```

> **Known Issue**: Merge operations are currently broken due to layer1-service TriG parsing.

---

## Phase 8: Configuration Management

### 8.1 View Current Configuration

```bash
cat ~/.flexo/config
```

### 8.2 Check Individual Settings

```bash
# Use verbose mode to see effective configuration
./build/install/flexo/bin/flexo -v branch --list
```

### 8.3 Environment Variable Overrides

```bash
# Override URL via environment
export FLEXO_MMS_URL=http://localhost:8080
export FLEXO_DEFAULT_ORG=localorg
export FLEXO_DEFAULT_REPO=localrepo

./build/install/flexo/bin/flexo branch --list
```

---

## Phase 9: RDF Format Support

### 9.1 Supported Formats

- `turtle` (default)
- `jsonld`
- `rdfxml`
- `ntriples`
- `nquads`
- `trig`

### 9.2 Format Conversion Demo

```bash
# Pull in Turtle format (default and working)
./build/install/flexo/bin/flexo pull --branch master --format turtle --output model.ttl

# Pull in JSON-LD format [Currently broken - server returns Turtle]
./build/install/flexo/bin/flexo pull --branch master --format jsonld --output model.jsonld

# Pull in RDF/XML format [Currently broken - server returns Turtle]
./build/install/flexo/bin/flexo pull --branch master --format rdfxml --output model.rdf

# Push different formats (these work as input formats)
./build/install/flexo/bin/flexo push --format jsonld \
    --message "Push JSON-LD" \
    --input model.jsonld
```

---

## Phase 10: Verbose and Debug Output

### 10.1 Verbose Mode

```bash
./build/install/flexo/bin/flexo -v branch --list
./build/install/flexo/bin/flexo --verbose pull --branch master
```

### 10.2 Debug Authentication

```bash
./build/install/flexo/bin/flexo -v pull --branch master
# Shows JWT token generation and auth headers
```

---

## Phase 11: Error Handling Demo

### 11.1 Branch Not Found

```bash
./build/install/flexo/bin/flexo pull --branch nonexistent
```

Expected error:
```
Error: Failed to get model: HTTP 404 - Branch not found
```

### 11.2 Authentication Failure (wrong mode)

```bash
# Temporarily disable local mode (wrong for local dev)
echo "local.mode=false" >> ~/.flexo/config
./build/install/flexo/bin/flexo branch --list

# Restore local mode
sed -i 's/local.mode=false/local.mode=true/' ~/.flexo/config
```

### 11.3 Invalid RDF Format

```bash
# Create invalid RDF
echo "this is not valid RDF" > /tmp/bad.ttl

./build/install/flexo/bin/flexo push --message "Bad push" --input /tmp/bad.ttl
```

Expected error:
```
Error: Failed to push model: HTTP 400 - Bad Request
```

---

## Cleanup

### Stop Docker Services

```bash
docker stop layer1-service quad-store-server
# Or
docker-compose -f flexo-mms-docker-compose.yml down
```

### Clear Configuration

```bash
rm ~/.flexo/config
```

### Reset Database

```bash
curl -X POST http://localhost:3030/ds/update --data "DELETE WHERE { GRAPH ?g { ?s ?p ?o } }"
```

---

## Summary of Commands

| Command | Description |
|---------|-------------|
| `flexo init` | Initialize local MMS instance |
| `flexo branch --list` | List all branches |
| `flexo branch --create <name>` | Create new branch |
| `flexo pull` | Fetch model from branch |
| `flexo push` | Push model to branch |
| `flexo merge` | Merge branches |
| `flexo remote` | Manage remotes |
| `flexo rm` | Remove elements (planned) |

---

## Next Steps

- Explore the [SysML v2 Plugin](./flexo-cli-sysmlv2-plugin/README.md)
- Set up [production authentication](./README.md#ssh-key-based-authentication-production)
- Create [custom plugins](./README-PLUGINS.md)