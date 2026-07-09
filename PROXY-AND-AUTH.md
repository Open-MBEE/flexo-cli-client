# Proxy and Authentication Configuration

This document describes how to configure Flexo CLI to work behind corporate proxies and with pre-existing JWT tokens.

## Proxy Configuration

Flexo CLI supports automatic proxy configuration from standard environment variables, making it work seamlessly in corporate environments.

### Environment Variables

The CLI recognizes these standard proxy environment variables:

- `HTTP_PROXY` or `http_proxy` - Proxy for HTTP connections
- `HTTPS_PROXY` or `https_proxy` - Proxy for HTTPS connections  
- `NO_PROXY` or `no_proxy` - Comma-separated list of hosts to exclude from proxying

### Proxy URL Formats

Proxy URLs can be specified in several formats:

```bash
# Full URL with scheme
export HTTPS_PROXY=http://proxy.example.com:8080
export HTTPS_PROXY=https://secure-proxy.example.com:3128

# Host and port only (defaults to http://)
export HTTPS_PROXY=proxy.example.com:8080

# With authentication (if supported by proxy)
export HTTPS_PROXY=http://user:pass@proxy.example.com:8080
```

### Proxy Exclusions (NO_PROXY)

The `NO_PROXY` variable supports several formats for excluding hosts:

```bash
# Exact hostname match
export NO_PROXY=localhost,127.0.0.1

# Domain suffix match (matches all subdomains)
export NO_PROXY=.example.com,example.com

# Multiple exclusions (comma-separated)
export NO_PROXY=localhost,.internal.corp,.example.com,192.168.1.1
```

**Supported patterns:**
- `localhost` - Matches localhost, 127.0.0.1, and ::1
- `.example.com` - Matches api.example.com, www.example.com, etc.
- `example.com` - Matches example.com and *.example.com
- `192.168.1.1` - Exact IP match

**Note:** CIDR notation (e.g., `192.168.1.0/24`) is not currently supported.

### Configuration File

Alternatively, you can configure proxies in `~/.flexo/config`:

```properties
# HTTP proxy
proxy.http.url=http://proxy.example.com:8080

# HTTPS proxy
proxy.https.url=http://proxy.example.com:8080

# Exclusions (comma-separated)
proxy.no=localhost,.internal.corp,.example.com
```

**Note:** Environment variables take precedence over config file settings.

### Complete Example

```bash
# Set up proxy for corporate network
export HTTPS_PROXY=http://proxy.corp.example.com:3128
export HTTP_PROXY=http://proxy.corp.example.com:3128
export NO_PROXY=localhost,127.0.0.1,.internal.corp

# Use Flexo CLI normally
flexo init --org myorg --repo myrepo
flexo pull master --output model.ttl

# The CLI will automatically use the proxy for external connections
# while bypassing it for localhost and internal hosts
```

## JWT Token Authentication

For environments where JWT tokens are pre-issued by an external authentication system, you can provide a long-lived token instead of using SSH keys or local mode.

### Environment Variable

The easiest way to provide a token is via environment variable:

```bash
export FLEXO_AUTH_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."

flexo pull master --output model.ttl
```

The token will be used automatically for all API requests.

### Configuration File

You can also configure the token in `~/.flexo/config`:

```properties
# Pre-existing JWT token
auth.token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Note:** `FLEXO_AUTH_TOKEN` environment variable takes precedence over the config file.

### Token Format

The token can be provided in two formats:

```bash
# Raw token (Bearer prefix will be added automatically)
export FLEXO_AUTH_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."

# With Bearer prefix (will be used as-is)
export FLEXO_AUTH_TOKEN="Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### Token Management

**Security considerations:**
- Tokens should be treated as sensitive credentials
- Store tokens securely (e.g., in a secrets manager)
- Use short-lived tokens when possible
- Rotate tokens regularly according to your security policy

**Token expiration:**
The CLI does not validate token expiration. If your token expires, you'll receive HTTP 401 errors. Update the token when this occurs.

### Remote-Specific Tokens

You can configure different tokens for different remotes:

```properties
# Remote configuration
remote.production.url=https://mms.example.com
remote.production.authToken=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...

remote.staging.url=https://mms-staging.example.com  
remote.staging.authToken=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

Then use with `--remote` flag:

```bash
flexo --remote production pull master --output model.ttl
flexo --remote staging push master --input model.ttl
```

## Combined Example

Using both proxy and token authentication:

```bash
# Corporate proxy setup
export HTTPS_PROXY=http://proxy.corp.example.com:8080
export HTTP_PROXY=http://proxy.corp.example.com:8080
export NO_PROXY=localhost,.internal.corp

# Authentication token
export FLEXO_AUTH_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."

# Use Flexo CLI
flexo --remote production pull master --output model.ttl
```

The CLI will:
1. Use the proxy for connections to the production remote
2. Exclude internal hosts from proxying (based on NO_PROXY)
3. Authenticate using the provided JWT token

## Troubleshooting

### Proxy Connection Issues

If you're having proxy connection problems:

1. **Verify proxy configuration:**
   ```bash
   echo $HTTPS_PROXY
   echo $NO_PROXY
   ```

2. **Test proxy manually:**
   ```bash
   curl -v --proxy $HTTPS_PROXY https://example.com
   ```

3. **Enable verbose logging:**
   ```bash
   flexo -v pull master --output model.ttl
   ```
   Look for log messages about proxy configuration.

4. **Check proxy exclusions:**
   Make sure localhost is in NO_PROXY if your MMS is local:
   ```bash
   export NO_PROXY=localhost,127.0.0.1
   ```

### Authentication Issues

If you're getting HTTP 401 errors:

1. **Verify token is set:**
   ```bash
   echo ${FLEXO_AUTH_TOKEN:0:20}...
   ```

2. **Check token format:**
   Token should be a valid JWT (three base64-encoded parts separated by dots)

3. **Verify token expiration:**
   Decode the token to check expiration time:
   ```bash
   # Using jwt-cli or online JWT decoder
   jwt decode $FLEXO_AUTH_TOKEN
   ```

4. **Test with curl:**
   ```bash
   curl -H "Authorization: Bearer $FLEXO_AUTH_TOKEN" \
        https://your-mms-server/orgs
   ```

## Migration from Existing Configuration

### Updating from auth.remote

If you're using the deprecated `auth.remote` configuration:

**Old configuration:**
```properties
auth.remote=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

**New configuration:**
```properties
auth.token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

Or use environment variable:
```bash
export FLEXO_AUTH_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
```

Both formats are currently supported, but `auth.token` / `FLEXO_AUTH_TOKEN` is recommended.

## See Also

- [Main README](README.md) - General CLI documentation
- [Configuration Guide](README.md#configuration) - All configuration options
- [Authentication Methods](README.md#authentication) - SSH keys and local mode
