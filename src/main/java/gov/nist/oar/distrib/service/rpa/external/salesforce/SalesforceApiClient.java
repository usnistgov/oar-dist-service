package gov.nist.oar.distrib.service.rpa.external.salesforce;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.rpa.JWTHelper;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.service.rpa.external.ExternalApiClient;
import gov.nist.oar.distrib.service.rpa.external.ExternalApiException;
import gov.nist.oar.distrib.service.rpa.external.ExternalCreateRecordPayload;
import gov.nist.oar.distrib.service.rpa.external.ExternalCreateRecordResponse;
import gov.nist.oar.distrib.service.rpa.external.ExternalGetRecordResponse;
import gov.nist.oar.distrib.service.rpa.external.ExternalUpdateRecordPayload;
import gov.nist.oar.distrib.service.rpa.external.ExternalUpdateRecordResponse;
import gov.nist.oar.distrib.service.rpa.model.JWTToken;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class SalesforceApiClient implements ExternalApiClient, SalesforceEndpointKeys {

    private final static Logger LOGGER = LoggerFactory.getLogger(SalesforceApiClient.class);

    private final static String RECORD_PENDING_STATUS = "pending";
    private final static String RECORD_APPROVED_STATUS = "approved";
    private final static String RECORD_DECLINED_STATUS = "declined";

    private final CloseableHttpClient httpClient;
    private final JWTHelper jwtHelper;

    private RPAConfiguration rpaConfiguration;

    public SalesforceApiClient(RPAConfiguration rpaConfiguration) {
        this.jwtHelper = JWTHelper.getInstance();
        this.rpaConfiguration = rpaConfiguration;
        this.httpClient = HttpClients.createDefault();
    }

    public SalesforceApiClient(JWTHelper jwtHelper, RPAConfiguration rpaConfiguration) {
        this.jwtHelper = jwtHelper;
        this.rpaConfiguration = rpaConfiguration;
        this.httpClient = HttpClients.createDefault();
    }

    public SalesforceApiClient(JWTHelper jwtHelper, RPAConfiguration rpaConfiguration, CloseableHttpClient httpClient) {
        this.jwtHelper = jwtHelper;
        this.rpaConfiguration = rpaConfiguration;
        this.httpClient = httpClient;
    }

    @Override
    public ExternalGetRecordResponse getRecordById(String recordId) throws ExternalApiException {
        // Get endpoint
        String getRecordUri = rpaConfiguration.getSalesforceEndpoints().get(GET_RECORD_ENDPOINT_KEY);
        // Get token
        JWTToken token = jwtHelper.getToken();
        // Build request URL
        String url;
        try {
            url = new URIBuilder(token.getInstanceUrl())
                    .setPath(getRecordUri + "/" + recordId)
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new RequestProcessingException("Error building URI: " + e.getMessage());
        }
        LOGGER.debug("GET_RECORD_URL " + url);
        // Create GET request object
        HttpGet request = new HttpGet(url);
        // Set Authorization header
        request.setHeader("Authorization", "Bearer " + jwtHelper.getToken().getAccessToken());
        SalesforceGetRecordResponse payload;
        int statusCode = 0;
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            if (statusCode == HttpStatus.SC_OK) {
                payload = new ObjectMapper().readValue(responseBody, SalesforceGetRecordResponse.class);
            } else {
                LOGGER.debug("Failed to get record: " + response.getStatusLine().getReasonPhrase());
                throw new SalesforceApiException(statusCode, "Failed to get record: " + responseBody);
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to get record: " + e.getStackTrace());
            throw new SalesforceApiException(statusCode, "Failed to get record: " + e.getMessage());
        }
        return payload;
    }

    @Override
    public ExternalUpdateRecordResponse updateRecordById(String recordId, ExternalUpdateRecordPayload requestPayload) throws ExternalApiException {
        SalesforceUpdateRecordResponse updateRecordResponse;
        // Get endpoint
        String updateRecordUri = rpaConfiguration.getSalesforceEndpoints().get(UPDATE_RECORD_ENDPOINT_KEY);
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
        // Send PATCH request
        try {
            HttpPatch httpPatch = new HttpPatch(url);
            httpPatch.setHeader("Authorization", "Bearer " + token.getAccessToken());
            httpPatch.setHeader("Content-Type", "application/json");
            HttpEntity httpEntity = new StringEntity(requestPayload.toString(), ContentType.APPLICATION_JSON);
            httpPatch.setEntity(httpEntity);

            CloseableHttpResponse response = httpClient.execute(httpPatch);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == HttpStatus.SC_OK) { // If success
                try (InputStream inputStream = response.getEntity().getContent()) {
                    String responseString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                    // Handle the response
                    updateRecordResponse = new ObjectMapper().readValue(responseString,
                            SalesforceUpdateRecordResponse.class);
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
        return updateRecordResponse;
    }

    @Override
    public ExternalCreateRecordResponse createRecord(ExternalCreateRecordPayload requestPayload) throws ExternalApiException {
        SalesforceCreateRecordResponse response = new SalesforceCreateRecordResponse();
        // Get path
        String createRecordUri = rpaConfiguration.getSalesforceEndpoints().get(CREATE_RECORD_ENDPOINT_KEY);
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
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Authorization", "Bearer " + token.getAccessToken());
        httpPost.setHeader("Content-Type", "application/json");

        // Set payload
        String postPayload;
        try {
            postPayload = new ObjectMapper().writeValueAsString(requestPayload);
        } catch (JsonProcessingException e) {
            LOGGER.debug("Error while serializing user input: " + e.getMessage());
            throw new RequestProcessingException("Error while serializing user input: " + e.getMessage());
        }
        StringEntity entity = new StringEntity(postPayload, StandardCharsets.UTF_8);
        httpPost.setEntity(entity);

        CloseableHttpResponse httpResponse = null;
        try {
            // Execute the request
            httpResponse = httpClient.execute(httpPost);

            int responseCode = httpResponse.getStatusLine().getStatusCode();

            if (responseCode == 200) { // If OK
                HttpEntity responseEntity = httpResponse.getEntity();
                if (responseEntity != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(responseEntity.getContent(), StandardCharsets.UTF_8))) {
                        StringBuilder responseStringBuilder = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseStringBuilder.append(line);
                        }
                        String responseString = responseStringBuilder.toString();
                        LOGGER.debug("CREATE_RECORD_RESPONSE=" + responseString);
                        // Handle the response
                        response = new ObjectMapper().readValue(responseString, SalesforceCreateRecordResponse.class);
                    }
                }
            } else if (responseCode == 400) { // If bad request
                LOGGER.debug("Invalid request: " + httpResponse.getStatusLine().getReasonPhrase());
                throw new InvalidRequestException("Invalid request: " + httpResponse.getStatusLine().getReasonPhrase());
            } else {
                // Handle any other error response
                LOGGER.debug("Error response from Salesforce service: " + httpResponse.getStatusLine().getReasonPhrase());
                throw new RequestProcessingException("Error response from Salesforce service: " + httpResponse.getStatusLine().getReasonPhrase());
            }
        } catch (IOException e) {
            // Handle the I/O error
            LOGGER.debug("Error sending POST request: " + e.getMessage());
            throw new RequestProcessingException("I/O error: " + e.getMessage());
        } finally {
            // Close the http response
            if (response != null) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    LOGGER.debug("Error closing response: " + e.getMessage());
                }
            }
        }
        return response;
    }

    /**
     * Generates an approval status string based on the given status and current date/time.
     * The date is in ISO 8601 format.
     *
     * @param status the approval status to use, either "Approved" or "Declined"
     * @return the generated approval status string, in the format "[status]_[yyyy-MM-dd'T'HH:mm:ss.SSSZ]"
     * @throws InvalidRequestException if the provided status is not "Approved" or "Declined"
     */
    private String generateApprovalStatus(String status) throws InvalidRequestException {
        String formattedDate = Instant.now().toString(); // ISO 8601 format: 2023-05-09T15:59:03.872Z
        String approvalStatus;
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
        return approvalStatus + formattedDate;
    }


}