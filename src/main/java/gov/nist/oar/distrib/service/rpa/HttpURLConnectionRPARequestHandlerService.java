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

        // Set the pending status of the record in this service, and not based on the user's input
        userInfoWrapper.getUserInfo().setApprovalStatus(RECORD_PENDING_STATUS);

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
            this.recordResponseHandler.onRecordCreationSuccess(newRecordWrapper.getRecord());
        } else {
            // we expect a record to be created every time we call createRecord
            // if newRecordWrapped is null, it means creation failed
            this.recordResponseHandler.onRecordCreationFailure(responseCode);
        }

        return newRecordWrapper;
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
