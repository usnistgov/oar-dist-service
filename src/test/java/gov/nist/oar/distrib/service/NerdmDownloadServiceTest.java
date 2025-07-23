package gov.nist.oar.distrib.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;

public class NerdmDownloadServiceTest {

    /**
     * Tests that fetchNerdm returns a valid JSON response when the HTTP request is
     * successful.
     */
    @Test
    public void testFetchNerdm_success() throws Exception {
        CloseableHttpClient mockClient = Mockito.mock(CloseableHttpClient.class);
        String json = "{\"components\": [{\"name\": \"file1\"}]}";

        when(mockClient.execute(
                any(HttpUriRequest.class),
                ArgumentMatchers.<HttpClientResponseHandler<JsonNode>>any())).thenAnswer(invocation -> {
                    HttpClientResponseHandler<JsonNode> handler = invocation.getArgument(1);
                    return handler.handleResponse(new BasicClassicHttpResponse(200) {
                        {
                            setEntity(new StringEntity(json));
                        }
                    });
                });

        NerdmDownloadService service = new NerdmDownloadService("http://mockserver", mockClient);
        JsonNode result = service.fetchNerdm("foo123");

        assertNotNull(result);
        assertTrue(result.has("components"));
        assertEquals("file1", result.get("components").get(0).get("name").asText());
    }

    /**
     * Tests that fetchNerdm throws an IOException if the HTTP request results in
     * an HTTP error response. The error message is expected to contain the HTTP
     * status code.
     */
    @Test
    public void testFetchNerdm_httpError() throws Exception {
        CloseableHttpClient mockClient = Mockito.mock(CloseableHttpClient.class);

        when(mockClient.execute(
                any(HttpUriRequest.class),
                ArgumentMatchers.<HttpClientResponseHandler<JsonNode>>any())).thenAnswer(invocation -> {
                    HttpClientResponseHandler<JsonNode> handler = invocation.getArgument(1);
                    return handler.handleResponse(new BasicClassicHttpResponse(404));
                });

        NerdmDownloadService service = new NerdmDownloadService("http://mockserver", mockClient);

        IOException ex = assertThrows(IOException.class, () -> service.fetchNerdm("doesnotexist"));
        assertTrue(ex.getMessage().contains("HTTP 404"));
    }

    /**
     * Tests that fetchNerdm caches the successful HTTP response.
     * This test verifies that when fetchNerdm is called with the same dataset ID
     * multiple times, the HTTP request is only executed once, and the subsequent
     * calls retrieve the data from the cache.
     */
    @Test
    public void testFetchNerdm_cachesSuccessfulResponse() throws Exception {
        CloseableHttpClient mockClient = Mockito.mock(CloseableHttpClient.class);
        String json = "{\"components\": [{\"name\": \"cachedFile\"}]}";

        // Mock behavior of HTTP client
        // Whenever execute() is called, no matter the request, pretend to send 
        // a successful response with the fake JSON body
        when(mockClient.execute(
            any(HttpUriRequest.class),
            ArgumentMatchers.<HttpClientResponseHandler<JsonNode>>any()))
        .thenAnswer(invocation -> {
            HttpClientResponseHandler<JsonNode> handler = invocation.getArgument(1);
            return handler.handleResponse(new BasicClassicHttpResponse(200) {{
                setEntity(new StringEntity(json));
            }});
        });

        NerdmDownloadService service = new NerdmDownloadService("http://mockserver", mockClient);

        JsonNode first = service.fetchNerdm("ds1");
        JsonNode second = service.fetchNerdm("ds1"); // Should hit the cache

        assertEquals("cachedFile", first.get("components").get(0).get("name").asText());
        assertEquals("cachedFile", second.get("components").get(0).get("name").asText());

        // Ensure only one actual HTTP call was made
        Mockito.verify(mockClient, Mockito.times(1)).execute(
                any(HttpUriRequest.class),
                ArgumentMatchers.<HttpClientResponseHandler<JsonNode>>any());
    }

}