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
import org.apache.http.util.EntityUtils;
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
        LOGGER.debug("RPA Salesforce record fetch started reqId={} recordId={} endpoint={}",
                RPALogContext.requestId(), recordId, RPALogContext.safeUrl(url));
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
                    recordWrapper = new ObjectMapper().readValue(response.toString(), RecordWrapper.class);
                    if (recordWrapper != null && recordWrapper.getRecord() != null) {
                        RPALogContext.updateFromRecord(recordWrapper.getRecord());
                        LOGGER.debug("RPA Salesforce record fetch completed reqId={} recordId={} caseNum={} approvalStatus={}",
                                RPALogContext.requestId(),
                                recordWrapper.getRecord().getId(),
                                recordWrapper.getRecord().getCaseNum(),
                                RPALogContext.summarizeApprovalStatus(
                                        recordWrapper.getRecord().getUserInfo().getApprovalStatus()));
                    }
                }
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                // Handle the error HTTP_NOT_FOUND response
                LOGGER.warn("RPA Salesforce record fetch returned not-found reqId={} recordId={}",
                        RPALogContext.requestId(), recordId);
                throw RecordNotFoundException.fromRecordId(recordId);
            } else {
                // Handle any other error response
                LOGGER.warn("RPA Salesforce record fetch failed reqId={} recordId={} statusCode={} message={}",
                        RPALogContext.requestId(), recordId, responseCode, connection.getResponseMessage());
                throw new RequestProcessingException("Error response from salesforce service: " + connection.getResponseMessage());
            }
        } catch (MalformedURLException e) {
            // Handle the URL Malformed error
            LOGGER.error("RPA Salesforce record fetch failed reqId={} recordId={} reason=invalid-url message={}",
                    RPALogContext.requestId(), recordId, e.getMessage());
            throw new RequestProcessingException("Invalid URL: " + e.getMessage());
        } catch (IOException e) {
            // Handle the I/O error
            LOGGER.error("RPA Salesforce record fetch failed reqId={} recordId={} message={}",
                    RPALogContext.requestId(), recordId, e.getMessage());
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
        Record createdRecord = postResult.getRecordWrapper() != null ? postResult.getRecordWrapper().getRecord() : null;
        if (createdRecord != null) {
            RPALogContext.updateFromRecord(createdRecord);
            LOGGER.info("RPA Salesforce record create completed reqId={} dataset={} recordId={} caseNum={} approvalStatus={} statusCode={}",
                    RPALogContext.requestId(),
                    createdRecord.getUserInfo().getSubject(),
                    createdRecord.getId(),
                    createdRecord.getCaseNum(),
                    RPALogContext.summarizeApprovalStatus(createdRecord.getUserInfo().getApprovalStatus()),
                    statusCode);
        } else {
            LOGGER.info("RPA Salesforce record create completed reqId={} dataset={} statusCode={}",
                    RPALogContext.requestId(),
                    userInfoWrapper.getUserInfo().getSubject(),
                    statusCode);
        }

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

        LOGGER.debug("RPA blacklist check started reqId={} dataset={} emailDomain={} country={}",
                RPALogContext.requestId(), datasetId, RPALogContext.emailDomain(email), country);

        RPAConfiguration.BlacklistConfig blacklist = rpaConfiguration.getBlacklists().get(datasetId);

        if (blacklist == null) {
            LOGGER.info("RPA blacklist check passed reqId={} dataset={} ruleSet=none",
                    RPALogContext.requestId(), datasetId);
            return ""; // Allow access if no blacklist defined
        }

        if (isEmailBlacklisted(email, blacklist.getDisallowedEmails())) {
            LOGGER.warn("RPA blacklist check blocked reqId={} dataset={} reason=email email={}",
                    RPALogContext.requestId(), datasetId, RPALogContext.maskEmail(email));
            return "Email " + email + " is blacklisted for dataset " + datasetId + ".";
        }

        if (isCountryBlacklisted(country, blacklist.getDisallowedCountries())) {
            LOGGER.warn("RPA blacklist check blocked reqId={} dataset={} reason=country country={}",
                    RPALogContext.requestId(), datasetId, country);
            return "Country " + country + " is blacklisted for dataset " + datasetId + ".";
        }

        LOGGER.info("RPA blacklist check passed reqId={} dataset={} ruleSet=configured",
                RPALogContext.requestId(), datasetId);
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
        String description = wrapper.getUserInfo().getDescription();
        String updatedDesc = (description == null ? "" : description) +
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
    private void updateApprovalStatus(UserInfoWrapper wrapper) throws RequestProcessingException {
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
            LOGGER.debug("RPA create-record payload prepared reqId={} dataset={} approvalStatus={} fields=userInfo",
                    RPALogContext.requestId(),
                    wrapper.getUserInfo().getSubject(),
                    RPALogContext.summarizeApprovalStatus(wrapper.getUserInfo().getApprovalStatus()));
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
                LOGGER.info("RPA post-create processing skipped reqId={} recordId={} dataset={} reason=auto-rejected",
                        RPALogContext.requestId(),
                        wrapper.getRecord().getId(),
                        input.getUserInfo().getSubject());
                break;

            case RECORD_PRE_APPROVED_STATUS:
                try {
                    // Try to cache the dataset and send a download link
                    String datasetId = input.getUserInfo().getSubject();
                    String randomId = rpaDatasetCacher.cache(datasetId);

                    if (randomId == null) {
                        // Caching failed — notify user of failure
                        LOGGER.warn("RPA pre-approved cache failed reqId={} recordId={} dataset={} reason=null-cart-id",
                                RPALogContext.requestId(), wrapper.getRecord().getId(), datasetId);
                        recordResponseHandler.onFailure(wrapper.getRecord());
                    } else {
                        // Caching succeeded — notify user of success and provide download link
                        LOGGER.info("RPA pre-approved cache completed reqId={} recordId={} dataset={}",
                                RPALogContext.requestId(), wrapper.getRecord().getId(), datasetId);
                        recordResponseHandler.onPreApprovedRecordCreationSuccess(wrapper.getRecord(), randomId);
                    }

                } catch (Exception e) {
                    // Unexpected error during caching or metadata lookup — notify user
                    LOGGER.error("RPA pre-approved post-processing failed reqId={} recordId={} dataset={}: {}",
                            RPALogContext.requestId(), wrapper.getRecord().getId(),
                            input.getUserInfo().getSubject(), e.getMessage(), e);
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
                    RecordWrapper recordWrapper =  new ObjectMapper().readValue(response.toString(), RecordWrapper.class);
                    return new RecordCreationResult(recordWrapper, responseCode);

                }
            } else {
                LOGGER.error("RPA Salesforce record create failed reqId={} endpoint={} statusCode={} message={}",
                        RPALogContext.requestId(), RPALogContext.safeUrl(url), responseCode, connection.getResponseMessage());
                throw new RequestProcessingException("Error response from Salesforce service: " + connection.getResponseMessage());
            }

        } catch (IOException e) {
            LOGGER.error("RPA Salesforce record create failed reqId={} endpoint={} message={}",
                    RPALogContext.requestId(), RPALogContext.safeUrl(url), e.getMessage());
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
            LOGGER.error("Failed to retrieve metadata for dataset {}", datasetId);
            throw new RequestProcessingException("Failed to retrieve metadata from resolver");
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
                    LOGGER.info("RPA approval path selected reqId={} dataset={} mode=pre-approved",
                            RPALogContext.requestId(), datasetId);
                    return true;
                }
            }
        }
        LOGGER.info("RPA approval path selected reqId={} dataset={} mode=sme",
                RPALogContext.requestId(), datasetId);
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
     * This method handles the approval or decline of a record. For approvals, it sets an interim
     * "ApprovalPending" status and returns immediately. The actual caching and final status update
     * happen asynchronously via {@link #handleAfterRecordUpdate(Record, String, String, String)}.
     * </p>
     * <p>
     * For declines, the status is updated directly without caching.
     * </p>
     *
     * @param recordId The ID of the record to update.
     * @param status   The new status to be set for the record. Can be 'Approved' or 'Declined'.
     * @param smeId    The SME ID associated with the record update.
     * @return A {@link RecordUpdateResult} containing the record status, record data, and dataset ID.
     * @throws RecordNotFoundException    If the record with the given ID is not found.
     * @throws InvalidRequestException    If the provided status is invalid or the request is otherwise invalid.
     * @throws RequestProcessingException If there is an error in processing the update request.
     */
    @Override
    public RecordUpdateResult updateRecord(String recordId, String status, String smeId) throws RecordNotFoundException,
            InvalidRequestException, RequestProcessingException {
        // Initialize return object
        RecordStatus recordStatus;
        Record record = this.getRecord(recordId).getRecord();
        String datasetId = record.getUserInfo().getSubject();
        String currentStatus = record.getUserInfo().getApprovalStatus();

        // Create a valid approval status based on input
        // For approvals, use interim "ApprovalPending" status (no random ID yet)
        // For declines, use final "Declined" status
        String approvalStatus = generateApprovalStatus(status, smeId, null);

        // PATCH request payload
        // Approval_Status__c is how SF service expect the key
        String patchPayload = new JSONObject().put("Approval_Status__c", approvalStatus).toString();

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
        LOGGER.debug("RPA record status update started reqId={} recordId={} endpoint={} requestedAction={} previousStatus={} targetStatus={}",
                RPALogContext.requestId(), recordId, RPALogContext.safeUrl(url), status,
                RPALogContext.summarizeApprovalStatus(currentStatus),
                RPALogContext.summarizeApprovalStatus(approvalStatus));

        // Send PATCH request
        try {
            HttpPatch httpPatch = new HttpPatch(url);
            httpPatch.setHeader("Authorization", "Bearer " + token.getAccessToken());
            httpPatch.setHeader("Content-Type", "application/json");
            HttpEntity httpEntity = new StringEntity(patchPayload, ContentType.APPLICATION_JSON);
            httpPatch.setEntity(httpEntity);

            CloseableHttpResponse response = httpClient.execute(httpPatch);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) { // If success
                try (InputStream inputStream = response.getEntity().getContent()) {
                    String responseString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                    // Handle the response
                    recordStatus = new ObjectMapper().readValue(responseString, RecordStatus.class);
                    LOGGER.info("RPA record status updated reqId={} recordId={} caseNum={} dataset={} action={} previousStatus={} newStatus={}",
                            RPALogContext.requestId(), recordId, record.getCaseNum(), datasetId, status,
                            RPALogContext.summarizeApprovalStatus(currentStatus),
                            RPALogContext.summarizeApprovalStatus(approvalStatus));
                }
            } else if (statusCode == HttpStatus.SC_BAD_REQUEST) { // If bad request
                LOGGER.warn("RPA record status update rejected reqId={} recordId={} statusCode={} message={}",
                        RPALogContext.requestId(), recordId, statusCode, response.getStatusLine().getReasonPhrase());
                throw new InvalidRequestException("Invalid request: " + response.getStatusLine().getReasonPhrase());
            } else {
                // Handle any other error response
                LOGGER.error("RPA record status update failed reqId={} recordId={} statusCode={} message={}",
                        RPALogContext.requestId(), recordId, statusCode, response.getStatusLine().getReasonPhrase());
                throw new RequestProcessingException("Error response from Salesforce service: " + response.getStatusLine().getReasonPhrase());
            }
        } catch (IOException e) {
            // Handle the I/O error
            LOGGER.error("RPA record status update failed reqId={} recordId={} message={}",
                    RPALogContext.requestId(), recordId, e.getMessage());
            throw new RequestProcessingException("I/O error: " + e.getMessage());
        }

        // Return result for async processing
        // Note: Email notifications and caching are handled asynchronously
        record.getUserInfo().setApprovalStatus(approvalStatus);
        return new RecordUpdateResult(recordStatus, record, datasetId, currentStatus);
    }

    /**
     * Handles the post-processing of a record update (approval/decline).
     * <p>
     * For approvals: caches the dataset, updates Salesforce with final status including
     * the random ID, and sends success/failure email.
     * For declines: uncaches the dataset if previously approved, and sends decline notification.
     * </p>
     *
     * @param record    the record that was updated
     * @param status    the approval status ("approved" or "declined")
     * @param datasetId the ID of the dataset associated with the record
     * @throws RequestProcessingException if an error occurs during post-processing
     */
    @Override
    public void handleAfterRecordUpdate(Record record, String status, String datasetId, String previousApprovalStatus)
            throws RequestProcessingException {

        if (RECORD_APPROVED_STATUS.equalsIgnoreCase(status)) {
            handleApprovalPostProcessing(record, datasetId);
        } else if (RECORD_DECLINED_STATUS.equalsIgnoreCase(status)) {
            handleDeclinePostProcessing(record, previousApprovalStatus);
        }
    }

    /**
     * Handles the async post-processing for an approval.
     * Caches the dataset, updates Salesforce with final status, and sends email.
     */
    private void handleApprovalPostProcessing(Record record, String datasetId)
            throws RequestProcessingException {
        String randomId = null;
        long startedAt = System.currentTimeMillis();

        try {
            // Cache the dataset
            LOGGER.info("RPA approval post-processing started reqId={} recordId={} dataset={} stage=caching",
                    RPALogContext.requestId(), record.getId(), datasetId);
            long cacheStartedAt = System.currentTimeMillis();
            randomId = this.rpaDatasetCacher.cache(datasetId);
            long cacheElapsedMs = System.currentTimeMillis() - cacheStartedAt;

            if (randomId == null) {
                LOGGER.error("RPA approval post-processing failed reqId={} recordId={} dataset={} reason=null-cart-id",
                        RPALogContext.requestId(), record.getId(), datasetId);
                patchFailureStatus(record);
                this.recordResponseHandler.onFailure(record);
                return;
            }
            LOGGER.info("RPA approval cache completed reqId={} recordId={} dataset={} cartId={} elapsedMs={}",
                    RPALogContext.requestId(), record.getId(), datasetId, randomId, cacheElapsedMs);

            // Update Salesforce with final status including randomId
            String currentStatus = record.getUserInfo().getApprovalStatus();
            String finalStatus = updateApprovalStatusWithRandomId(currentStatus, randomId);
            LOGGER.info(
                    "RPA approval finalization started reqId={} recordId={} dataset={} currentStatus={} targetStatus={} cartId={}",
                    RPALogContext.requestId(),
                    record.getId(),
                    datasetId,
                    RPALogContext.summarizeApprovalStatus(currentStatus),
                    RPALogContext.summarizeApprovalStatus(finalStatus),
                    randomId);
            patchRecordStatus(record.getId(), finalStatus);
            LOGGER.info("RPA approval finalization patch succeeded reqId={} recordId={} dataset={} targetStatus={}",
                    RPALogContext.requestId(), record.getId(), datasetId,
                    RPALogContext.summarizeApprovalStatus(finalStatus));
            record.getUserInfo().setApprovalStatus(finalStatus);

            // Send success email with download link
            this.recordResponseHandler.onRecordUpdateApproved(record, randomId);
            LOGGER.info("RPA approval post-processing completed reqId={} recordId={} dataset={} finalStatus={} totalElapsedMs={}",
                    RPALogContext.requestId(), record.getId(), datasetId,
                    RPALogContext.summarizeApprovalStatus(finalStatus),
                    System.currentTimeMillis() - startedAt);

        } catch (Exception e) {
            LOGGER.error("RPA approval post-processing failed reqId={} recordId={} dataset={} elapsedMs={}: {}",
                    RPALogContext.requestId(), record.getId(), datasetId,
                    System.currentTimeMillis() - startedAt, e.getMessage(), e);
            if (randomId != null) {
                try {
                    LOGGER.warn("RPA approval cleanup started reqId={} recordId={} dataset={} cartId={}",
                            RPALogContext.requestId(), record.getId(), datasetId, randomId);
                    this.rpaDatasetCacher.uncache(randomId);
                    LOGGER.warn("RPA approval cleanup completed reqId={} recordId={} dataset={} cartId={}",
                            RPALogContext.requestId(), record.getId(), datasetId, randomId);
                } catch (Exception uncacheException) {
                    LOGGER.error("RPA approval cleanup failed reqId={} recordId={} dataset={} stage=uncache: {}",
                            RPALogContext.requestId(), record.getId(), datasetId,
                            uncacheException.getMessage(), uncacheException);
                }
            }
            patchFailureStatus(record);
            this.recordResponseHandler.onFailure(record);
        }
    }

    /**
     * Handles the async post-processing for a decline.
     * Uncaches the dataset if previously approved, and sends decline notification.
     */
    private void handleDeclinePostProcessing(Record record, String previousApprovalStatus) {
        // Check if there's a random ID from a previous approval to uncache
        String statusForCleanup = previousApprovalStatus != null
                ? previousApprovalStatus
                : record.getUserInfo().getApprovalStatus();
        String randomId = extractRandomIdFromCurrentStatus(statusForCleanup);

        if (randomId != null) {
            try {
                LOGGER.info("RPA decline post-processing started reqId={} recordId={} previousStatus={} stage=uncache",
                        RPALogContext.requestId(), record.getId(),
                        RPALogContext.summarizeApprovalStatus(statusForCleanup));
                this.rpaDatasetCacher.uncache(randomId);
            } catch (Exception e) {
                // Log but don't fail - the decline should still succeed
                LOGGER.error("RPA decline cleanup failed reqId={} recordId={}: {}",
                        RPALogContext.requestId(), record.getId(), e.getMessage(), e);
            }
        }

        // Send decline notification
        this.recordResponseHandler.onRecordUpdateDeclined(record);
        LOGGER.info("RPA decline post-processing completed reqId={} recordId={} finalStatus={}",
                RPALogContext.requestId(), record.getId(),
                RPALogContext.summarizeApprovalStatus(record.getUserInfo().getApprovalStatus()));
    }

    private void patchFailureStatus(Record record) {
        String failureStatus = updateApprovalStatusPrefix(record.getUserInfo().getApprovalStatus(), "ApprovalFailed");
        try {
            patchRecordStatus(record.getId(), failureStatus);
            record.getUserInfo().setApprovalStatus(failureStatus);
        } catch (RequestProcessingException e) {
            LOGGER.error("Failed to update approval failure status for record {}: {}",
                    record.getId(), e.getMessage(), e);
        }
    }

    /**
     * Updates the approval status string to include the random ID.
     * Converts "ApprovalPending_timestamp_smeId" to "Approved_timestamp_smeId_randomId"
     */
    private String updateApprovalStatusWithRandomId(String currentStatus, String randomId) {
        // Replace "ApprovalPending" with "Approved" and append randomId
        if (currentStatus != null && currentStatus.startsWith("ApprovalPending_")) {
            return updateApprovalStatusPrefix(currentStatus, "Approved") + "_" + randomId;
        }
        // Fallback: if already "Approved_", just append randomId
        if (currentStatus != null && currentStatus.startsWith("Approved_") && !currentStatus.contains("_" + randomId)) {
            return currentStatus + "_" + randomId;
        }
        return currentStatus;
    }

    private String updateApprovalStatusPrefix(String currentStatus, String newPrefix) {
        if (currentStatus != null && currentStatus.contains("_")) {
            return newPrefix + currentStatus.substring(currentStatus.indexOf("_"));
        }
        return newPrefix + "_" + Instant.now().toString();
    }

    /**
     * Sends a PATCH request to update the record status in Salesforce.
     */
    private void patchRecordStatus(String recordId, String approvalStatus) throws RequestProcessingException {
        String patchPayload = new JSONObject().put("Approval_Status__c", approvalStatus).toString();

        JWTToken token = jwtHelper.getToken();
        String updateRecordUri = getConfig().getSalesforceEndpoints().get(UPDATE_RECORD_ENDPOINT_KEY);

        String url;
        try {
            url = new URIBuilder(token.getInstanceUrl())
                    .setPath(updateRecordUri + "/" + recordId)
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new RequestProcessingException("Error building URI: " + e.getMessage());
        }

        try {
            HttpPatch httpPatch = new HttpPatch(url);
            httpPatch.setHeader("Authorization", "Bearer " + token.getAccessToken());
            httpPatch.setHeader("Content-Type", "application/json");
            HttpEntity httpEntity = new StringEntity(patchPayload, ContentType.APPLICATION_JSON);
            httpPatch.setEntity(httpEntity);

            long executeStartedAt = System.currentTimeMillis();
            LOGGER.info("RPA record status patch executing reqId={} recordId={} endpoint={} targetStatus={}",
                    RPALogContext.requestId(), recordId, RPALogContext.safeUrl(url),
                    RPALogContext.summarizeApprovalStatus(approvalStatus));

            try (CloseableHttpResponse response = httpClient.execute(httpPatch)) {
                long executeElapsedMs = System.currentTimeMillis() - executeStartedAt;
                int statusCode = response.getStatusLine().getStatusCode();
                LOGGER.info("RPA record status patch returned reqId={} recordId={} statusCode={} elapsedMs={} targetStatus={}",
                        RPALogContext.requestId(), recordId, statusCode, executeElapsedMs,
                        RPALogContext.summarizeApprovalStatus(approvalStatus));

                if (statusCode != HttpStatus.SC_OK) {
                    String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8) : "";
                    LOGGER.error(
                            "RPA record status patch failed reqId={} recordId={} statusCode={} reason={} targetStatus={} responseBody={}",
                            RPALogContext.requestId(),
                            recordId,
                            statusCode,
                            response.getStatusLine().getReasonPhrase(),
                            RPALogContext.summarizeApprovalStatus(approvalStatus),
                            responseBody);
                    throw new RequestProcessingException("Failed to update record status: " + response.getStatusLine().getReasonPhrase());
                }
            }
        } catch (IOException e) {
            LOGGER.error("RPA record status patch I/O failure reqId={} recordId={} endpoint={} targetStatus={}: {}",
                    RPALogContext.requestId(), recordId, RPALogContext.safeUrl(url),
                    RPALogContext.summarizeApprovalStatus(approvalStatus), e.getMessage(), e);
            throw new RequestProcessingException("I/O error updating record status: " + e.getMessage());
        }
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
     * The date is in ISO 8601 format.
     *
     * @param status   the approval status to use, either "Approved" or "Declined"
     * @param smeId    the SME ID to append to the status
     * @param randomId the generated random ID to append (only if provided, for final approved status)
     * @return the generated approval status string.
     *         If status is "Approved" and randomId is null, the format is:
     *         "ApprovalPending_[timestamp]_[smeId]" (interim status).
     *         If status is "Approved" and randomId is provided, the format is:
     *         "Approved_[timestamp]_[smeId]_[randomId]" (final status).
     *         If status is "Declined", the format is:
     *         "Declined_[timestamp]_[smeId]"
     * @throws InvalidRequestException if the provided status is not "Approved" or "Declined"
     */
    private String generateApprovalStatus(String status, String smeId, String randomId) throws InvalidRequestException {
        String formattedDate = Instant.now().toString();
        String approvalStatus;

        if (status != null) {
            switch (status.toLowerCase()) {
                case RECORD_APPROVED_STATUS:
                    if (randomId != null) {
                        // Final approved status with random ID
                        approvalStatus = "Approved_" + formattedDate + "_" + smeId + "_" + randomId;
                    } else {
                        // Interim status - approval pending caching
                        approvalStatus = "ApprovalPending_" + formattedDate + "_" + smeId;
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
