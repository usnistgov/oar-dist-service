package gov.nist.oar.distrib.service.rpa;

import static gov.nist.oar.distrib.service.rpa.RecaptchaHelper.RECAPTCHA_VERIFY_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaClientException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaServerException;
import gov.nist.oar.distrib.service.rpa.model.RecaptchaResponse;

public class RecaptchaHelperTest {

    private static final String RECAPTCHA_SECRET = "secret";
    private static final String RECAPTCHA_RESPONSE = "response";

    @Mock
    private HttpURLConnection mockConnection;

    @Mock
    private HttpURLConnectionFactory mockConnectionFactory;

    private RecaptchaHelper recaptchaHelper;
    private AutoCloseable closeable;  // For closing open mocks

    @BeforeEach
    public void setup() {
        closeable = MockitoAnnotations.openMocks(this);  // Use openMocks instead of initMocks
        recaptchaHelper = new RecaptchaHelper();
        recaptchaHelper.setHttpURLConnectionFactory(mockConnectionFactory);
    }

    @AfterEach
    public void tearDown() throws Exception {
        closeable.close();  // Ensure mocks are closed after each test
    }

    private String createTestUri() {
        // Build the URI
        String uri = null;
        try {
            uri = new URIBuilder(RECAPTCHA_VERIFY_URL)
                    .setParameter("secret", RECAPTCHA_SECRET)
                    .setParameter("response", RECAPTCHA_RESPONSE)
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return uri;
    }

    @Test
    public void testVerifyRecaptcha_validResponse_success() throws Exception {
        String expectedUri = createTestUri();
        // Convert the expected URI string to a URL object
        URL expectedURL = new URI(expectedUri).toURL();  // Use URI to create a URL instead of using the deprecated URL(String) constructor

        // Mock the response from the Google reCAPTCHA service
        when(mockConnectionFactory.createHttpURLConnection(expectedURL)).thenReturn(mockConnection);

        // Set expected dummy response
        String expectedResponseData = "{" +
                "  \"success\": true," +
                "  \"challenge_ts\": \"2022-04-07T10:42:41Z\"," +
                "  \"hostname\": \"example.com\"" +
                "}";
        // Create an input stream with the dummy data
        InputStream inputStream = new ByteArrayInputStream(expectedResponseData.getBytes(StandardCharsets.UTF_8));
        when(mockConnection.getInputStream()).thenReturn(inputStream);
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);

        // Call the method under test
        RecaptchaResponse actualResponse = recaptchaHelper.verifyRecaptcha(RECAPTCHA_SECRET, RECAPTCHA_RESPONSE);

        // Read from the input stream and check if response is correct
        try (BufferedReader in = new BufferedReader(new InputStreamReader(mockConnection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            ObjectMapper mapper = new ObjectMapper();
            RecaptchaResponse expectedResponse = mapper.readValue(expectedResponseData, RecaptchaResponse.class);
            assertEquals(expectedResponse.isSuccess(), actualResponse.isSuccess());
        }

        // Verify that the connection was closed
        verify(mockConnection).disconnect();
    }

    @Test
    public void testVerifyRecaptcha_failure_invalidResponse() throws Exception {
        String expectedUri = createTestUri();
        URL expectedURL = new URI(expectedUri).toURL();  // Use URI to create a URL instead of using deprecated URL(String)

        // Mock the response from the Google reCAPTCHA service
        when(mockConnectionFactory.createHttpURLConnection(expectedURL)).thenReturn(mockConnection);

        // Set the response from Google reCAPTCHA service
        String errorResponse = "{\"success\": false, \"error-codes\": [\"invalid-input-response\"]}";
        InputStream errorStream = new ByteArrayInputStream(errorResponse.getBytes(StandardCharsets.UTF_8));
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConnection.getInputStream()).thenReturn(errorStream);

        try {
            recaptchaHelper.verifyRecaptcha(RECAPTCHA_SECRET, RECAPTCHA_RESPONSE);
            fail("Expected InvalidRecaptchaException to be thrown");
        } catch (RecaptchaClientException e) {
            assertEquals("reCAPTCHA validation failed due to client error: [InvalidResponse]", e.getMessage());
        }

        // Verify that the connection was closed
        verify(mockConnection).disconnect();
    }

    @Test
    public void testVerifyRecaptcha_failure_missingResponse() throws Exception {
        String expectedUri = createTestUri();
        URL expectedURL = new URI(expectedUri).toURL();  // Use URI to create a URL instead of using deprecated URL(String)

        // Mock the response from the Google reCAPTCHA service
        when(mockConnectionFactory.createHttpURLConnection(expectedURL)).thenReturn(mockConnection);

        // Set the response from Google reCAPTCHA service
        String errorResponse = "{\"success\": false, \"error-codes\": [\"missing-input-response\"]}";
        InputStream errorStream = new ByteArrayInputStream(errorResponse.getBytes(StandardCharsets.UTF_8));
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConnection.getInputStream()).thenReturn(errorStream);

        try {
            recaptchaHelper.verifyRecaptcha(RECAPTCHA_SECRET, RECAPTCHA_RESPONSE);
            fail("Expected InvalidRecaptchaException to be thrown");
        } catch (RecaptchaClientException e) {
            assertEquals("reCAPTCHA validation failed due to client error: [MissingResponse]", e.getMessage());
        }

        // Verify that the connection was closed
        verify(mockConnection).disconnect();
    }

    @Test
    public void testVerifyRecaptcha_failure_badRequest() throws Exception {
        String expectedUri = createTestUri();
        URL expectedURL = new URI(expectedUri).toURL();  // Use URI to create a URL instead of using deprecated URL(String)

        // Mock the response from the Google reCAPTCHA service
        when(mockConnectionFactory.createHttpURLConnection(expectedURL)).thenReturn(mockConnection);

        // Set the response from Google reCAPTCHA service
        String errorResponse = "{\"success\": false, \"error-codes\": []}";
        InputStream errorStream = new ByteArrayInputStream(errorResponse.getBytes(StandardCharsets.UTF_8));
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);
        when(mockConnection.getInputStream()).thenReturn(errorStream);
        when(mockConnection.getResponseMessage()).thenReturn("Bad Request");

        try {
            recaptchaHelper.verifyRecaptcha(RECAPTCHA_SECRET, RECAPTCHA_RESPONSE);
            fail("Expected InvalidRecaptchaException to be thrown");
        } catch (RecaptchaServerException e) {
            assertEquals("Error response from Google reCAPTCHA service: Bad Request", e.getMessage());
        }

        // Verify that the connection was closed
        verify(mockConnection).disconnect();
    }
}
