package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaClientException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaServerException;
import gov.nist.oar.distrib.service.rpa.model.RecaptchaResponse;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Helper class for handling reCAPTCHA verification. Contains methods to perform various
 * checks and verifications related to Google reCAPTCHA.
 *
 * This class includes functionality to:
 * - Determine whether reCAPTCHA verification should be performed.
 * - Verify the reCAPTCHA response with Google's reCAPTCHA service.
 * - Perform a sanity check on the response.
 */
public class RecaptchaVerificationHelper {

    private static final String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    private static final Pattern RECAPTCHA_RESPONSE_PATTERN = Pattern.compile("[A-Za-z0-9_-]+"); // Define your regex

    private RPAConfiguration rpaConfiguration;

    public RecaptchaVerificationHelper(RPAConfiguration config) {
        this.rpaConfiguration = config;
    }

    public void setRpaConfiguration(RPAConfiguration rpaConfiguration) {
        this.rpaConfiguration = rpaConfiguration;
    }

    /**
     * Determines whether reCAPTCHA verification should be performed based on the authorization header.
     *
     * @param authorizationHeader The authorization header from the request.
     * @return true if reCAPTCHA verification should be performed, false otherwise.
     */
    public boolean shouldVerifyRecaptcha(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            return token.isEmpty() || !rpaConfiguration.isAuthorized(token);
        }
        return true;
    }

    /**
     * Verifies the Google reCAPTCHA using the given response string returned by the "I am not a Robot" widget.
     *
     * @param response The response string from the Google reCAPTCHA widget.
     * @return RecaptchaResponse The Google response for the validation request.
     * @throws RecaptchaClientException If the response contains invalid characters or validation failed due to client error.
     * @throws RecaptchaServerException If there was an error during the validation such as an unknown error.
     */
    public RecaptchaResponse verifyRecaptcha(String response) throws RecaptchaClientException {
        if (!responseSanityCheck(response)) {
            throw new RecaptchaClientException("Response contains invalid characters");
        }

        RestTemplate restTemplate = new RestTemplate();
        URI uri = null;
        try {
            uri = new URIBuilder(RECAPTCHA_VERIFY_URL)
                    .setParameter("secret", rpaConfiguration.getRecaptchaSecret())
                    .setParameter("response", response)
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        ResponseEntity<RecaptchaResponse> recaptchaResponseEntity = restTemplate.getForEntity(uri, RecaptchaResponse.class);
        RecaptchaResponse recaptchaResponse = recaptchaResponseEntity.getBody();

        if (recaptchaResponse != null && !recaptchaResponse.isSuccess()) {
            if (recaptchaResponse.hasClientError())
                throw new RecaptchaClientException("reCAPTCHA validation failed due to client error: " +
                        Arrays.toString(recaptchaResponse.getErrorCodesAsStrings()));
            throw new RecaptchaServerException("reCAPTCHA validation failed due to unknown error");
        }

        return recaptchaResponse;
    }

    /**
     * Checks if the reCAPTCHA response is valid.
     *
     * @param response The reCAPTCHA response string.
     * @return true if the response is valid, false otherwise.
     */
    private boolean responseSanityCheck(String response) {
        return response != null && !response.isEmpty() && RECAPTCHA_RESPONSE_PATTERN.matcher(response).matches();
    }
}

