package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.service.rpa.exceptions.InvalidFormInputException;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import gov.nist.oar.distrib.service.rpa.model.UserInfo;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;



/**
 * The RequestSanitizer class is responsible for sanitizing and validating a UserInfoWrapper object,
 * ensuring that it doesn't contain any malicious HTML, and does not contain any unsupported parameters.
 */
public class RequestSanitizer {
    // Logger
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestSanitizer.class);

    /**
     * Variable containing a list of allowed HTML tags and attributes used to sanitize input strings.
     * Change this according to needs.
     * This for example will remove the `a`, and `script` tags from the list of allowed tags.
     */
    private static final Safelist BASIC_SAFELIST = Safelist.basic().removeTags("a", "script");

    private UrlValidator urlValidator= new UrlValidator();

    private static final Set<String> SUPPORTED_PARAMS = new HashSet<>(Arrays.asList(
            "fullName",
            "organization",
            "email",
            "receiveEmails",
            "country",
            "approvalStatus",
            "productTitle",
            "subject",
            "description"
    ));

    /**
     * Sanitizes and validates the given UserInfoWrapper object.
     *
     * @param userInfoWrapper The UserInfoWrapper object to be sanitized and validated.
     * @throws InvalidRequestException If the request body is null, UserInfo is null, contains unsupported parameters,
     *                                or the known fields are invalid.
     */
    public void sanitizeAndValidate(UserInfoWrapper userInfoWrapper) throws InvalidRequestException {
        if (userInfoWrapper == null) {
            throw new InvalidRequestException("Request body cannot be null.");
        }

        // Check for unsupported parameters in UserInfoWrapper
        checkForUnsupportedParams(userInfoWrapper);

        UserInfo userInfo = userInfoWrapper.getUserInfo();
        if (userInfo == null) {
            throw new InvalidRequestException("UserInfo cannot be null.");
        }

        // Check for unsupported parameters in UserInfo
        checkForUnsupportedParams(userInfo);

        // Sanitize known fields in UserInfo
        try {
            sanitizeKnownFields(userInfo);
        } catch (InvalidFormInputException e) {
            throw new InvalidRequestException(e.getMessage());
        }

        // Validate sanitized fields in UserInfo
        validateKnownFields(userInfo);
    }

    private void checkForUnsupportedParams(UserInfoWrapper userInfoWrapper) {
        Map<String, Object> unknownProperties = userInfoWrapper.getUnknownProperties();
        List<String> unsupportedParams = new ArrayList<>();

        for (String paramName : unknownProperties.keySet()) {
            if (!SUPPORTED_PARAMS.contains(paramName)) {
                unsupportedParams.add(paramName);
            }
        }

        if (!unsupportedParams.isEmpty()) {
            LOGGER.debug("Request payload contains unsupported parameters in UserInfoWrapper: " + unsupportedParams);
            throw new InvalidRequestException("Request payload contains unsupported parameters: " + unsupportedParams);
        }
    }

    private void checkForUnsupportedParams(UserInfo userInfo) {
        Map<String, Object> unknownProperties = userInfo.getUnknownProperties();
        List<String> unsupportedParams = new ArrayList<>();

        for (String paramName : unknownProperties.keySet()) {
            if (!SUPPORTED_PARAMS.contains(paramName)) {
                unsupportedParams.add(paramName);
            }
        }

        if (!unsupportedParams.isEmpty()) {
            LOGGER.debug("Request payload contains unsupported parameters in UserInfo: " + unsupportedParams);
            throw new InvalidRequestException("Request payload contains unsupported parameters: " + unsupportedParams);
        }
    }

    /**
     * Sanitizes the known fields in the UserInfo object by removing any potentially unsafe HTML tags or attributes,
     * and checks for the presence of URLs.
     *
     * @param userInfo The UserInfo object to be sanitized
     * @throws InvalidFormInputException if a URL is detected in any of the fields
     */
    private void sanitizeKnownFields(UserInfo userInfo) throws InvalidFormInputException {
        if (userInfo.getFullName() != null) {
            userInfo.setFullName(sanitizeHtml(userInfo.getFullName()));
        }

        if (userInfo.getOrganization() != null) {
            userInfo.setOrganization(sanitizeHtml(userInfo.getOrganization()));
        }

        if (userInfo.getEmail() != null) {
            userInfo.setEmail(sanitizeHtml(userInfo.getEmail()));
        }

        if (userInfo.getReceiveEmails() != null) {
            userInfo.setReceiveEmails(sanitizeHtml(userInfo.getReceiveEmails()));
        }

        if (userInfo.getCountry() != null) {
            userInfo.setCountry(sanitizeHtml(userInfo.getCountry()));
        }

        if (userInfo.getApprovalStatus() != null) {
            userInfo.setApprovalStatus(sanitizeHtml(userInfo.getApprovalStatus()));
        }

        if (userInfo.getProductTitle() != null) {
            userInfo.setProductTitle(sanitizeHtml(userInfo.getProductTitle()));
        }

        if (userInfo.getSubject() != null) {
            userInfo.setSubject(sanitizeHtml(userInfo.getSubject()));
        }

        if (userInfo.getDescription() != null) {
            userInfo.setDescription(sanitizeHtml(userInfo.getDescription()));
        }
    }

    /**
     * Sanitizes the HTML input by scrubbing out any tags and attributes that are not explicitly allowed.
     * This will help prevent XSS attacks by sanitizing any user-provided HTML input and ensuring that
     * only safe HTML tags and attributes are allowed in the output.
     *
     * The safe HTML tags are provided by the {@link Safelist}.
     *
     * @param input the HTML input to sanitize
     * @return the sanitized input
     */
    private String sanitizeHtml(String input) throws InvalidFormInputException {
        // Jsoup removes the newline character (\n) by default from the HTML text
        // To prevent that, disable pretty-print
        Document.OutputSettings outputSettings = new Document.OutputSettings();
        outputSettings.prettyPrint(false);

        // Use the Jsoup.clean method to sanitize the input string
        String output = Jsoup.clean(input, "", BASIC_SAFELIST, outputSettings);
        // Check if form input contains URL
        if (containsURL(output)) {
            // Form input contains URL
            LOGGER.debug("URL detected in the input: " + output);
            throw new InvalidFormInputException("URL detected in the input: " + output);
        }
        return output;
    }

    /**
     * Checks if the given input string contains a valid URL within some text.
     *
     * @param input the string to check for URLs
     * @return true if a valid URL is found within the text, false otherwise
     */
    private boolean containsURL(String input) {
        // Split the input by whitespace to separate individual words
        String[] words = input.split("\\s+");

        // Check each word if it is a valid URL
        for (String word : words) {
            if (urlValidator.isValid(word)) {
                return true; // Found a valid URL within the text
            }
        }
        return false; // No valid URL found
    }

    /**
     * Checks if the known fields in the UserInfo object are valid by ensuring they are not null, and not empty.
     * We only validate the fields that required.
     * If any field fails the validation, an InvalidRequestException is thrown.
     *
     * @param userInfo
     */
    private void validateKnownFields(UserInfo userInfo) {
        // add a field to this map to make it required for validation
        Map<String, String> requiredFields = new HashMap<>();
        requiredFields.put("fullName", userInfo.getFullName());
        requiredFields.put("organization", userInfo.getOrganization());
        requiredFields.put("email", userInfo.getEmail());
        requiredFields.put("country", userInfo.getCountry());
        requiredFields.put("productTitle", userInfo.getProductTitle());
        requiredFields.put("subject", userInfo.getSubject());
        requiredFields.put("description", userInfo.getDescription());

        for (Map.Entry<String, String> entry : requiredFields.entrySet()) {
            String fieldName = entry.getKey();
            String fieldValue = entry.getValue();

            if (fieldValue == null || fieldValue.trim().isEmpty()) {
                throw new InvalidRequestException(fieldName + " cannot be blank.");
            }
        }
    }

}
