package gov.nist.oar.distrib.service.rpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.rpa.model.RecaptchaResponse;
import org.apache.http.client.utils.URIBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class RecaptchaVerifierTest {

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
    URIBuilder uriBuilder;

    @Mock
    private HttpURLConnection mockConnection;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RecaptchaVerifier recaptchaVerifier;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testVerifyRecaptcha_validResponse_success() throws Exception {
        // Arrange
        // Dummy recaptchaResponse
        String expectedResponseData = "{\"success\":true}";

        InputStream inputStream = new ByteArrayInputStream(
                new ObjectMapper().writeValueAsString(expectedResponseData).getBytes());
        // When getInputStream() is called on the mockConnection, return the dummy input stream
        when(mockConnection.getInputStream()).thenReturn(inputStream);
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);

        // Read from the input stream and check if response is correct
        try (BufferedReader in = new BufferedReader(new InputStreamReader(mockConnection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            ObjectMapper mapper = new ObjectMapper();
            RecaptchaResponse expectedToken = mapper.readValue(expectedResponseData, RecaptchaResponse.class);
            assertEquals(expectedToken.getAccessToken(), actualToken.getAccessToken());
            assertEquals(expectedToken.getInstanceUrl(), actualToken.getInstanceUrl());
        }
        // Act
        recaptchaHelper.verifyRecaptcha(RECAPTCHA_SECRET, RECAPTCHA_RESPONSE);

        // Assert
        // Verify that no exception is thrown and the verifyRecaptcha() method returns successfully
    }

//    @Test
//    public void testVerifyRecaptcha_unsuccessfulValidation_failure() throws Exception {
//
//        // Build the URI
//        String uri = new URIBuilder(RECAPTCHA_VERIFY_URL)
//                .setParameter("secret", RECAPTCHA_SECRET)
//                .setParameter("response", RECAPTCHA_RESPONSE)
//                .build()
//                .toString();
//
//        // Create the mock response from Google reCAPTCHA service
//        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
//
//        // Set the response from Google reCAPTCHA service
//        String errorResponse = "{\"success\": false, \"error-codes\": [\"invalid-input-response\"]}";
//        InputStream errorStream = new ByteArrayInputStream(errorResponse.getBytes());
//        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
//        when(mockConnection.getInputStream()).thenReturn(errorStream);
//        when(mockConnection.getResponseMessage()).thenReturn("Error response from Google reCAPTCHA service");
//
//        // Call the method to be tested
//        RecaptchaResponse mockResponse = new RecaptchaResponse();
//        mockResponse.setSuccess(false);
//        mockResponse.setErrorCodes(new RecaptchaResponse.ErrorCode[]{RecaptchaResponse.ErrorCode.InvalidResponse});
//        RecaptchaHelper.verifyRecaptcha(RECAPTCHA_SECRET, RECAPTCHA_RESPONSE);
//        //
//        try {
//            recaptchaHelper.verifyRecaptcha(RECAPTCHA_SECRET, RECAPTCHA_RESPONSE);
//            fail();
//        } catch (InvalidRecaptchaException e) {
//            assertEquals("reCAPTCHA validation failed due to client error: [invalid-input-response]", e.getMessage());
//        }
//    }

}
