package gov.nist.oar.distrib.service.rpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRecaptchaException;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaClientException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaServerException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaVerificationFailedException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecordNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.service.rpa.model.JWTToken;
import gov.nist.oar.distrib.service.rpa.model.RecaptchaResponse;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.service.rpa.model.RecordStatus;
import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * An implementation of the RPARequestHandlerService that uses HttpURLConnection to send HTTP requests and
 * receives responses from the Salesforce service.
 */
public class HttpURLConnectionRPARequestHandlerService implements IRPARequestHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(HttpURLConnectionRPARequestHandlerService.class);

    /**
     * The key for the getRecord endpoint in the Salesforce endpoints configuration.
     */
    private final static String GET_RECORD_ENDPOINT_KEY = "get-record-endpoint";

    /**
     * The key for the createRecord endpoint in the Salesforce endpoints configuration.
     */
    private final static String CREATE_RECORD_ENDPOINT_KEY = "create-record-endpoint";

    /**
     * The key for the updateRecord endpoint in the Salesforce endpoints configuration.
     */
    private final static String UPDATE_RECORD_ENDPOINT_KEY = "update-record-endpoint";

    private final static String RECORD_PENDING_STATUS = "pending";
    private final static String RECORD_APPROVED_STATUS = "approved";
    private final static String RECORD_DECLINED_STATUS = "declined";
    private final static String RECORD_REJECTED_STATUS = "rejected";

    /**
     * The RPA configuration.
     */
    private final RPAConfiguration rpaConfiguration;

    /**
     * The HTTPURLConnection factory.
     */
    private HttpURLConnectionFactory connectionFactory;

    private CloseableHttpClient httpClient;

    /**
     * The JWT helper.
     */
    private JWTHelper jwtHelper;

    /**
     * The Recaptcha helper.
     */
    private RecaptchaHelper recaptchaHelper;

    /**
     * The record response handler.
     */
    private RecordResponseHandler recordResponseHandler;

    /**
     * Sets the HTTP URL connection factory.
     *
     * @param connectionFactory The HTTP URL connection factory to set.
     */
    public void setHttpURLConnectionFactory(HttpURLConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void setHttpClient(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Sets the JWT helper.
     *
     * @param jwtHelper The JWT helper to set.
     */
    public void setJWTHelper(JWTHelper jwtHelper) {
        this.jwtHelper = jwtHelper;
    }

    /**
     * Sets the Recaptcha helper.
     *
     * @param recaptchaHelper The Recaptcha helper to set.
     */
    public void setRecaptchaHelper(RecaptchaHelper recaptchaHelper) {
        this.recaptchaHelper = recaptchaHelper;
    }


    /**
     * Sets the RecordResponseHandler instance to handle the response of the record operations.
     *
     * @param recordResponseHandler the RecordResponseHandler instance to set
     */
    public void setRecordResponseHandler(RecordResponseHandler recordResponseHandler) {
        this.recordResponseHandler = recordResponseHandler;
    }

    /**
     * Constructs a new instance of the service using the given RPA configuration.
     *
     * @param rpaConfiguration The RPA configuration to use for this service.
     */
    public HttpURLConnectionRPARequestHandlerService(RPAConfiguration rpaConfiguration,
                                                     RPACachingService rpaCachingService) {
        // Initialize instance variables
        this.rpaConfiguration = rpaConfiguration;
        this.connectionFactory = url -> (HttpURLConnection) url.openConnection();

        // Initialize JWT helper
        this.jwtHelper = JWTHelper.getInstance();
        this.jwtHelper.setKeyRetriever(new JKSKeyRetriever());
        this.jwtHelper.setConfig(rpaConfiguration);
        this.jwtHelper.setHttpURLConnectionFactory(this.connectionFactory);

        // Initialize Recaptcha helper
        this.recaptchaHelper = new RecaptchaHelper();
        this.recaptchaHelper.setHttpURLConnectionFactory(this.connectionFactory);

        // Set RecordResponseHandler
        this.recordResponseHandler = new RecordResponseHandlerImpl(this.rpaConfiguration, this.connectionFactory,
                rpaCachingService);

        // Set HttpClient
        this.httpClient = HttpClients.createDefault();

        // Log RPA configuration coming from the config server
        LOGGER.debug("RPA_CONFIGURATION=" + this.rpaConfiguration.toString());
    }

    public RPAConfiguration getConfig() {
        return this.rpaConfiguration;
    }

    /**
     * Retrieves a record from Salesforce with the given record ID.
     *
     * @param recordId The ID of the record to retrieve.
     * @return The RecordWrapper object that contains the retrieved record.
     * @throws RecordNotFoundException    If the specified record was not found.
     * @throws RequestProcessingException If there was an error retrieving the record.
     */
    @Override
    public RecordWrapper getRecord(String recordId) throws RecordNotFoundException, RequestProcessingException {
        // Get the endpoint path for retrieving records
        String getRecordUri = getConfig().getSalesforceEndpoints().get(GET_RECORD_ENDPOINT_KEY);
        // Retrieve the JWTToken
        JWTToken token = jwtHelper.getToken();
        // Build the URL for this request
        String url;
        try {
            url = new URIBuilder(token.getInstanceUrl())
                    .setPath(getRecordUri + "/" + recordId)
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new RequestProcessingException("Error building URI: " + e.getMessage());
        }
        LOGGER.debug("GET_RECORD_URL=" + url);
        // Create the HttpURLConnection and send the GET request
        RecordWrapper recordWrapper;
        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            connection = connectionFactory.createHttpURLConnection(requestUrl);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token.getAccessToken());

            int responseCode = connection.getResponseCode();

            // Check if request is successful
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    LOGGER.debug("GET_RECORD_RESPONSE=" + response);
                    // Handle the response
                    recordWrapper = new ObjectMapper().readValue(response.toString(), RecordWrapper.class);
                }
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                // Handle the error HTTP_NOT_FOUND response
                throw RecordNotFoundException.fromRecordId(recordId);
            } else {
                // Handle any other error response
                throw new RequestProcessingException("Error response from salesforce service: " + connection.getResponseMessage());
            }
        } catch (MalformedURLException e) {
            // Handle the URL Malformed error
            LOGGER.debug("Invalid URL: " + e.getMessage());
            throw new RequestProcessingException("Invalid URL: " + e.getMessage());
        } catch (IOException e) {
            // Handle the I/O error
            LOGGER.debug("Error sending GET request: " + e.getMessage());
            throw new RequestProcessingException("I/O error: " + e.getMessage());
        } finally {
            // Close the connection
            if (connection != null) {
                connection.disconnect();
            }
        }
        return recordWrapper;
    }

    /**
     * Creates a new record in Salesforce using the information from the provided UserInfoWrapper object.
     *
     * This method builds a URL to create a record in Salesforce using a configuration endpoint.
     * It prepares the request payload from the UserInfoWrapper object and sends a POST request
     * to Salesforce. The response is parsed into a RecordWrapper object, which is returned.
     *
     * @param userInfoWrapper       The UserInfoWrapper object containing the data to create the new record.
     * @return RecordWrapper        The RecordWrapper object containing the newly created record.
     * @throws InvalidRequestException   If the request is invalid or incomplete.
     * @throws RequestProcessingException If an error occurs during request processing, including URL creation, serialization, or communication errors.
     */

    @Override
    public RecordWrapper createRecord(UserInfoWrapper userInfoWrapper) throws InvalidRequestException,
            RequestProcessingException {
        int responseCode;

        // Validate the email and country against the blacklists before proceeding
        String email = userInfoWrapper.getUserInfo().getEmail();
        String country = userInfoWrapper.getUserInfo().getCountry();
        String rejectionReason = "";

        // Log user info
        LOGGER.debug("User's email: " + email);
        LOGGER.debug("User's country: " + country);

        // Check for blacklisted email and country
        if (isEmailBlacklisted(email)) {
            rejectionReason = "Email " + email + " is blacklisted.";
            LOGGER.warn("Email {} is blacklisted. Request to create record will be automatically rejected.", email);
        } else if (isCountryBlacklisted(country)) {
            rejectionReason = "Country " + country + " is blacklisted.";
            LOGGER.warn("Country {} is blacklisted. Request to create record will be automatically rejected.", country);
        }

        // Set the pending status of the record in this service, and not based on the user's input
        userInfoWrapper.getUserInfo().setApprovalStatus(RECORD_PENDING_STATUS);

        if (!rejectionReason.isEmpty()) {
            // Append the rejection reason to the existing description
            String currentDescription = userInfoWrapper.getUserInfo().getDescription();
            String updatedDescription = currentDescription + "\nThis record was automatically rejected. Reason: " + rejectionReason;
            userInfoWrapper.getUserInfo().setDescription(updatedDescription);
            // Set approval status to rejected
            userInfoWrapper.getUserInfo().setApprovalStatus(RECORD_REJECTED_STATUS);
        }

        // Initialize return value
        RecordWrapper newRecordWrapper;

        // Get path
        String createRecordUri = getConfig().getSalesforceEndpoints().get(CREATE_RECORD_ENDPOINT_KEY);

        // Get token
        JWTToken token = jwtHelper.getToken();

        // Build URL
        String url;
        try {
            url = new URIBuilder(token.getInstanceUrl())
                    .setPath(createRecordUri)
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new RequestProcessingException("Error building URI: " + e.getMessage());
        }
        LOGGER.debug("CREATE_RECORD_URL=" + url);

        String postPayload;
        try {
            postPayload = prepareRequestPayload(userInfoWrapper);
            // Log the payload before serialization
            LOGGER.debug("CREATE_RECORD_PAYLOAD=" + postPayload);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error while preparing record creation payload: " + e.getMessage());
            throw new RequestProcessingException("Error while preparing record creation payload: " + e.getMessage());
        }

        // Send POST request
        HttpURLConnection connection = null;

        try {
            URL requestUrl = new URL(url);
            connection = connectionFactory.createHttpURLConnection(requestUrl);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token.getAccessToken());
            // Set payload
            byte[] payloadBytes = postPayload.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true); // tell connection we are writing data to the output stream
            OutputStream os = connection.getOutputStream();
            os.write(payloadBytes);
            os.flush();
            os.close();

            responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) { // If created
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    LOGGER.debug("CREATE_RECORD_RESPONSE=" + response);
                    // Handle the response
                    newRecordWrapper = new ObjectMapper().readValue(response.toString(), RecordWrapper.class);
                }
            } else {
                // Handle any other error response
                LOGGER.debug("Error response from Salesforce service: " + connection.getResponseMessage());
                throw new RequestProcessingException("Error response from Salesforce service: " + connection.getResponseMessage());
            }

        } catch (MalformedURLException e) {
            // Handle the URL Malformed error
            LOGGER.debug("Invalid URL: " + e.getMessage());
            throw new RequestProcessingException("Invalid URL: " + e.getMessage());
        } catch (IOException e) {
            // Handle the I/O error
            LOGGER.debug("Error sending GET request: " + e.getMessage());
            throw new RequestProcessingException("I/O error: " + e.getMessage());
        } finally {
            // Close the connection
            if (connection != null) {
                connection.disconnect();
            }
        }

        // Check if success and handle accordingly
        if (newRecordWrapper != null) {
            // Check if the record is marked as rejected before proceeding
            if (!RECORD_REJECTED_STATUS.equals(newRecordWrapper.getRecord().getUserInfo().getApprovalStatus())) {
                // If the record is not marked as rejected, proceed with the normal success handling,
                // including sending emails for SME approval and to the requester.
                this.recordResponseHandler.onRecordCreationSuccess(newRecordWrapper.getRecord());
            } else {
                // Since the record is automatically rejected, we skip sending approval and notification emails.
                LOGGER.info("Record automatically rejected due to blacklist. Skipping email notifications.");
            }
        } else {
            // we expect a record to be created every time we call createRecord
            // if newRecordWrapper is null, it means creation failed
            this.recordResponseHandler.onRecordCreationFailure(responseCode);
        }


        return newRecordWrapper;
    }

    private boolean isEmailBlacklisted(String email) {
        List<String> disallowedEmailStrings = rpaConfiguration.getDisallowedEmails();
        for (String patternString : disallowedEmailStrings) {
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(email);
            if (matcher.find()) {
                return true;
            }
        }
        return false;
    }

    private boolean isCountryBlacklisted(String country) {
        List<String> disallowedCountries = rpaConfiguration.getDisallowedCountries();
        return disallowedCountries.contains(country);
    }

    private String prepareRequestPayload(UserInfoWrapper userInfoWrapper) throws JsonProcessingException {
        // Set reCAPTCHA field to null, so it doesn't get serialized. SF service doesn't expect this field
        userInfoWrapper.setRecaptcha(null);

        // Serialize the userInfoWrapper object to JSON
        return new ObjectMapper().writeValueAsString(userInfoWrapper);
    }


    /**
     * Updates the status of a record with a given ID.
     *
     * @param recordId The ID of the record to update.
     * @param status   The status to update the record with.
     * @return The {@link RecordStatus} object representing the updated record status.
     * @throws RecordNotFoundException    If the record with the given ID is not found.
     * @throws InvalidRequestException    If the request is invalid.
     * @throws RequestProcessingException If there is an error processing the request.
     */
    @Override
    public RecordStatus updateRecord(String recordId, String status, String smeId) throws RecordNotFoundException,
            InvalidRequestException, RequestProcessingException {
        // Initialize return object
        RecordStatus recordStatus;

        // Get endpoint
        String updateRecordUri = getConfig().getSalesforceEndpoints().get(UPDATE_RECORD_ENDPOINT_KEY);

        // Create a valid approval status based on input
        String approvalStatus = generateApprovalStatus(status, smeId);

        // TODO: try caching here before updating the status in SF

        // PATCH request payload
        // Approval_Status__c is how SF service expect the key
        String patchPayload = new JSONObject().put("Approval_Status__c", approvalStatus).toString();
        LOGGER.debug("UPDATE_RECORD_PAYLOAD=" + patchPayload);

        // Get token
        JWTToken token = jwtHelper.getToken();

        // Build request URL
        String url;
        try {
            url = new URIBuilder(token.getInstanceUrl())
                    .setPath(updateRecordUri + "/" + recordId)
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new RequestProcessingException("Error building URI: " + e.getMessage());
        }
        LOGGER.debug("UPDATE_RECORD_URL=" + url);

        // Send PATCH request
        try {
            HttpPatch httpPatch = new HttpPatch(url);
            httpPatch.setHeader("Authorization", "Bearer " + token.getAccessToken());
            httpPatch.setHeader("Content-Type", "application/json");
            HttpEntity httpEntity = new StringEntity(patchPayload, ContentType.APPLICATION_JSON);
            httpPatch.setEntity(httpEntity);

            CloseableHttpResponse response = httpClient.execute(httpPatch);
            int statusCode = response.getStatusLine().getStatusCode();
            LOGGER.debug("UPDATE_RECORD_STATUS_CODE=" + statusCode);
            if (statusCode == HttpStatus.SC_OK) { // If success
                try (InputStream inputStream = response.getEntity().getContent()) {
                    String responseString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                    LOGGER.debug("UPDATE_RECORD_RESPONSE=" + responseString);
                    // Handle the response
                    recordStatus = new ObjectMapper().readValue(responseString, RecordStatus.class);
                }
            } else if (statusCode == HttpStatus.SC_BAD_REQUEST) { // If bad request
                LOGGER.debug("Invalid request: " + response.getStatusLine().getReasonPhrase());
                throw new InvalidRequestException("Invalid request: " + response.getStatusLine().getReasonPhrase());
            } else {
                // Handle any other error response
                LOGGER.debug("Error response from Salesforce service: " + response.getStatusLine().getReasonPhrase());
                throw new RequestProcessingException("Error response from Salesforce service: " + response.getStatusLine().getReasonPhrase());
            }
        } catch (IOException e) {
            // Handle the I/O error
            LOGGER.debug("Error sending GET request: " + e.getMessage());
            throw new RequestProcessingException("I/O error: " + e.getMessage());
        }

        // Retrieve updated record from SF service
        Record record = this.getRecord(recordId).getRecord();

        // Check if status is approved
        if (recordStatus.getApprovalStatus().toLowerCase().contains("approved")) {
            this.recordResponseHandler.onRecordUpdateApproved(record);
        } else {
            this.recordResponseHandler.onRecordUpdateDeclined(record);
        }

        return recordStatus;
    }

    /**
     * Generates an approval status string based on the given status and current date/time.
     * The date is in ISO 8601 format.
     *
     * @param status the approval status to use, either "Approved" or "Declined"
     * @param email  the email to append to the status
     * @return the generated approval status string, in the format "[status]_[yyyy-MM-dd'T'HH:mm:ss.SSSZ]_[email]"
     * @throws InvalidRequestException if the provided status is not "Approved" or "Declined"
     */
    private String generateApprovalStatus(String status, String smeId) throws InvalidRequestException {
        String formattedDate = Instant.now().toString(); // ISO 8601 format: 2023-05-09T15:59:03.872Z
        String approvalStatus;
        if (status != null) {
            switch (status.toLowerCase()) {
                case RECORD_APPROVED_STATUS:
                    approvalStatus = "Approved_";
                    break;
                case RECORD_DECLINED_STATUS:
                    approvalStatus = "Declined_";
                    break;
                default:
                    throw new InvalidRequestException("Invalid approval status: " + status);
            }
        } else {
            throw new InvalidRequestException("Invalid approval status: status is null");
        }
        return approvalStatus + formattedDate + "_" + smeId;
    }

}
