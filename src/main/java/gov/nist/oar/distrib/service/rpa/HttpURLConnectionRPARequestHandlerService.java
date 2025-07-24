package gov.nist.oar.distrib.service.rpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecordNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.service.rpa.model.JWTToken;
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
 * An implementation of the {@link RPARequestHandler} interface that uses HttpURLConnection
 * to send HTTP requests and receive responses from the Salesforce service. This class serves
 * as a bridge between the application and Salesforce, acting as the data source for managing
 * records. Through this service, records can be created, retrieved, and updated directly in
 * Salesforce, using the platform's API for record management.
 *
 */
public class HttpURLConnectionRPARequestHandlerService implements RPARequestHandler {

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
    private final static String RECORD_PRE_APPROVED_STATUS = "pre-approved";
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

    private RPADatasetCacher rpaDatasetCacher;
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

    public void seRPADatasetCacher(RPADatasetCacher rpaDatasetCacher) {
        this.rpaDatasetCacher = rpaDatasetCacher;
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

        // Set RPADatasetCacher
        this.rpaDatasetCacher = new DefaultRPADatasetCacher(rpaCachingService);

        // Set HttpClient
        this.httpClient = HttpClients.createDefault();
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
     * Creates a new record in the Salesforce database based on the provided user information.
     * 
     * This method will first validate the user information against the blacklist. If the user is on the
     * blacklist, the record will be marked as rejected. Otherwise, the record will be created in
     * Salesforce. The method will then return the result of the record creation, including the
     * HTTP status code and the newly created record data.
     * 
     * @param userInfoWrapper the user information and record data for the new record
     * @return the result of the record creation, including the HTTP status code and the newly
     *         created record data
     * @throws InvalidRequestException if the request payload is invalid
     * @throws RequestProcessingException if there is an error while processing the request
     */
    @Override
    public RecordCreationResult createRecord(UserInfoWrapper userInfoWrapper)
            throws InvalidRequestException, RequestProcessingException {
        // 1. Blacklist validation
        String rejectionReason = evaluateBlacklistStatus(userInfoWrapper);
        if (!rejectionReason.isEmpty()) {
            markAsRejected(userInfoWrapper, rejectionReason);
        } else {
            updateApprovalStatus(userInfoWrapper);
        }

        // 2. Prepare POST payload
        String payload = preparePayloadOrFail(userInfoWrapper);
        String url = buildCreateRecordUrl();

        // 3. Call Salesforce
        RecordCreationResult postResult = postToSalesforce(url, payload);
        int statusCode = postResult.getStatusCode();
        LOGGER.debug("CREATE_RECORD_STATUS_CODE=" + statusCode);

        return postResult;
    }

    /**
     * Evaluates the blacklist status of the given UserInfoWrapper.
     * 
     * Given a UserInfoWrapper, this method will evaluate the blacklist status
     * of the user's email and country. If either the email or country is
     * blacklisted, the method will return a string indicating the reason for
     * the rejection. Otherwise, an empty string will be returned.
     * 
     * @param wrapper the UserInfoWrapper to evaluate
     * @return a string indicating the reason for rejection, or an empty string
     *         if the user is not blacklisted
     */
    private String evaluateBlacklistStatus(UserInfoWrapper wrapper) {
        String email = wrapper.getUserInfo().getEmail();
        String country = wrapper.getUserInfo().getCountry();
        String datasetId = wrapper.getUserInfo().getSubject(); // Dataset ID comes from subject

        LOGGER.debug("USER_EMAIL={}, USER_COUNTRY={}, DATASET_ID={}", email, country, datasetId);

        RPAConfiguration.BlacklistConfig blacklist = rpaConfiguration.getBlacklists().get(datasetId);

        if (blacklist == null) {
            LOGGER.info("No blacklist configured for dataset {}, access allowed.", datasetId);
            return ""; // Allow access if no blacklist defined
        }

        if (isEmailBlacklisted(email, blacklist.getDisallowedEmails())) {
            LOGGER.warn("Email {} is blacklisted for dataset {}.", email, datasetId);
            return "Email " + email + " is blacklisted for dataset " + datasetId + ".";
        }

        if (isCountryBlacklisted(country, blacklist.getDisallowedCountries())) {
            LOGGER.warn("Country {} is blacklisted for dataset {}.", country, datasetId);
            return "Country " + country + " is blacklisted for dataset " + datasetId + ".";
        }

        return "";
    }
    
    /**
     * Sets the approval status of a record to {@link #RECORD_REJECTED_STATUS} and
     * appends the given reason to the description of the record.
     * 
     * @param wrapper the record to be updated
     * @param reason the reason for rejection
     */
    private void markAsRejected(UserInfoWrapper wrapper, String reason) {
        wrapper.getUserInfo().setApprovalStatus(RECORD_REJECTED_STATUS);
        String updatedDesc = wrapper.getUserInfo().getDescription() +
                "\nThis record was automatically rejected. Reason: " + reason;
        wrapper.getUserInfo().setDescription(updatedDesc);
    }
    
    /**
     * Updates the approval status of a record based on whether the dataset is pre-approved.
     * If the dataset is pre-approved, the status is set to {@link #RECORD_PRE_APPROVED_STATUS}.
     * Otherwise, the status is set to {@link #RECORD_PENDING_STATUS}.
     *
     * @param wrapper The UserInfoWrapper containing the record.
     */
    private void updateApprovalStatus(UserInfoWrapper wrapper) {
        String datasetId = wrapper.getUserInfo().getSubject();
        boolean isPreApproved = isPreApprovedDataset(datasetId);
        wrapper.getUserInfo().setApprovalStatus(isPreApproved ? RECORD_PRE_APPROVED_STATUS : RECORD_PENDING_STATUS);
    }
    
    /**
     * Prepares the request payload for the record creation request.
     * If successful, returns the prepared payload. If an error occurs while preparing the payload,
     * logs the error and throws a RequestProcessingException.
     *
     * @param wrapper The UserInfoWrapper containing the record.
     * @return The prepared request payload.
     * @throws RequestProcessingException If an error occurs while preparing the payload.
     */
    private String preparePayloadOrFail(UserInfoWrapper wrapper) throws RequestProcessingException {
        try {
            String payload = prepareRequestPayload(wrapper);
            LOGGER.debug("CREATE_RECORD_PAYLOAD={}", payload);
            return payload;
        } catch (JsonProcessingException e) {
            LOGGER.error("Error preparing payload: {}", e.getMessage());
            throw new RequestProcessingException("Error preparing payload: " + e.getMessage());
        }
    }
    
    /**
     * Handles the post-processing steps after a record has been created.
     * 
     * Depending on the approval status of the record, this method will take
     * different actions:
     * 
     * - If the record is null, indicating that the creation failed entirely,
     *   it will notify the handler of the failure.
     * - If the record is auto-rejected, it logs the event and skips further processing.
     * - If the record is pre-approved, it attempts to cache the dataset and
     *   send a download link to the user. Notifies the user of success or failure.
     * - For all other statuses, it sends a confirmation to the user and an
     *   approval request to the SME.
     *
     * @param wrapper The RecordWrapper containing the created record and its details.
     * @param input The UserInfoWrapper containing the original input data used to create the record.
     * @param code The HTTP status code returned by the record creation request.
     * @throws RequestProcessingException If an error occurs during post-processing.
     */
    @Override
    public void handleAfterRecordCreation(RecordWrapper wrapper, UserInfoWrapper input, int code)
            throws RequestProcessingException {

        if (wrapper == null) {
            // If record creation failed entirely (null wrapper), notify handler of failure
            recordResponseHandler.onRecordCreationFailure(code);
            return;
        }

        String status = wrapper.getRecord().getUserInfo().getApprovalStatus();

        switch (status) {
            case RECORD_REJECTED_STATUS:
                // Automatically rejected records (e.g., blacklisted) require no post-processing
                LOGGER.info("Record auto-rejected; skipping post-processing.");
                break;

            case RECORD_PRE_APPROVED_STATUS:
                try {
                    // Try to cache the dataset and send a download link
                    String datasetId = input.getUserInfo().getSubject();
                    String randomId = rpaDatasetCacher.cache(datasetId);

                    if (randomId == null) {
                        // Caching failed — notify user of failure
                        LOGGER.warn("Failed to cache dataset '{}'; notifying user of processing failure.", datasetId);
                        recordResponseHandler.onFailure(wrapper.getRecord());
                    } else {
                        // Caching succeeded — notify user of success and provide download link
                        recordResponseHandler.onPreApprovedRecordCreationSuccess(wrapper.getRecord(), randomId);
                    }

                } catch (Exception e) {
                    // Unexpected error during caching or metadata lookup — notify user
                    LOGGER.error("Unexpected error while caching dataset or fetching metadata: {}", e.getMessage(), e);
                    recordResponseHandler.onFailure(wrapper.getRecord());
                }
                break;

            default:
                // Default behavior: confirmation to user and approval request to SME
                recordResponseHandler.onRecordCreationSuccess(wrapper.getRecord());
        }
    }
    
    /**
     * Builds the URL used to create a record in the Salesforce database.
     * 
     * @return the URL used to create a record in the Salesforce database.
     * @throws RequestProcessingException if there is an error while building the URL.
     */
    private String buildCreateRecordUrl() throws RequestProcessingException {
        try {
            String createRecordUri = getConfig().getSalesforceEndpoints().get(CREATE_RECORD_ENDPOINT_KEY);
            return new URIBuilder(jwtHelper.getToken().getInstanceUrl())
                    .setPath(createRecordUri)
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new RequestProcessingException("Error building URI: " + e.getMessage());
        }
    }

    /**
     * Performs a POST request to the Salesforce service to create a new record.
     * 
     * @param url    the URL of the Salesforce endpoint to post to
     * @param payload the JSON payload to send in the request body
     * @return a RecordCreationResult containing the newly created record and the HTTP status code
     * @throws RequestProcessingException if there is an error while performing the request
     */
    private RecordCreationResult postToSalesforce(String url, String payload) throws RequestProcessingException {
        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            connection = connectionFactory.createHttpURLConnection(requestUrl);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + jwtHelper.getToken().getAccessToken());
            connection.setDoOutput(true);
    
            // Write JSON payload
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
    
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    LOGGER.debug("CREATE_RECORD_RESPONSE=" + response);
                    RecordWrapper recordWrapper =  new ObjectMapper().readValue(response.toString(), RecordWrapper.class);
                    return new RecordCreationResult(recordWrapper, responseCode);

                }
            } else {
                LOGGER.error("Salesforce returned error: " + connection.getResponseMessage());
                throw new RequestProcessingException("Error response from Salesforce service: " + connection.getResponseMessage());
            }
    
        } catch (IOException e) {
            throw new RequestProcessingException("I/O error during POST to Salesforce: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    

    // Method to check if a dataset is pre-approved
    public boolean isPreApprovedDataset(String datasetId) {
        String datasetUrl = constructDatasetUrl(datasetId);

        JsonNode metadata = fetchDatasetMetadata(datasetUrl);
        if (metadata == null) {
            LOGGER.info("Failed to retrieve metadata or metadata is empty.");
            return false;
        }

        return checkForPreApproval(metadata, datasetId);
    }

    private String constructDatasetUrl(String datasetId) {
        String baseUrl = rpaConfiguration.getResolverUrl();
        return baseUrl.endsWith("/") ? baseUrl + datasetId : baseUrl + "/" + datasetId;
    }

    /**
     * Retrieves the metadata for the given datasetId in JSON format.
     *
     * @param datasetUrl the URL to retrieve the dataset metadata from
     * @return the JSON metadata for the dataset, or null if the request fails
     */
    private JsonNode fetchDatasetMetadata(String datasetUrl) {
        HttpURLConnection connection = null; 
        try {
            URL url = new URL(datasetUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
    
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    return new ObjectMapper().readTree(in);
                }
            } else {
                LOGGER.debug("Failed to retrieve metadata, HTTP response code: " + connection.getResponseCode());
            }
        } catch (IOException e) {
            LOGGER.debug("Error retrieving metadata: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect(); // Close connection
            }
        }
        return null; // Return null if metadata fetch fails
    }
    

    /**
     * Check if a dataset is pre-approved.  This is done by examining the
     * metadata for the dataset and looking for a component with type
     * "nrdp:RestrictedAccessPage" and an accessProfile with type "rpa:rp0".
     * If such a component is found, the method returns true, indicating the
     * dataset is pre-approved.  Otherwise, it returns false.
     * 
     * @param metadata the JSON metadata for the dataset
     * @param datasetId the dataset ID
     * @return true if the dataset is pre-approved, false otherwise
     */
    private boolean checkForPreApproval(JsonNode metadata, String datasetId) {
        JsonNode components = metadata.path("components");
        for (JsonNode component : components) {
            JsonNode typeNode = component.path("@type");
            if (typeNode.isArray() && typeNode.toString().contains("nrdp:RestrictedAccessPage")) {
                JsonNode accessProfile = component.path("pdr:accessProfile");
                if (!accessProfile.isMissingNode() && "rpa:rp0".equals(accessProfile.path("@type").asText())) {
                    LOGGER.info("Dataset (ID =" + datasetId + ")  is pre-approved.");
                    return true;
                }
            }
        }
        LOGGER.info("Dataset (ID =" + datasetId + ") requires SME approval.");
        return false;
    }
    
     private boolean isEmailBlacklisted(String email, List<String> patterns) {
        if (patterns == null)
            return false;
        for (String patternString : patterns) {
            if (Pattern.compile(patternString).matcher(email).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean isCountryBlacklisted(String country, List<String> disallowedCountries) {
        return disallowedCountries != null && disallowedCountries.contains(country);
    }
    

    private String prepareRequestPayload(UserInfoWrapper userInfoWrapper) throws JsonProcessingException {
        // Set reCAPTCHA field to null, so it doesn't get serialized. SF service doesn't expect this field
        userInfoWrapper.setRecaptcha(null);

        // Serialize the userInfoWrapper object to JSON
        return new ObjectMapper().writeValueAsString(userInfoWrapper);
    }
    

    /**
     * Updates the status of a record in the database.
     * <p>
     * This method handles the approval or decline of a record. When a record is approved, it caches the dataset,
     * generates a random ID, and appends this ID to the status before updating the record in the database.
     * When a record is declined, the dataset is not cached, a null random ID is used, and the record status is
     * updated in the database without appending the random ID.
     * </p>
     * <p>
     * In cases where a record was initially approved (and thus cached with a random ID) but is later declined,
     * this method retrieves the random ID from the status, uncaches the dataset using this ID, and updates
     * the record status without the random ID.
     * </p>
     *
     * @param recordId The ID of the record to update.
     * @param status   The new status to be set for the record. Can be 'Approved' or 'Declined'.
     * @param smeId    The SME ID associated with the record update.
     * @return A {@link RecordStatus} object representing the updated record status.
     * @throws RecordNotFoundException    If the record with the given ID is not found.
     * @throws InvalidRequestException    If the provided status is invalid or the request is otherwise invalid.
     * @throws RequestProcessingException If there is an error in processing the update request, such as issues
     *                                    with caching or communication errors with the database.
     */
    @Override
    public RecordStatus updateRecord(String recordId, String status, String smeId) throws RecordNotFoundException,
            InvalidRequestException, RequestProcessingException {
        // Initialize return object
        RecordStatus recordStatus;
        Record record = this.getRecord(recordId).getRecord();
        String datasetId = record.getUserInfo().getSubject();
        String randomId = null;

        // If the record is being approved
        if (RECORD_APPROVED_STATUS.equalsIgnoreCase(status)) {
            LOGGER.info("Starting caching...");
            randomId = this.rpaDatasetCacher.cache(datasetId);
            if (randomId == null) {
                throw new RequestProcessingException("Caching process returned a null randomId");
            }
        }
        // If the record is being declined, check if it needs uncaching
        else if (RECORD_DECLINED_STATUS.equalsIgnoreCase(status)) {
            randomId = extractRandomIdFromCurrentStatus(record.getUserInfo().getApprovalStatus());
            if (randomId != null) {
                this.rpaDatasetCacher.uncache(randomId);
            }
        }

        // Create a valid approval status based on input
        String approvalStatus = generateApprovalStatus(status, smeId, randomId);

        // PATCH request payload
        // Approval_Status__c is how SF service expect the key
        String patchPayload = new JSONObject().put("Approval_Status__c", approvalStatus).toString();
        LOGGER.debug("UPDATE_RECORD_PAYLOAD=" + patchPayload);

        // Get token
        JWTToken token = jwtHelper.getToken();

        // Get endpoint
        String updateRecordUri = getConfig().getSalesforceEndpoints().get(UPDATE_RECORD_ENDPOINT_KEY);

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

        // Check if status is approved
        if (recordStatus.getApprovalStatus().toLowerCase().contains("approved")) {
            this.recordResponseHandler.onRecordUpdateApproved(record, randomId);
        } else {
            this.recordResponseHandler.onRecordUpdateDeclined(record);
        }

        return recordStatus;
    }

    private String extractRandomIdFromCurrentStatus(String currentStatus) {
        if (currentStatus != null && currentStatus.startsWith("Approved_")) {
            String[] parts = currentStatus.split("_");

            // Since the expected format is "[status]_[yyyy-MM-dd'T'HH:mm:ss.SSSZ]_[smeId]_[randomId]",
            // the randomId should be the 4th part, if all parts are present
            if (parts.length == 4) {
                return parts[3];
            }
        }
        return null;
    }


    /**
     * Generates an approval status string based on the given status, current date/time, and random ID.
     * The date is in ISO 8601 format. If the status is "Declined", the randomId will not be appended.
     *
     * @param status   the approval status to use, either "Approved" or "Declined"
     * @param smeId    the SME ID to append to the status
     * @param randomId the generated random ID to append (only if status is "Approved")
     * @return the generated approval status string.
     *         If status is "Approved", the format is:
     *         "[status]_[yyyy-MM-dd'T'HH:mm:ss.SSSZ]_[smeId]_[randomId]".
     *         If status is "Declined", the format is:
     *         "[status]_[yyyy-MM-dd'T'HH:mm:ss.SSSZ]_[smeId]"
     * @throws InvalidRequestException if the provided status is not "Approved" or "Declined"
     */
    private String generateApprovalStatus(String status, String smeId, String randomId) throws InvalidRequestException {
        String formattedDate = Instant.now().toString();
        String approvalStatus;

        if (status != null) {
            switch (status.toLowerCase()) {
                case RECORD_APPROVED_STATUS:
                    approvalStatus = "Approved_" + formattedDate + "_" + smeId;
                    if (randomId != null) {
                        approvalStatus += "_" + randomId;
                    }
                    break;
                case RECORD_DECLINED_STATUS:
                    approvalStatus = "Declined_" + formattedDate + "_" + smeId;
                    break;
                default:
                    throw new InvalidRequestException("Invalid approval status: " + status);
            }
        } else {
            throw new InvalidRequestException("Invalid approval status: status is null");
        }
        return approvalStatus;
    }


}