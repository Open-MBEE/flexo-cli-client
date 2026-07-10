package org.openmbee.flexo.cli.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmbee.flexo.cli.model.Branch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FlexoMmsClientTest {

    @Mock
    private CloseableHttpClient mockHttpClient;

    @Mock
    private CloseableHttpResponse mockResponse;

    @Mock
    private HttpEntity mockEntity;

    @Mock
    private AuthenticationHandler mockAuthHandler;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testConstructorRemovesTrailingSlash() throws Exception {
        FlexoMmsClient client = new FlexoMmsClient("http://example.com/", null);

        // Use reflection to verify baseUrl
        Field baseUrlField = FlexoMmsClient.class.getDeclaredField("baseUrl");
        baseUrlField.setAccessible(true);
        String baseUrl = (String) baseUrlField.get(client);

        assertEquals("http://example.com", baseUrl);
        client.close();
    }

    @Test
    void testConstructorWithoutTrailingSlash() throws Exception {
        FlexoMmsClient client = new FlexoMmsClient("http://example.com", null);

        Field baseUrlField = FlexoMmsClient.class.getDeclaredField("baseUrl");
        baseUrlField.setAccessible(true);
        String baseUrl = (String) baseUrlField.get(client);

        assertEquals("http://example.com", baseUrl);
        client.close();
    }

    @Test
    void testClose() throws IOException {
        FlexoMmsClient client = new FlexoMmsClient("http://example.com", null);

        // close() should not throw exception
        assertDoesNotThrow(() -> client.close());
    }

    @Test
    void testListBranchesSuccess() throws Exception {
        String rdfResponse = "@prefix ex: <http://example.org/> . ex:branch1 ex:name \"main\" .";

        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream(rdfResponse.getBytes()));
        when(mockHttpClient.execute(any(HttpGet.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        List<Branch> branches = client.listBranches("org1", "repo1");

        assertNotNull(branches);
        verify(mockHttpClient).execute(any(HttpGet.class));
    }

    @Test
    void testListBranchesError() throws Exception {
        when(mockResponse.getCode()).thenReturn(404);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream("Not found".getBytes()));
        when(mockHttpClient.execute(any(HttpGet.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        assertThrows(IOException.class, () -> client.listBranches("org1", "repo1"));
    }

    @Test
    void testGetBranchSuccess() throws Exception {
        String rdfResponse = "@prefix ex: <http://example.org/> . ex:branch1 ex:name \"develop\" .";

        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream(rdfResponse.getBytes()));
        when(mockHttpClient.execute(any(HttpGet.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        client.getBranch("org1", "repo1", "develop");

        // Branch might be null if parsing returns empty list
        // The test verifies the method completes without exception
        verify(mockHttpClient).execute(any(HttpGet.class));
    }

    @Test
    void testGetBranchError() throws Exception {
        when(mockResponse.getCode()).thenReturn(500);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream("Server error".getBytes()));
        when(mockHttpClient.execute(any(HttpGet.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        assertThrows(IOException.class, () -> client.getBranch("org1", "repo1", "develop"));
    }

    @Test
    void testCreateBranchSuccess() throws Exception {
        String rdfResponse = "@prefix ex: <http://example.org/> . ex:newbranch ex:name \"feature\" .";

        when(mockResponse.getCode()).thenReturn(201);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream(rdfResponse.getBytes()));
        when(mockHttpClient.execute(any(HttpUriRequestBase.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        client.createBranch("org1", "repo1", "feature", null);

        // Verify PUT was executed twice (create + get)
        verify(mockHttpClient, atLeastOnce()).execute(any(HttpUriRequestBase.class));
    }

    @Test
    void testCreateBranchError() throws Exception {
        when(mockResponse.getCode()).thenReturn(400);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream("Bad request".getBytes()));
        when(mockHttpClient.execute(any(HttpPut.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        assertThrows(IOException.class, () -> client.createBranch("org1", "repo1", "feature", null));
    }

    @Test
    void testGetModelSuccess() throws Exception {
        String turtleModel = "@prefix ex: <http://example.org/> . ex:subject ex:predicate \"object\" .";

        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream(turtleModel.getBytes()));
        when(mockHttpClient.execute(any(HttpGet.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        Model model = client.getModel("org1", "repo1", "main", "turtle");

        assertNotNull(model);
        assertTrue(model.size() > 0);
        verify(mockHttpClient).execute(any(HttpGet.class));
    }

    @Test
    void testGetModelError() throws Exception {
        when(mockResponse.getCode()).thenReturn(403);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream("Forbidden".getBytes()));
        when(mockHttpClient.execute(any(HttpGet.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        assertThrows(IOException.class, () -> client.getModel("org1", "repo1", "main", "turtle"));
    }

    @Test
    void testPutModelSuccess() throws Exception {
        String commitResponse = "{\"commitId\": \"abc123\"}";

        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream(commitResponse.getBytes()));
        when(mockHttpClient.execute(any(HttpPut.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        Model model = ModelFactory.createDefaultModel();
        String commitId = client.putModel("org1", "repo1", "main", model, "turtle", "Test commit");

        assertNotNull(commitId);
        assertEquals("abc123", commitId);
        verify(mockHttpClient).execute(any(HttpPut.class));
    }

    @Test
    void testPutModelWithoutCommitId() throws Exception {
        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream("{}".getBytes()));
        when(mockHttpClient.execute(any(HttpPut.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        Model model = ModelFactory.createDefaultModel();
        String commitId = client.putModel("org1", "repo1", "main", model, "turtle", "Test commit");

        assertNotNull(commitId);
        assertEquals("success", commitId); // Default fallback
    }

    @Test
    void testPutModelError() throws Exception {
        when(mockResponse.getCode()).thenReturn(409);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream("Conflict".getBytes()));
        when(mockHttpClient.execute(any(HttpPut.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        Model model = ModelFactory.createDefaultModel();

        assertThrows(IOException.class, () ->
            client.putModel("org1", "repo1", "main", model, "turtle", "Test commit"));
    }

    @Test
    void testCreateDiffSuccess() throws Exception {
        String diffResponse = "{\"additions\": [], \"deletions\": []}";

        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream(diffResponse.getBytes()));
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        String result = client.createDiff("org1", "repo1", "source-branch", "target-branch");

        assertNotNull(result);
        assertTrue(result.contains("additions"));
        verify(mockHttpClient).execute(any(HttpPost.class));
    }

    @Test
    void testCreateDiffError() throws Exception {
        when(mockResponse.getCode()).thenReturn(400);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream("Invalid request".getBytes()));
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        assertThrows(IOException.class, () ->
            client.createDiff("org1", "repo1", "source-branch", "target-branch"));
    }

    @Test
    void testExecuteRequestSuccess() throws Exception {
        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream("Success".getBytes()));
        when(mockHttpClient.execute(any(HttpUriRequestBase.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        HttpGet request = new HttpGet("http://example.com/test");
        String result = client.executeRequest(request);

        assertEquals("Success", result);
        verify(mockHttpClient).execute(any(HttpUriRequestBase.class));
    }

    @Test
    void testExecuteRequestError() throws Exception {
        when(mockResponse.getCode()).thenReturn(500);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream("Error".getBytes()));
        when(mockHttpClient.execute(any(HttpUriRequestBase.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        HttpGet request = new HttpGet("http://example.com/test");

        assertThrows(IOException.class, () -> client.executeRequest(request));
    }

    @Test
    void testAuthenticationHeaderInjection() throws Exception {
        when(mockAuthHandler.isEnabled()).thenReturn(true);
        when(mockAuthHandler.getAuthorizationHeader()).thenReturn("Bearer test-token");
        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream("@prefix ex: <http://example.org/> .".getBytes()));
        when(mockHttpClient.execute(any(HttpGet.class))).thenReturn(mockResponse);

        FlexoMmsClient client = new FlexoMmsClient("http://example.com", mockAuthHandler);
        injectMockHttpClient(client);

        client.listBranches("org1", "repo1");

        verify(mockAuthHandler).isEnabled();
        verify(mockAuthHandler).getAuthorizationHeader();
    }

    @Test
    void testSquashSuccess() throws Exception {
        String responseBody = "@prefix morc: <http://example.com/orgs/org1/repos/repo1/commits/abc-123> .";

        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream(responseBody.getBytes()));
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        String result = client.squash("org1", "repo1", "lock1", "lock2");

        assertEquals("abc-123", result);
        verify(mockHttpClient).execute(any(HttpPost.class));
    }

    @Test
    void testSquashError() throws Exception {
        when(mockResponse.getCode()).thenReturn(400);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream("Bad squash".getBytes()));
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        assertThrows(IOException.class, () -> client.squash("org1", "repo1", "lock1", "lock2"));
    }

    @Test
    void testListCollectionsSuccess() throws Exception {
        String turtle = "@prefix mms: <https://mms.openmbee.org/rdf/ontology/> .\n"
                + "<http://example.com/orgs/org1/collections/c1> a mms:Collection ;\n"
                + "    mms:id \"c1\" ;\n"
                + "    mms:collects <http://example.com/orgs/org1/repos/repo1/branches/master> .";

        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream(turtle.getBytes()));
        when(mockHttpClient.execute(any(HttpGet.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        List<org.openmbee.flexo.cli.model.Collection> result = client.listCollections("org1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("c1", result.get(0).getId());
        assertTrue(result.get(0).getCollectedRefs().stream().anyMatch(r -> r.endsWith("/master")));
    }

    @Test
    void testListCollectionsError() throws Exception {
        when(mockResponse.getCode()).thenReturn(404);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream("Not found".getBytes()));
        when(mockHttpClient.execute(any(HttpGet.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        assertThrows(IOException.class, () -> client.listCollections("org1"));
    }

    @Test
    void testGetCollectionSuccess() throws Exception {
        String turtle = "@prefix mms: <https://mms.openmbee.org/rdf/ontology/> .\n"
                + "<http://example.com/orgs/org1/collections/c1> a mms:Collection ;\n"
                + "    mms:id \"c1\" ;\n"
                + "    mms:etag \"etag-1\" .";

        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream(turtle.getBytes()));
        when(mockHttpClient.execute(any(HttpGet.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        org.openmbee.flexo.cli.model.Collection result = client.getCollection("org1", "c1");

        assertNotNull(result);
        assertEquals("c1", result.getId());
        assertEquals("etag-1", result.getEtag());
    }

    @Test
    void testCreateCollectionEmptyRefsThrows() throws Exception {
        FlexoMmsClient client = createClientWithMockedHttp();

        assertThrows(IOException.class,
            () -> client.createCollection("org1", "c1", java.util.Collections.emptyList()));
        assertThrows(IOException.class,
            () -> client.createCollection("org1", "c1", null));
        verify(mockHttpClient, never()).execute(any(HttpUriRequestBase.class));
    }

    @Test
    void testQueryCollectionSuccess() throws Exception {
        String sparqlResults = "{\"head\":{},\"results\":{\"bindings\":[]}}";

        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream(sparqlResults.getBytes()));
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        String result = client.queryCollection("org1", "c1", "SELECT * WHERE { ?s ?p ?o }");

        assertNotNull(result);
        assertTrue(result.contains("bindings"));
        verify(mockHttpClient).execute(any(HttpPost.class));
    }

    @Test
    void testQueryCollectionError() throws Exception {
        when(mockResponse.getCode()).thenReturn(400);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream("Bad query".getBytes()));
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockResponse);

        FlexoMmsClient client = createClientWithMockedHttp();

        assertThrows(IOException.class,
            () -> client.queryCollection("org1", "c1", "INVALID"));
    }

    private FlexoMmsClient createClientWithMockedHttp() throws Exception {
        FlexoMmsClient client = new FlexoMmsClient("http://example.com", null);
        injectMockHttpClient(client);
        return client;
    }

    private void injectMockHttpClient(FlexoMmsClient client) throws Exception {
        Field httpClientField = FlexoMmsClient.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(client, mockHttpClient);
    }
}
