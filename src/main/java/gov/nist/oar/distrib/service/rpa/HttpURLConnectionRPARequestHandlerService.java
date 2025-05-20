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
        LOGGER.debug("USER_EMAIL={}, USER_COUNTRY={}", email, country);
    
        if (isEmailBlacklisted(email)) {
            LOGGER.warn("Email {} is blacklisted.", email);
            return "Email " + email + " is blacklisted.";
        } else if (isCountryBlacklisted(country)) {
            LOGGER.warn("Country {} is blacklisted.", country);
            return "Country " + country + " is blacklisted.";
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
     * Handles the actions to be taken after the creation of a record.
     *
     * Depending on the approval status of the record, this method determines the
     * post-processing steps. If the record was rejected, it logs the rejection and skips
     * further processing. If the record is pre-approved, it caches the dataset and sends
     * confirmation and download emails. Otherwise, it handles the successful creation of
     * the record.
     *
     * @param wrapper The RecordWrapper containing the record and its details.
     * @param input   The UserInfoWrapper containing the original input data used to create the record.
     * @param code    The HTTP status code returned by the record creation request.
     * @throws RequestProcessingException If any error occurs during post-processing, such as caching failures.
     */
    @Override
    public void handleAfterRecordCreation(RecordWrapper wrapper, UserInfoWrapper input, int code)
            throws RequestProcessingException {
        if (wrapper == null) {
            recordResponseHandler.onRecordCreationFailure(code);
            return;
        }
    
        String status = wrapper.getRecord().getUserInfo().getApprovalStatus();
    
        switch (status) {
            case RECORD_REJECTED_STATUS:
                LOGGER.info("Record auto-rejected; skipping post-processing.");
                break;
            case RECORD_PRE_APPROVED_STATUS:
                // Handle pre-approved dataset: cache immediately and send both confirmation and download emails
                String randomId = rpaDatasetCacher.cache(input.getUserInfo().getSubject());
                if (randomId == null)
                    throw new RequestProcessingException("Caching process returned null randomId");
                recordResponseHandler.onPreApprovedRecordCreationSuccess(wrapper.getRecord(), randomId);
                break;
            default:
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
        String baseUrl = rpaConfiguration.getBaseDownloadUrl().replace("/ds/", "/id/");
        return baseUrl + datasetId;
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
    public UpdateRecordResult updateRecord(String recordId, String status, String smeId)
            throws RecordNotFoundException, InvalidRequestException, RequestProcessingException {

        // 1. Fetch the original record (pre-update)
        Record record = getRecord(recordId).getRecord();

        // 2. Prepare approval status
        StatusPreparationResult prep = prepareApprovalStatusForPatch(record, status, smeId);

        // 3. Build PATCH payload and send it to backend
        String patchPayload = new JSONObject().put("Approval_Status__c", prep.getStatus()).toString();
        LOGGER.debug("UPDATE_RECORD_PAYLOAD={}", patchPayload);

        String url = buildUpdateRecordUrl(recordId);
        RecordStatus recordStatus = patchRecordInSalesforce(url, patchPayload);
        LOGGER.debug("UPDATE_RECORD_RESPONSE={}", recordStatus);

        // 4. Return the update result
        // IMPORTANT: here we return the original Record object (retrieved before the
        // PATCH request) along with the updated RecordStatus.
        // We need to return the original Record for necessary context (subject, email,
        // etc.) needed downstream.
        // This is intentional and not an issue because:
        // - Post-processing logic uses the updated status from RecordStatus, not from
        // Record.
        // - This avoids making another API call to fetch the updated record, improving
        // performance.
        return new UpdateRecordResult(recordStatus, record, prep.getRandomId());
    }

    /**
     * Extracts the random ID from the given current status string if the status
     * is in the expected finalized format.
     * 
     * <p>
     * The method skips processing if the current status is null or if it starts
     * with "Approved_PENDING_CACHING". It expects the finalized format to be:
     * "Approved_<timestamp>_<smeId>_<randomId>".
     * </p>
     * 
     * @param currentStatus The current approval status of the record.
     * @return The extracted random ID if the current status is in the expected
     *         format and is not pending caching, otherwise returns null.
     */

    private String extractRandomIdFromCurrentStatus(String currentStatus) {
        if (currentStatus == null)
            return null;

        // Skip if still pending
        if (currentStatus.startsWith("Approved_PENDING_CACHING")) {
            return null;
        }

        // Expected finalized format: Approved_<timestamp>_<smeId>_<randomId>
        String[] parts = currentStatus.split("_");
        if (parts.length == 4 && currentStatus.startsWith("Approved_")) {
            return parts[3]; // return the randomId
        }

        return null;
    }

    /**
     * Builds the URL used to update a record in the Salesforce database.
     *
     * @param recordId the ID of the record to update
     * @return the URL used to update the record in the Salesforce database
     * @throws RequestProcessingException if there is an error while building the
     *                                    URL
     */
    private String buildUpdateRecordUrl(String recordId) throws RequestProcessingException {
        try {
            String updateRecordUri = getConfig().getSalesforceEndpoints().get(UPDATE_RECORD_ENDPOINT_KEY);
            return new URIBuilder(jwtHelper.getToken().getInstanceUrl())
                    .setPath(updateRecordUri + "/" + recordId)
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new RequestProcessingException("Error building URI: " + e.getMessage());
        }
    }

    /**
     * Patches a record in the Salesforce database using the given URL and JSON
     * payload. The JSON payload is expected to be a valid JSON object that
     * contains the fields to update in the record.
     *
     * @param url     the URL of the record to update
     * @param payload the JSON payload containing the fields to update in the
     *                record
     * @return the updated record status, or null if the request was not
     *         successful
     * @throws RequestProcessingException if there is an error while processing
     *                                    the request
     */
    private RecordStatus patchRecordInSalesforce(String url, String payload) throws RequestProcessingException {
        try {
            HttpPatch patch = new HttpPatch(url);
            patch.setHeader("Authorization", "Bearer " + jwtHelper.getToken().getAccessToken());
            patch.setHeader("Content-Type", "application/json");
            patch.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(patch)) {
                int code = response.getStatusLine().getStatusCode();
                LOGGER.debug("UPDATE_RECORD_STATUS_CODE={}", code);

                if (code == HttpStatus.SC_OK) {
                    String json = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                    LOGGER.debug("UPDATE_RECORD_RESPONSE={}", json);
                    return new ObjectMapper().readValue(json, RecordStatus.class);
                } else if (code == HttpStatus.SC_BAD_REQUEST) {
                    throw new InvalidRequestException("Invalid request: " + response.getStatusLine().getReasonPhrase());
                } else {
                    throw new RequestProcessingException(
                            "Salesforce error: " + response.getStatusLine().getReasonPhrase());
                }
            }

        } catch (IOException e) {
            throw new RequestProcessingException("I/O error during record update: " + e.getMessage());
        }
    }

    /**
     * Prepares the approval status for a PATCH request to update a record status in
     * the database. This method takes a record, the desired status to be
     * set for the record, and the SME ID associated with the update. It returns a
     * StatusPreparationResult object containing the new approval status and a
     * random
     * ID if the record is being declined and uncaching is needed.
     * 
     * <p>
     * If the desired status is "Approved", the method does not perform caching and
     * returns a status string of "Approved_PENDING_CACHING_[smeId]". If the
     * desired status is "Declined", the method uncaches the existing random ID for
     * the record and returns a status string of
     * "Declined_[yyyy-MM-dd'T'HH:mm:ss.SSSZ]_[smeId]_[randomId]".
     * </p>
     * 
     * @param record The record to update.
     * @param status The desired status to be set for the record.
     * @param smeId  The SME ID associated with the update.
     * @return A StatusPreparationResult object containing the new approval status
     *         and
     *         a random ID if the record is being approved and caching is needed.
     * @throws InvalidRequestException    If the provided status is invalid or the
     *                                    request
     *                                    is otherwise invalid.
     * @throws RequestProcessingException If there is an error while processing the
     *                                    request,
     *                                    such as issues with caching or
     *                                    communication
     *                                    errors with the database.
     */
    private StatusPreparationResult prepareApprovalStatusForPatch(Record record, String status, String smeId)
            throws InvalidRequestException, RequestProcessingException {

        String approvalStatus;
        String randomId = null;

        if (RECORD_APPROVED_STATUS.equalsIgnoreCase(status)) {
            // Do not perform caching now — defer to async step
            approvalStatus = "Approved_PENDING_CACHING_" + smeId;
        } else if (RECORD_DECLINED_STATUS.equalsIgnoreCase(status)) {
            randomId = extractRandomIdFromCurrentStatus(record.getUserInfo().getApprovalStatus());
            if (randomId != null)
                rpaDatasetCacher.uncache(randomId);

            approvalStatus = generateApprovalStatus(status, smeId, null);
        } else {
            throw new InvalidRequestException("Invalid approval status: " + status);
        }

        return new StatusPreparationResult(approvalStatus, randomId);
    }

    public String finalizeApprovalAndPatchStatus(Record record, String smeId) throws RequestProcessingException {
        String datasetId = record.getUserInfo().getSubject();

        // 1. Perform the actual caching
        String randomId = rpaDatasetCacher.cache(datasetId);
        if (randomId == null) {
            throw new RequestProcessingException("Caching failed: randomId is null");
        }

        // 2. Generate the final approval status string
        String finalStatus = generateApprovalStatus("Approved", smeId, randomId);

        // 3. Build PATCH payload
        String payload = new JSONObject().put("Approval_Status__c", finalStatus).toString();
        LOGGER.debug("FINAL_APPROVAL_STATUS_PAYLOAD={}", payload);

        // 4. Send PATCH to update status
        String recordId = record.getId();
        String url = buildUpdateRecordUrl(recordId);
        patchRecordInSalesforce(url, payload);
        LOGGER.debug("Final approval status patched for record {}", recordId);

        return finalStatus;
    }

    /**
     * Generates the approval status string based on the given status and SME ID.
     * 
     * <p>
     * This method is used to generate the approval status string for a record,
     * which is then used to update the record status in the database. The
     * approval status string is expected to follow the format
     * "[status]_[yyyy-MM-dd'T'HH:mm:ss.SSSZ]_[smeId]_[randomId]".
     * </p>
     * 
     * @param status   The desired approval status to be set for the record.
     * @param smeId    The SME ID associated with the update.
     * @param randomId A randomly generated ID used for approval status string
     *                 generation.
     * @return The generated approval status string.
     * @throws InvalidRequestException If the provided status is invalid or the
     *                                 request is otherwise invalid.
     */
    private String generateApprovalStatus(String status, String smeId, String randomId) throws InvalidRequestException {
        String formattedDate = Instant.now().toString();
        String approvalStatus;

        if (status == null)
            throw new InvalidRequestException("Invalid approval status: status is null");

        switch (status.toLowerCase()) {
            case RECORD_APPROVED_STATUS:
                if (randomId == null) {
                    // During async flow, we don't have the randomId yet
                    approvalStatus = "Approved_PENDING_CACHING_" + formattedDate + "_" + smeId;
                } else {
                    approvalStatus = "Approved_" + formattedDate + "_" + smeId + "_" + randomId;
                }
                break;

            case RECORD_DECLINED_STATUS:
                approvalStatus = "Declined_" + formattedDate + "_" + smeId;
                break;

            default:
                throw new InvalidRequestException("Invalid approval status: " + status);
        }

        return approvalStatus;
    }

    @Override
    public void onRecordUpdateApproved(Record record, String randomId) throws RequestProcessingException {
        this.recordResponseHandler.onRecordUpdateApproved(record, randomId);
    }

    @Override
    public void onRecordUpdateDeclined(Record record) {
        this.recordResponseHandler.onRecordUpdateDeclined(record);
    }

}