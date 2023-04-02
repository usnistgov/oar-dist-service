package gov.nist.oar.distrib.service.rpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRecaptchaException;
import gov.nist.oar.distrib.service.rpa.model.RecaptchaResponse;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.regex.Pattern;


/**
 * A helper class that uses Google reCAPTCHA to verify user responses to challenges.
 */
public class RecaptchaVerifier {

    // Pattern to check whether the response string contains only valid characters
    public static Pattern RECAPTCHA_RESPONSE_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");
    // Endpoint for the Google reCAPTCHA verification service
    public static final String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";


    // Check if recaptcha response is valid
    private boolean responseSanityCheck(String response) {
        return response != null && !response.isEmpty() && RECAPTCHA_RESPONSE_PATTERN.matcher(response).matches();
    }

    /**
     * Verify the Google reCaptcha using the given response string that was returned by the "I am not a Robot" widget.
     *
     * @param secret the secret used to authenticate requests sent to the Google reCAPTCHA verification service.
     * @param response The response string from the Google reCAPTCHA widget.
     * @throws InvalidReCaptchaException If the response contains invalid characters, or validation failed.
     * @throws IOException If there was an error sending the HTTP request or receiving the response from the Google reCAPTCHA service.
     */
    public void verifyRecaptcha(String secret, String response) throws InvalidRecaptchaException, IOException {
        // Response sanity check
        if (!responseSanityCheck(response)) {
            throw new InvalidRecaptchaException("Response contains invalid characters");
        }

        // Build the URI
        String uri = null;
        try {
            uri = new URIBuilder(RECAPTCHA_VERIFY_URL)
                    .setParameter("secret", secret)
                    .setParameter("response", response)
                    .build()
                    .toString();;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        // Create connection and send the request
        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(uri);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                    // Parse the response and check if the reCAPTCHA was successfully validated
                    RecaptchaResponse recaptchaResponse = new ObjectMapper().readValue(responseBuilder.toString(), RecaptchaResponse.class);
                    if (!recaptchaResponse.isSuccess()) {
                        if (recaptchaResponse.hasClientError())
                            throw new InvalidRecaptchaException("reCAPTCHA validation failed due to client error: " +
                                    Arrays.toString(recaptchaResponse.getErrorCodes()));
                        throw new InvalidRecaptchaException("reCAPTCHA validation failed due to unknown error");

                    }
                }
            } else {
                // Handle any other error response
                throw new IOException("Error response from Google reCAPTCHA service: " + connection.getResponseMessage());
            }
        } catch (IOException e) {
            throw new IOException("Error processing Google reCAPTCHA response: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
