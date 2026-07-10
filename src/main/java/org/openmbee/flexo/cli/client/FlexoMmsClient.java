package org.openmbee.flexo.cli.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.jena.rdf.model.Model;
import org.openmbee.flexo.cli.config.FlexoConfig;
import org.openmbee.flexo.cli.model.Branch;
import org.openmbee.flexo.cli.model.Collection;
import org.openmbee.flexo.cli.util.RdfParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP client for communicating with Flexo MMS Layer 1 Service
 */
public class FlexoMmsClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(FlexoMmsClient.class);

    private final String baseUrl;
    private final AuthenticationHandler authHandler;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final FlexoConfig config;

    public FlexoMmsClient(String baseUrl, AuthenticationHandler authHandler) {
        this(baseUrl, authHandler, null);
    }

    public FlexoMmsClient(String baseUrl, AuthenticationHandler authHandler, FlexoConfig config) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authHandler = authHandler;
        this.config = config;
        
        // Use HttpClientFactory if config is available (for proxy support)
        if (config != null) {
            HttpClientFactory factory = new HttpClientFactory(config);
            this.httpClient = factory.createClient();
            if (config.isProxyConfigured()) {
                logger.debug("HTTP client created with proxy configuration");
            }
        } else {
            // Fall back to default client for backward compatibility
            this.httpClient = org.apache.hc.client5.http.impl.classic.HttpClients.createDefault();
        }
        
        this.objectMapper = new ObjectMapper();
    }

    /**
     * List all branches in a repository
     */
    public List<Branch> listBranches(String orgId, String repoId) throws IOException {
        String url = String.format("%s/orgs/%s/repos/%s/branches", baseUrl, orgId, repoId);
        logger.debug("GET {}", url);

        HttpGet request = new HttpGet(url);
        addAuthHeader(request);
        request.setHeader("Accept", "text/turtle");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

            if (statusCode >= 200 && statusCode < 300) {
                return parseBranchesFromRdf(responseBody);
            } else {
                throw new IOException("Failed to list branches: HTTP " + statusCode + " - " + responseBody);
            }
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    /**
     * Get a specific branch
     */
    public Branch getBranch(String orgId, String repoId, String branchId) throws IOException {
        String url = String.format("%s/orgs/%s/repos/%s/branches/%s", baseUrl, orgId, repoId, branchId);
        logger.debug("GET {}", url);

        HttpGet request = new HttpGet(url);
        addAuthHeader(request);
        request.setHeader("Accept", "text/turtle");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

            if (statusCode >= 200 && statusCode < 300) {
                List<Branch> branches = parseBranchesFromRdf(responseBody);
                return branches.isEmpty() ? null : branches.get(0);
            } else {
                throw new IOException("Failed to get branch: HTTP " + statusCode + " - " + responseBody);
            }
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    /**
     * Create a new branch
     *
     * Creates a branch by referencing the source branch using a relative URL.
     * Based on integration test approach in flexo-mms-layer1-service.
     */
    public Branch createBranch(String orgId, String repoId, String branchId, String fromBranch) throws IOException {
        // If no source branch specified, use master
        if (fromBranch == null || fromBranch.isEmpty()) {
            fromBranch = "master";
        }

        String branchUrl = String.format("%s/orgs/%s/repos/%s/branches/%s", baseUrl, orgId, repoId, branchId);
        logger.debug("Creating branch at: {}", branchUrl);

        // Use relative URL for mms:ref (key insight from integration tests!)
        // The tests use <../branches/master> which is a relative reference
        String relativeRefUrl = "../branches/" + fromBranch;

        // Build RDF body with relative mms:ref
        StringBuilder rdfBody = new StringBuilder();
        rdfBody.append("@prefix mms: <https://mms.openmbee.org/rdf/ontology/> .\n");
        rdfBody.append("@prefix dct: <http://purl.org/dc/terms/> .\n\n");
        rdfBody.append("<> dct:title \"").append(branchId).append("\"@en .\n");
        rdfBody.append("<> mms:ref <").append(relativeRefUrl).append("> .\n");

        HttpPut request = new HttpPut(branchUrl);
        addAuthHeader(request);
        request.setHeader("Content-Type", "text/turtle");
        request.setEntity(new StringEntity(rdfBody.toString(), ContentType.parse("text/turtle")));

        logger.debug("Branch creation RDF:\n{}", rdfBody.toString());

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

            if (statusCode >= 200 && statusCode < 300) {
                logger.debug("Branch created successfully");
                return getBranch(orgId, repoId, branchId);
            } else {
                throw new IOException("Failed to create branch: HTTP " + statusCode + " - " + responseBody);
            }
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    /**
     * Get model from a branch (pull operation)
     */
    public Model getModel(String orgId, String repoId, String branchId, String format) throws IOException {
        String url = String.format("%s/orgs/%s/repos/%s/branches/%s/graph", baseUrl, orgId, repoId, branchId);
        logger.debug("GET {}", url);

        HttpGet request = new HttpGet(url);
        addAuthHeader(request);
        request.setHeader("Accept", RdfParser.getContentType(format));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

            if (statusCode >= 200 && statusCode < 300) {
                return RdfParser.parseString(responseBody, format);
            } else {
                throw new IOException("Failed to get model: HTTP " + statusCode + " - " + responseBody);
            }
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    /**
     * Put model to a branch (push operation)
     */
    public String putModel(String orgId, String repoId, String branchId, Model model, String format, String commitMessage) throws IOException {
        String url = String.format("%s/orgs/%s/repos/%s/branches/%s/graph", baseUrl, orgId, repoId, branchId);
        logger.debug("PUT {}", url);

        HttpPut request = new HttpPut(url);
        addAuthHeader(request);

        String modelContent = RdfParser.toString(model, format);
        request.setHeader("Content-Type", RdfParser.getContentType(format));
        if (commitMessage != null && !commitMessage.isEmpty()) {
            request.setHeader("X-Commit-Message", commitMessage);
        }
        request.setEntity(new StringEntity(modelContent, ContentType.parse(RdfParser.getContentType(format))));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

            if (statusCode >= 200 && statusCode < 300) {
                // Extract commit ID from response if available
                return extractCommitId(responseBody);
            } else {
                throw new IOException("Failed to push model: HTTP " + statusCode + " - " + responseBody);
            }
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    /**
     * Create a diff between two commits/branches
     */
    public String createDiff(String orgId, String repoId, String sourceRef, String targetRef) throws IOException {
        String url = String.format("%s/orgs/%s/repos/%s/diffs", baseUrl, orgId, repoId);
        logger.debug("POST {}", url);

        HttpPost request = new HttpPost(url);
        addAuthHeader(request);

        // TODO: Construct proper diff request body based on API
        String diffRequest = String.format("{\"source\": \"%s\", \"target\": \"%s\"}", sourceRef, targetRef);
        request.setHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(diffRequest, ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

            if (statusCode >= 200 && statusCode < 300) {
                return responseBody;
            } else {
                throw new IOException("Failed to create diff: HTTP " + statusCode + " - " + responseBody);
            }
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    /**
     * Squash the linear commit path between two locks into a single commit.
     *
     * Posts to the repo's /squash endpoint with a Turtle body referencing the
     * source and destination lock IRIs via mms:srcRef and mms:dstRef. The
     * service squashes the linear commit path between the two locks' commits
     * into a single diff on the newer (destination) lock's commit.
     *
     * @param orgId    Organization ID
     * @param repoId   Repository ID
     * @param srcLock  Source lock ID (or a full lock IRI)
     * @param dstLock  Destination lock ID (or a full lock IRI); must be the newer commit
     * @return The commit ID of the resulting squashed commit
     */
    public String squash(String orgId, String repoId, String srcLock, String dstLock) throws IOException {
        String url = String.format("%s/orgs/%s/repos/%s/squash", baseUrl, orgId, repoId);
        logger.debug("POST {}", url);

        String srcRef = resolveLockIri(orgId, repoId, srcLock);
        String dstRef = resolveLockIri(orgId, repoId, dstLock);

        // Build RDF body referencing the two locks to squash between.
        StringBuilder rdfBody = new StringBuilder();
        rdfBody.append("@prefix mms: <https://mms.openmbee.org/rdf/ontology/> .\n\n");
        rdfBody.append("<> mms:srcRef <").append(srcRef).append("> .\n");
        rdfBody.append("<> mms:dstRef <").append(dstRef).append("> .\n");

        HttpPost request = new HttpPost(url);
        addAuthHeader(request);
        request.setHeader("Content-Type", "text/turtle");
        request.setEntity(new StringEntity(rdfBody.toString(), ContentType.parse("text/turtle")));

        logger.debug("Squash RDF:\n{}", rdfBody.toString());

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

            if (statusCode >= 200 && statusCode < 300) {
                logger.debug("Squash completed successfully");
                return extractCommitId(responseBody);
            } else {
                throw new IOException("Failed to squash commits: HTTP " + statusCode + " - " + responseBody);
            }
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    /**
     * Resolve a lock reference to a full lock IRI. If the reference already
     * looks like an absolute IRI it is returned unchanged; otherwise it is
     * treated as a lock ID under the given repository.
     */
    private String resolveLockIri(String orgId, String repoId, String lockRef) {
        if (lockRef == null || lockRef.isEmpty()) {
            return lockRef;
        }
        if (lockRef.startsWith("http://") || lockRef.startsWith("https://")) {
            return lockRef;
        }
        return String.format("%s/orgs/%s/repos/%s/locks/%s", baseUrl, orgId, repoId, lockRef);
    }

    /**
     * List all collections in an organization.
     */
    public List<Collection> listCollections(String orgId) throws IOException {
        String url = String.format("%s/orgs/%s/collections", baseUrl, orgId);
        logger.debug("GET {}", url);

        HttpGet request = new HttpGet(url);
        addAuthHeader(request);
        request.setHeader("Accept", "text/turtle");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

            if (statusCode >= 200 && statusCode < 300) {
                return parseCollectionsFromRdf(responseBody);
            } else {
                throw new IOException("Failed to list collections: HTTP " + statusCode + " - " + responseBody);
            }
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    /**
     * Get a specific collection.
     */
    public Collection getCollection(String orgId, String collectionId) throws IOException {
        String url = String.format("%s/orgs/%s/collections/%s", baseUrl, orgId, collectionId);
        logger.debug("GET {}", url);

        HttpGet request = new HttpGet(url);
        addAuthHeader(request);
        request.setHeader("Accept", "text/turtle");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

            if (statusCode >= 200 && statusCode < 300) {
                List<Collection> collections = parseCollectionsFromRdf(responseBody);
                return collections.isEmpty() ? null : collections.get(0);
            } else {
                throw new IOException("Failed to get collection: HTTP " + statusCode + " - " + responseBody);
            }
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    /**
     * Create a new collection that groups the given refs.
     *
     * Puts to the collection resource with a Turtle body declaring one
     * mms:collects statement per collected ref. The refs are branch, lock or
     * scratch IRIs; relative IRIs are resolved against the collection resource.
     *
     * @param orgId        Organization ID
     * @param collectionId Collection ID (slug)
     * @param refIris      One or more ref IRIs to collect
     * @return The created collection
     */
    public Collection createCollection(String orgId, String collectionId, List<String> refIris) throws IOException {
        if (refIris == null || refIris.isEmpty()) {
            throw new IOException("A collection requires at least one collected ref");
        }

        String url = String.format("%s/orgs/%s/collections/%s", baseUrl, orgId, collectionId);
        logger.debug("PUT {}", url);

        // Build RDF body with an mms:collects statement per ref.
        StringBuilder rdfBody = new StringBuilder();
        rdfBody.append("@prefix mms: <https://mms.openmbee.org/rdf/ontology/> .\n");
        rdfBody.append("@prefix dct: <http://purl.org/dc/terms/> .\n\n");
        rdfBody.append("<> dct:title \"").append(collectionId).append("\"@en .\n");
        for (String refIri : refIris) {
            rdfBody.append("<> mms:collects <").append(refIri).append("> .\n");
        }

        HttpPut request = new HttpPut(url);
        addAuthHeader(request);
        request.setHeader("Content-Type", "text/turtle");
        request.setEntity(new StringEntity(rdfBody.toString(), ContentType.parse("text/turtle")));

        logger.debug("Collection creation RDF:\n{}", rdfBody.toString());

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

            if (statusCode >= 200 && statusCode < 300) {
                logger.debug("Collection created successfully");
                return getCollection(orgId, collectionId);
            } else {
                throw new IOException("Failed to create collection: HTTP " + statusCode + " - " + responseBody);
            }
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    /**
     * Get the union graph of all refs collected by a collection (pull operation).
     */
    public Model getCollectionModel(String orgId, String collectionId, String format) throws IOException {
        String url = String.format("%s/orgs/%s/collections/%s/graph", baseUrl, orgId, collectionId);
        logger.debug("GET {}", url);

        HttpGet request = new HttpGet(url);
        addAuthHeader(request);
        request.setHeader("Accept", RdfParser.getContentType(format));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

            if (statusCode >= 200 && statusCode < 300) {
                return RdfParser.parseString(responseBody, format);
            } else {
                throw new IOException("Failed to get collection model: HTTP " + statusCode + " - " + responseBody);
            }
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    /**
     * Run a SPARQL query across the union of all graphs collected by a collection.
     *
     * @return The raw query result body as returned by the service
     */
    public String queryCollection(String orgId, String collectionId, String sparql) throws IOException {
        String url = String.format("%s/orgs/%s/collections/%s/query", baseUrl, orgId, collectionId);
        logger.debug("POST {}", url);

        HttpPost request = new HttpPost(url);
        addAuthHeader(request);
        request.setHeader("Content-Type", "application/sparql-query");
        request.setHeader("Accept", "application/sparql-results+json");
        request.setEntity(new StringEntity(sparql, ContentType.parse("application/sparql-query")));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

            if (statusCode >= 200 && statusCode < 300) {
                return responseBody;
            } else {
                throw new IOException("Failed to query collection: HTTP " + statusCode + " - " + responseBody);
            }
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    /**
     * Execute arbitrary HTTP request with authentication
     */
    public String executeRequest(HttpUriRequestBase request) throws IOException {
        addAuthHeader(request);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

            if (statusCode >= 200 && statusCode < 300) {
                return responseBody;
            } else {
                throw new IOException("Request failed: HTTP " + statusCode + " - " + responseBody);
            }
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    /**
     * Get the base URL for the MMS service
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Get the FlexoConfig this client was created with (may be null when
     * constructed via the legacy two-arg constructor). Exposed so callers
     * that create their own HTTP clients (e.g. plugins targeting a different
     * backend) can reuse the same proxy configuration.
     */
    public FlexoConfig getConfig() {
        return config;
    }

    public void addAuthHeader(HttpUriRequestBase request) {
        if (authHandler != null && authHandler.isEnabled()) {
            String authHeader = authHandler.getAuthorizationHeader();
            if (authHeader != null) {
                request.setHeader("Authorization", authHeader);
            }
        }
    }

    private List<Branch> parseBranchesFromRdf(String rdfContent) {
        List<Branch> branches = new ArrayList<>();
        try {
            Model model = RdfParser.parseString(rdfContent, "turtle");
            logger.debug("Parsed RDF model with {} statements", model.size());

            // Query for branch resources
            String mmsNs = "https://mms.openmbee.org/rdf/ontology/";
            org.apache.jena.rdf.model.Property rdfType = model.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
            org.apache.jena.rdf.model.Resource branchType = model.getResource(mmsNs + "Branch");
            org.apache.jena.rdf.model.Property mmsId = model.getProperty(mmsNs + "id");
            org.apache.jena.rdf.model.Property mmsCommit = model.getProperty(mmsNs + "commit");
            org.apache.jena.rdf.model.Property mmsEtag = model.getProperty(mmsNs + "etag");

            // Find all branch subjects
            org.apache.jena.rdf.model.ResIterator iter = model.listSubjectsWithProperty(rdfType, branchType);
            while (iter.hasNext()) {
                org.apache.jena.rdf.model.Resource branchRes = iter.nextResource();
                Branch branch = new Branch();

                // Set ID from URI or mms:id property
                if (branchRes.hasProperty(mmsId)) {
                    branch.setId(branchRes.getProperty(mmsId).getString());
                } else {
                    // Extract ID from URI (last path segment)
                    String uri = branchRes.getURI();
                    if (uri != null && uri.contains("/branches/")) {
                        String id = uri.substring(uri.lastIndexOf("/") + 1);
                        branch.setId(id);
                    }
                }

                // Set commit ID
                if (branchRes.hasProperty(mmsCommit)) {
                    String commitUri = branchRes.getProperty(mmsCommit).getResource().getURI();
                    branch.setCommitId(commitUri);
                }

                // Set etag
                if (branchRes.hasProperty(mmsEtag)) {
                    branch.setEtag(branchRes.getProperty(mmsEtag).getString());
                }

                // Set name same as ID for now
                branch.setName(branch.getId());

                branches.add(branch);
            }

            logger.debug("Parsed {} branches from RDF", branches.size());
        } catch (Exception e) {
            logger.error("Failed to parse branches from RDF: {}", e.getMessage(), e);
        }
        return branches;
    }

    private List<Collection> parseCollectionsFromRdf(String rdfContent) {
        List<Collection> collections = new ArrayList<>();
        try {
            Model model = RdfParser.parseString(rdfContent, "turtle");
            logger.debug("Parsed RDF model with {} statements", model.size());

            String mmsNs = "https://mms.openmbee.org/rdf/ontology/";
            org.apache.jena.rdf.model.Property rdfType = model.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
            org.apache.jena.rdf.model.Resource collectionType = model.getResource(mmsNs + "Collection");
            org.apache.jena.rdf.model.Property mmsId = model.getProperty(mmsNs + "id");
            org.apache.jena.rdf.model.Property mmsEtag = model.getProperty(mmsNs + "etag");
            org.apache.jena.rdf.model.Property mmsCollects = model.getProperty(mmsNs + "collects");

            org.apache.jena.rdf.model.ResIterator iter = model.listSubjectsWithProperty(rdfType, collectionType);
            while (iter.hasNext()) {
                org.apache.jena.rdf.model.Resource collectionRes = iter.nextResource();
                Collection collection = new Collection();

                // Set ID from mms:id property or the URI's last path segment
                if (collectionRes.hasProperty(mmsId)) {
                    collection.setId(collectionRes.getProperty(mmsId).getString());
                } else {
                    String uri = collectionRes.getURI();
                    if (uri != null && uri.contains("/collections/")) {
                        collection.setId(uri.substring(uri.lastIndexOf("/") + 1));
                    }
                }

                // Set etag
                if (collectionRes.hasProperty(mmsEtag)) {
                    collection.setEtag(collectionRes.getProperty(mmsEtag).getString());
                }

                // Collect all mms:collects ref IRIs
                org.apache.jena.rdf.model.StmtIterator collectsIter = collectionRes.listProperties(mmsCollects);
                while (collectsIter.hasNext()) {
                    org.apache.jena.rdf.model.Statement stmt = collectsIter.nextStatement();
                    if (stmt.getObject().isResource()) {
                        collection.addCollectedRef(stmt.getObject().asResource().getURI());
                    }
                }

                collection.setName(collection.getId());
                collections.add(collection);
            }

            logger.debug("Parsed {} collections from RDF", collections.size());
        } catch (Exception e) {
            logger.error("Failed to parse collections from RDF: {}", e.getMessage(), e);
        }
        return collections;
    }

    /**
     * Matches a commit IRI of the form .../commits/&lt;id&gt; and captures the id.
     * The id segment stops at the next '/', '>' or whitespace.
     */
    private static final Pattern COMMIT_ID_PATTERN =
        Pattern.compile("/commits/([^/>\\s]+)");

    private String extractCommitId(String response) {
        if (response == null || response.isEmpty()) {
            return "success";
        }

        // The Layer 1 service returns an RDF/SPARQL payload (a set of PREFIX
        // declarations) rather than JSON. Only attempt a JSON parse when the
        // body actually looks like JSON, so we don't log a spurious parse error.
        String trimmed = response.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode node = objectMapper.readTree(response);
                if (node.has("commitId")) {
                    return node.get("commitId").asText();
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                logger.debug("Could not parse JSON commit response: {}", e.getOriginalMessage());
            }
        }

        // Extract the commit id from a commit IRI (e.g. .../commits/<uuid>).
        Matcher matcher = COMMIT_ID_PATTERN.matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return "success";
    }

    @Override
    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }
}
