package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpRPADatasetCacherTest {
    private HttpRPADatasetCacher httpRPADatasetCacher;
    private RPAConfiguration rpaConfiguration;
    HttpClient httpClient;
    @Captor
    private ArgumentCaptor<HttpPut> httpPutCaptor;
    String datasetId;

    private static final String BASE_URL = "https://oardev.nist.gov";
    private static final String CACHE_PATH = "/od/ds/rpa/cache/";


    @Before
    public void setUp() {
        rpaConfiguration = mock(RPAConfiguration.class);
        when(rpaConfiguration.getPdrCachingUrl()).thenReturn(BASE_URL);

        httpRPADatasetCacher = new HttpRPADatasetCacher(rpaConfiguration);
        httpClient = mock(HttpClient.class);
        httpRPADatasetCacher.setHttpClient(httpClient);

        datasetId = "mds2-2909";
    }

    @Test
    public void testCache_success() throws Exception {
        String expectedResponse = "randomId123";

        // Arrange
        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(response.getStatusLine()).thenReturn(statusLine);

        HttpEntity entity = mock(HttpEntity.class);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream(expectedResponse.getBytes()));
        when(response.getEntity()).thenReturn(entity);
        when(httpClient.execute(any(HttpPut.class))).thenReturn(response);

        String result = httpRPADatasetCacher.cache(datasetId);

        // Assert
        assertEquals(expectedResponse, result);

        // Verify the URL used in the request
        verify(httpClient).execute(httpPutCaptor.capture());
        HttpPut httpPutUsedInRequest = httpPutCaptor.getValue();
        String urlUsedInRequest = httpPutUsedInRequest.getURI().toString();
        String expectedUrl = BASE_URL + CACHE_PATH + datasetId;
        assertEquals(expectedUrl, urlUsedInRequest);

        // Verify the HTTP method used (should be PUT here)
        assertEquals(HttpPut.METHOD_NAME, httpPutUsedInRequest.getMethod());

        // Verify the request headers (if needed)

        // Verify the request body is empty
        HttpEntity requestEntity = httpPutUsedInRequest.getEntity();
        assertNull(requestEntity);
    }

    @Test
    public void testCache_requestProcessingException() throws Exception {
        // httpClient throws an IOException
        when(httpClient.execute(any(HttpPut.class))).thenThrow(new IOException("IOException message example"));

        try {
            httpRPADatasetCacher.cache(datasetId);
            fail("Expected RequestProcessingException to be thrown");
        } catch (RequestProcessingException e) {
            assertThat(e.getMessage(), containsString("Failed to cache dataset " + datasetId));
            assertThat(e.getMessage(), containsString("IOException message example"));

            // Verify httpClient.execute() was called
            verify(httpClient).execute(httpPutCaptor.capture());
            HttpPut httpPutUsedInRequest = httpPutCaptor.getValue();

            // Verify the URL used in the request
            String urlUsedInRequest = httpPutUsedInRequest.getURI().toString();
            String expectedUrl = BASE_URL + CACHE_PATH + datasetId;
            assertEquals(expectedUrl, urlUsedInRequest);
        }
    }

    @Test
    public void testCache_failedStatus() throws Exception {
        String reason = "Not Found";

        // httpResponse has a non-OK status
        HttpResponse httpResponse = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_NOT_FOUND);
        when(statusLine.getReasonPhrase()).thenReturn(reason);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);

        when(httpClient.execute(any(HttpPut.class))).thenReturn(httpResponse);

        try {
            httpRPADatasetCacher.cache(datasetId);
            fail("Expected RequestProcessingException to be thrown");
        } catch (RequestProcessingException e) {
            assertThat(e.getMessage(), containsString("Failed to cache dataset " + datasetId));
            assertThat(e.getMessage(), containsString(reason));

            // Verify httpClient.execute() was called
            verify(httpClient).execute(httpPutCaptor.capture());
            HttpPut httpPutUsedInRequest = httpPutCaptor.getValue();

            // Verify the URL used in the request
            String urlUsedInRequest = httpPutUsedInRequest.getURI().toString();
            String expectedUrl = BASE_URL + CACHE_PATH + datasetId;
            assertEquals(expectedUrl, urlUsedInRequest);
        }
    }

}
