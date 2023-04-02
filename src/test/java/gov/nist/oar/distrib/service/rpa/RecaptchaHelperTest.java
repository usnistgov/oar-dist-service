package gov.nist.oar.distrib.service.rpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.rpa.exceptions.ClientRecaptchaException;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRecaptchaException;
import gov.nist.oar.distrib.service.rpa.exceptions.ServerRecaptchaException;
import gov.nist.oar.distrib.service.rpa.model.RecaptchaResponse;
import org.apache.http.client.utils.URIBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;

import static gov.nist.oar.distrib.service.rpa.RecaptchaHelper.RECAPTCHA_VERIFY_URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RecaptchaHelperTest {

    private String MOCK_RECAPTCHA_RESPONSE = "03AGdBq24RJhXnXW1j8MvTtJQwL3qDdWVEiv8vZBaz0M4dkD4znXmC_2-uJjgSx0SdSEHkCP8W7Pl" +
            "jJb89--TlTsz9Xj8AeTt_NvEqIsNQDd3qJ8-KjNzDrhjrJjE3e9qRGcB7yIdYv_7JqaCnCFxZ8IwpZu45GRZXf-NnH9XDWoU6etpww6" +
            "YrU64rEseKj5c5a5Twli--Umea0KjX9UmxG6m0Y6M0rHbhEjKc5Q5F5ETdwOs3KbN3X9-0N6IdNvLVwK7Gx-Jx48DCkh7Vz-UOZtG-9" +
            "tT9TtjLG1bmTvlvZtr2rxVDLf3q8-AjWZo_qA4B4d-4bP8y2gJyK59C92n1xZfEZ_y00ilzdtZvJf30i9puHjKU-zY6U-Pr6z5oMw5r" +
            "6F5Z5g2Z5qKqp9i2EjmrYI-8L7VdDp-Vwz7iKnIvZrlz7VpF-1cEYmsRPUwW_hUtJBoPJG4_MyMj2MCx4GLz4-ZqKjNNC0HdZaW8SBv" +
            "2oAHfnMjJiNPV7bfW8AYvT9z-1uiFzhOJjPwIQOvATV50Dd-HmP2yO4GLGxq3Sc4veeH5Q5f5rf5-5q5r74th6U96Af6U9uy6U9vMjK" +
            "ZrdYlZteVpt1_zlB-6gLeE";
    private static final String RECAPTCHA_SECRET = "secret";
    private static final String RECAPTCHA_RESPONSE = "response";

    @Mock
    private HttpURLConnection mockConnection;

    @Mock
    private HttpURLConnectionFactory mockConnectionFactory;


    private RecaptchaHelper recaptchaHelper;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        recaptchaHelper = new RecaptchaHelper();
        recaptchaHelper.setHttpURLConnectionFactory(mockConnectionFactory);
    }

    private String createTestUri() {
        // Build the URI
        String uri = null;
        try {
            uri = new URIBuilder(RECAPTCHA_VERIFY_URL)
                    .setParameter("secret", RECAPTCHA_SECRET)
                    .setParameter("response", RECAPTCHA_RESPONSE)
                    .build()
                    .toString();;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return uri;
    }
    @Test
    public void testVerifyRecaptcha_validResponse_success() throws Exception {
        String expectedUri = createTestUri();
        // Mock the response from the Google reCAPTCHA service
        when(mockConnectionFactory.createHttpURLConnection(new URL(expectedUri))).thenReturn(mockConnection);
        // Set expected dummy response
        String expectedResponseData = "{" +
                "  \"success\": true," +
                "  \"challenge_ts\": \"2022-04-07T10:42:41Z\"," +
                "  \"hostname\": \"example.com\"" +
                "}";
        // Create an input stream with the dummy data
        InputStream inputStream = new ByteArrayInputStream(expectedResponseData.getBytes());
        // When getInputStream() is called on the mockConnection, return the dummy input stream
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
        // Mock the response from the Google reCAPTCHA service
        when(mockConnectionFactory.createHttpURLConnection(new URL(expectedUri))).thenReturn(mockConnection);

        // Set the response from Google reCAPTCHA service
        String errorResponse = "{\"success\": false, \"error-codes\": [\"invalid-input-response\"]}";
        InputStream errorStream = new ByteArrayInputStream(errorResponse.getBytes());
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConnection.getInputStream()).thenReturn(errorStream);

         //
        try {
            recaptchaHelper.verifyRecaptcha(RECAPTCHA_SECRET, RECAPTCHA_RESPONSE);
            fail("Expected InvalidRecaptchaException to be thrown");
        } catch (ClientRecaptchaException e) {
            assertEquals("reCAPTCHA validation failed due to client error: [InvalidResponse]", e.getMessage());
        }
        // Verify that the connection was closed
        verify(mockConnection).disconnect();
    }

    @Test
    public void testVerifyRecaptcha_failure_missingResponse() throws Exception {

        String expectedUri = createTestUri();
        // Mock the response from the Google reCAPTCHA service
        when(mockConnectionFactory.createHttpURLConnection(new URL(expectedUri))).thenReturn(mockConnection);

        // Set the response from Google reCAPTCHA service
        String errorResponse = "{\"success\": false, \"error-codes\": [\"missing-input-response\"]}";
        InputStream errorStream = new ByteArrayInputStream(errorResponse.getBytes());
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConnection.getInputStream()).thenReturn(errorStream);

        //
        try {
            recaptchaHelper.verifyRecaptcha(RECAPTCHA_SECRET, RECAPTCHA_RESPONSE);
            fail("Expected InvalidRecaptchaException to be thrown");
        } catch (ClientRecaptchaException e) {
            assertEquals("reCAPTCHA validation failed due to client error: [MissingResponse]", e.getMessage());
        }
        // Verify that the connection was closed
        verify(mockConnection).disconnect();
    }

    @Test
    public void testVerifyRecaptcha_failure_unknownError() throws Exception {

        String expectedUri = createTestUri();
        // Mock the response from the Google reCAPTCHA service
        when(mockConnectionFactory.createHttpURLConnection(new URL(expectedUri))).thenReturn(mockConnection);

        // Set the response from Google reCAPTCHA service
        String errorResponse = "{\"success\": false, \"error-codes\": []}";
        InputStream errorStream = new ByteArrayInputStream(errorResponse.getBytes());
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConnection.getInputStream()).thenReturn(errorStream);

        //
        try {
            recaptchaHelper.verifyRecaptcha(RECAPTCHA_SECRET, RECAPTCHA_RESPONSE);
            fail("Expected InvalidRecaptchaException to be thrown");
        } catch (ServerRecaptchaException e) {
            assertEquals("reCAPTCHA validation failed due to unknown error", e.getMessage());
        }
        // Verify that the connection was closed
        verify(mockConnection).disconnect();
    }

    @Test
    public void testVerifyRecaptcha_failure_badRequest() throws Exception {

        String expectedUri = createTestUri();
        // Mock the response from the Google reCAPTCHA service
        when(mockConnectionFactory.createHttpURLConnection(new URL(expectedUri))).thenReturn(mockConnection);

        // Set the response from Google reCAPTCHA service
        String errorResponse = "{\"success\": false, \"error-codes\": []}";
        InputStream errorStream = new ByteArrayInputStream(errorResponse.getBytes());
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);
        when(mockConnection.getInputStream()).thenReturn(errorStream);
        when(mockConnection.getResponseMessage()).thenReturn("Bad Request");

        //
        try {
            recaptchaHelper.verifyRecaptcha(RECAPTCHA_SECRET, RECAPTCHA_RESPONSE);
            fail("Expected InvalidRecaptchaException to be thrown");
        } catch (ServerRecaptchaException e) {
            assertEquals("Error response from Google reCAPTCHA service: Bad Request", e.getMessage());
        }
        // Verify that the connection was closed
        verify(mockConnection).disconnect();
    }


}
