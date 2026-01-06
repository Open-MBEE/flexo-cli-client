package org.openmbee.flexo.cli.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.jena.rdf.model.Model;
import org.openmbee.flexo.cli.model.Branch;
import org.openmbee.flexo.cli.util.RdfParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client for communicating with Flexo MMS Layer 1 Service
 */
public class FlexoMmsClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(FlexoMmsClient.class);

    private final String baseUrl;
    private final AuthenticationHandler authHandler;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FlexoMmsClient(String baseUrl, AuthenticationHandler authHandler) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authHandler = authHandler;
        this.httpClient = HttpClients.createDefault();
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
     */
    public Branch createBranch(String orgId, String repoId, String branchId, String fromCommit) throws IOException {
        String url = String.format("%s/orgs/%s/repos/%s/branches/%s", baseUrl, orgId, repoId, branchId);
        logger.debug("PUT {}", url);

        HttpPut request = new HttpPut(url);
        addAuthHeader(request);

        // TODO: Add branch creation body if needed based on API
        request.setHeader("Content-Type", "text/turtle");
        request.setEntity(new StringEntity("", ContentType.parse("text/turtle")));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

            if (statusCode >= 200 && statusCode < 300) {
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

    private void addAuthHeader(HttpUriRequestBase request) {
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
            // TODO: Parse RDF model to extract branch information
            // For now, return empty list
            logger.debug("Parsed RDF model with {} statements", model.size());
        } catch (Exception e) {
            logger.error("Failed to parse branches from RDF: {}", e.getMessage());
        }
        return branches;
    }

    private String extractCommitId(String response) {
        // Try to extract commit ID from response
        // This is a placeholder - actual implementation depends on API response format
        try {
            JsonNode node = objectMapper.readTree(response);
            if (node.has("commitId")) {
                return node.get("commitId").asText();
            }
        } catch (Exception e) {
            logger.debug("Could not extract commit ID from response: {}", e.getMessage());
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
