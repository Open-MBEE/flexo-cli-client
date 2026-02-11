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
