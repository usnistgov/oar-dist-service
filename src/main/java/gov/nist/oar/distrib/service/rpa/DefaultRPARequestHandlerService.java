package gov.nist.oar.distrib.service.rpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.rpa.exceptions.FailedRecordUpdateException;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRecaptchaException;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecordNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.UnauthorizedException;
import gov.nist.oar.distrib.service.rpa.model.EmailInfo;
import gov.nist.oar.distrib.service.rpa.model.EmailInfoWrapper;
import gov.nist.oar.distrib.service.rpa.model.RecaptchaResponse;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.service.rpa.model.RecordStatus;
import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;
import gov.nist.oar.distrib.service.rpa.model.JWTToken;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;
import gov.nist.oar.distrib.web.RPAConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Default implementation of the RPARequestHandlerService.
 */
@RequiredArgsConstructor
public class DefaultRPARequestHandlerService implements RPARequestHandlerService {

    private final static Logger LOGGER = LoggerFactory.getLogger(DefaultRPARequestHandlerService.class);
    private final static String API_TEST_ENDPOINT_KEY = "api-test-endpoint";
    private final static String GET_RECORD_ENDPOINT_KEY = "get-record-endpoint";
    private final static String CREATE_RECORD_ENDPOINT_KEY = "create-record-endpoint";
    private final static String UPDATE_RECORD_ENDPOINT_KEY = "update-record-endpoint";
    private final static String SEND_EMAIL_ENDPOINT_KEY = "send-email-endpoint";

    private RPAConfiguration rpaConfiguration = null;
    private KeyRetriever keyRetriever = null;
    private RestTemplate restTemplate;

    /**
     * Constructs a new DefaultRPARequestHandlerService object with the given rpaConfiguration and keyRetriever.
     * @param rpaConfiguration The Restricted Public Access (RPA) configuration object
     * @param keyRetriever The private key retriever object
     */
    public DefaultRPARequestHandlerService(RPAConfiguration rpaConfiguration, RestTemplate restTemplate) {
        this.rpaConfiguration = rpaConfiguration;
        this.keyRetriever = new JKSKeyRetriever();
        this.restTemplate = new RestTemplate();
        // We need to include HttpComponentsClientHttpRequestFactory because The standard JDK HTTP library
        // does not support HTTP PATCH. We need to use the Apache HttpComponents or OkHttp request factory.
        HttpClient httpClient = HttpClientBuilder.create().build();
        this.restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));

        LOGGER.debug("RPA_CONFIGURATION=" + this.rpaConfiguration.toString());
    }

    /**
     * Returns the RPA configuration. This is a bean that is injected into the service.
     * The RPA configuration is loaded from the config server.
     *
     * @return The RPA configuration
     */
    public RPAConfiguration getConfig() { return this.rpaConfiguration; }


    /**
     * Get information about a specific record.
     *
     * This asks for an access token, then uses it to add the bearer auth http header.
     * Constructs the URL, and sends a GET request to fetch information about the record.
     *
     * @param recordId  the identifier for the record.
     *
     * @exception UnauthorizedException if there is an issue with the access token.
     * @exception RecordNotFoundException if record is not found.
     *
     * @return RecordWrapper -- the requested record wrapped within a "record" envelope.
     */
    @Override
    public RecordWrapper getRecord(String recordId) throws RecordNotFoundException, UnauthorizedException {
        String getRecordUri = getConfig().getSalesforceEndpoints().get(GET_RECORD_ENDPOINT_KEY);
        JWTToken token = getToken();
        HttpHeaders headers = getHttpHeaders(token, null);
        String url = UriComponentsBuilder.fromUriString(token.getInstanceUrl())
                .path(getRecordUri + "/" + recordId)
                .toUriString();
        ResponseEntity<RecordWrapper> response = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), RecordWrapper.class);
        if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw RecordNotFoundException.forID(recordId);
        }
        return response.getBody();
    }

    /**
     * Create a new record.
     *
     * This asks for an access token, then uses it to add the bearer auth http header.
     * Constructs the URL, and sends a POST request to create a new record.
     *
     * @param userInfoWrapper  the information provided by the user.
     *
     * @exception UnauthorizedException if there is an issue with the access token.
     * @exception InvalidRecaptchaException if there is an issue processing the recaptcha.
     * @exception InvalidRequestException if there is an issue with the request fields.
     *
     * @return RecordWrapper -- the newly created record wrapped within a "record" envelope.
     */
    @Override
    public RecordWrapper createRecord(UserInfoWrapper userInfoWrapper)
            throws InvalidRecaptchaException, InvalidRequestException, UnauthorizedException {

        String createRecordUri = getConfig().getSalesforceEndpoints().get(CREATE_RECORD_ENDPOINT_KEY);
        JWTToken token = getToken();
        HttpHeaders headers = getHttpHeaders(token, MediaType.APPLICATION_JSON);
        HttpEntity<String> request;
        ObjectMapper mapper = new ObjectMapper();

        // first check if recaptcha is valid
        RecaptchaResponse recaptchaResponse = verifyRecaptcha(userInfoWrapper.getRecaptcha());
        if (!recaptchaResponse.isSuccess()) {
            throw new InvalidRecaptchaException("failed to verify the reCaptcha: " + recaptchaResponse.getErrorCodes());
        }
        try {
            // cleaning form input from any HTML
            UserInfoWrapper cleanUserInfoWrapper = HTMLCleaner.clean(userInfoWrapper);
            String payload = mapper.writeValueAsString(cleanUserInfoWrapper);
            LOGGER.debug("PAYLOAD=" + payload);
            request = new HttpEntity<>(payload, headers);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("could not clean form fields: " + e.getMessage());
        }
        String url = token.getInstanceUrl() + createRecordUri;
        ResponseEntity<RecordWrapper> responseEntity = restTemplate.exchange(url, HttpMethod.POST, request, RecordWrapper.class);
        if (responseEntity.getStatusCode().equals(HttpStatus.OK)) {
            onSuccessfulRecordCreation(responseEntity.getBody().getRecord());
        } else {
            onFailedRecordCreation(responseEntity.getStatusCode());
        }
        return responseEntity.getBody();
    }

    /**
     * Function to verify the recaptcha token.
     */
    private RecaptchaResponse verifyRecaptcha(String token) {
        String url = UriComponentsBuilder.fromUriString("https://www.google.com")
                .path("/recaptcha/api/siteverify")
                .queryParam("secret", getConfig().getRecaptchaSecret())
                .queryParam("response", token)
                .toUriString();

        LOGGER.debug("RECAPTCHA_URL=" + url);
        HttpHeaders headers = new HttpHeaders();
        return restTemplate.postForObject(url, new HttpEntity<>(null, headers), RecaptchaResponse.class);
    }


    /**
     * On successful creation of a record, send a confirmation email to the user, and another email to the approver.
     * Check if sending of emails was successful.
     */
    private void onSuccessfulRecordCreation(Record record) throws UnauthorizedException {
        LOGGER.debug("Record created successfully! Now sending emails...");
        if (sendConfirmationEmailToEndUser(record).equals(HttpStatus.OK)) {
            LOGGER.debug("Confirmation email sent to end user successfully!");
        }
        if (sendApprovalEmailToSME(record).equals(HttpStatus.OK)) {
            LOGGER.debug("Request approval email sent to subject matter expert successfully!");
        }
    }

    /**
     * On failed creation of a record
     * TODO: add logic when creation fails
     */
    private void onFailedRecordCreation(HttpStatus statusCode) {
        // handle failed record creation
    }


    /**
     * Update the status of a specific record.
     *
     * This asks for an access token, then uses it to add the bearer auth http header.
     * Constructs the URL, and sends a PATCH request to update a record.
     *
     * @param recordId  the identifier for the record.
     * @param status  the new status.
     *
     * @exception UnauthorizedException if there is an issue with the access token.
     * @exception RecordNotFoundException if record is not found.
     * @exception FailedRecordUpdateException if the record update failed.
     *
     * @return RecordStatus -- the updated status of the record.
     */
    @Override
    public RecordStatus updateRecord(String recordId, String status)
            throws RecordNotFoundException, UnauthorizedException, FailedRecordUpdateException {

        String updateRecordUri = getConfig().getSalesforceEndpoints().get(UPDATE_RECORD_ENDPOINT_KEY);
        JSONObject updateBody = new JSONObject();
        updateBody.put("Approval_Status__c", status);
        JWTToken token = getToken();
        HttpHeaders headers = getHttpHeaders(token, MediaType.APPLICATION_JSON);
        String patch = updateBody.toString();
        LOGGER.debug("PATCH_DATA=" + patch);
        HttpEntity<String> request = new HttpEntity<>(patch, headers);
        String url = UriComponentsBuilder.fromUriString(token.getInstanceUrl())
                .path(updateRecordUri + "/" + recordId)
                .toUriString();
        LOGGER.debug("UPDATE_URL=" + url);
        ResponseEntity<RecordStatus> responseEntity = null;
        try {
            responseEntity= restTemplate.exchange(
                    url, HttpMethod.PATCH, request, RecordStatus.class
            );
        } catch (HttpStatusCodeException e) {
            LOGGER.debug("failed to update record: " + e.getResponseBodyAsString());
            throw FailedRecordUpdateException.forID(recordId);
        }

        // check if status is approved and trigger the caching process
        LOGGER.debug("APPROVAL_STATUS=" + responseEntity.getBody().getApprovalStatus());
        if (responseEntity.getBody().getApprovalStatus().toLowerCase().contains("approved")) {
            onEndUserApproved(recordId);
        }
        // check if status is declined and send email notification to user
        if (responseEntity.getBody().getApprovalStatus().toLowerCase().contains("declined")) {
            // Don't do anything if User is declined
            // onEndUserDeclined(recordId);
        }
        return responseEntity.getBody();
    }

    private HttpStatus onEndUserApproved(String recordId) throws RecordNotFoundException, UnauthorizedException {
        LOGGER.info("User was approved by SME. Starting caching...");
        Record record = getRecord(recordId).getRecord();
        String datasetId = record.getUserInfo().getSubject();
        String randomId = startCaching(datasetId);
        String downloadUrl = UriComponentsBuilder.fromUriString(getConfig().getDatacartUrl())
                .queryParam("id", randomId)
                .toUriString();
        LOGGER.info("Dataset was cached successfully. Sending email to user...");
        return sendDownloadEmailToEndUser(record, downloadUrl);
    }

    private HttpStatus onEndUserDeclined(String recordId) throws RecordNotFoundException, UnauthorizedException {
        LOGGER.info("User was declined by SME. Sending declined email to user...");
        Record record = getRecord(recordId).getRecord();
        return sendDeclinedEmailToEndUser(record);
    }

    // Function to call the caching the service to start the caching process
    // This function returns a temporary URL to the datacart that contains the cached dataset
    private String startCaching(String datasetId) {
        String url = getConfig().getPdrCachingUrl() + "/cache/" + datasetId;
        HttpEntity<String> request = new HttpEntity<>(null, new HttpHeaders());
        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
        return responseEntity.getBody();
    }

    private HttpStatus sendApprovalEmailToSME(Record record) throws UnauthorizedException {
        EmailInfo emailInfo = EmailHelper.getSMEApprovalEmailInfo(record, getConfig());
        LOGGER.debug("EMAIL_INFO=" + emailInfo);
        ResponseEntity<EmailInfoWrapper> responseEntity = this.sendEmail(emailInfo);
        return responseEntity.getStatusCode();
    }

    private HttpStatus sendConfirmationEmailToEndUser(Record record) throws UnauthorizedException {
        EmailInfo emailInfo = EmailHelper.getEndUserConfirmationEmailInfo(record, getConfig());
        LOGGER.debug("EMAIL_INFO=" + emailInfo);
        ResponseEntity<EmailInfoWrapper> responseEntity = this.sendEmail(emailInfo);
        return responseEntity.getStatusCode();
    }

    private HttpStatus sendDownloadEmailToEndUser(Record record, String downloadUrl) throws UnauthorizedException {
        EmailInfo emailInfo = EmailHelper.getEndUserDownloadEmailInfo(record, getConfig(), downloadUrl);
        LOGGER.info("EMAIL_INFO=" + emailInfo);
        ResponseEntity<EmailInfoWrapper> responseEntity = this.sendEmail(emailInfo);
        return responseEntity.getStatusCode();
    }

    private HttpStatus sendDeclinedEmailToEndUser(Record record) throws UnauthorizedException {
        EmailInfo emailInfo = EmailHelper.getEndUserDeclinedEmailInfo(record, getConfig());
        LOGGER.debug("EMAIL_INFO=" + emailInfo);
        ResponseEntity<EmailInfoWrapper> responseEntity = this.sendEmail(emailInfo);
        return responseEntity.getStatusCode();
    }


    private ResponseEntity<EmailInfoWrapper> sendEmail(EmailInfo emailInfo) throws UnauthorizedException {
        String sendEmailUri = getConfig().getSalesforceEndpoints().get(SEND_EMAIL_ENDPOINT_KEY);
        JWTToken token = getToken();
        HttpHeaders headers = getHttpHeaders(token, MediaType.APPLICATION_JSON);
        ObjectMapper mapper = new ObjectMapper();
        HttpEntity<String> request;
        try {
            request = new HttpEntity<>(mapper.writeValueAsString(emailInfo), headers);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        String url = token.getInstanceUrl() + sendEmailUri;
        return restTemplate.exchange(url, HttpMethod.POST, request, EmailInfoWrapper.class);
    }


    /**
     * Test connection to Salesforce service to make sure the Connected App is working.
     */
    public String testSalesforceAPIConnection() throws UnauthorizedException {
        String testUri = getConfig().getSalesforceEndpoints().get(API_TEST_ENDPOINT_KEY);
        JWTToken token = getToken();
        HttpHeaders headers = getHttpHeaders(token, null);
        ResponseEntity<String> response = restTemplate.exchange(token.getInstanceUrl() + testUri,
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return response.getBody();
    }

    private HttpHeaders getHttpHeaders(JWTToken token, MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token.getAccessToken());
        if (mediaType != null)
            headers.setContentType(mediaType);
        return headers;
    }

    // JWT methods

    // Retrieve the JWT Access Token
    private JWTToken getToken() throws UnauthorizedException {
        return sendTokenRequest(createAssertion());
    }

    // Create the jwt assertion.
    private String createAssertion() {
        LocalDateTime localDateTime = LocalDateTime.now().plusMinutes(getConfig().getSalesforceJwt().getExpirationInMinutes());
        return Jwts.builder()
                .setIssuer(getConfig().getSalesforceJwt().getClientId())
                .setAudience(getConfig().getSalesforceJwt().getAudience())
                .setSubject(getConfig().getSalesforceJwt().getSubject())
                .setExpiration(Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant()))
                .signWith(SignatureAlgorithm.RS256, keyRetriever.getKey(getConfig()))
                .compact();
    }

    /**
     * Send token request as per <a href="https://www.rfc-editor.org/rfc/rfc7523">rfc7523</a>.
     *
     * @param assertion - the assertion token containing a JWT token
     *
     * @return JWTToken - the access token
     */
    private JWTToken sendTokenRequest(String assertion) throws UnauthorizedException {
        String url = UriComponentsBuilder.fromUriString(getConfig().getSalesforceInstanceUrl())
                .path("/services/oauth2/token")
                .queryParam("grant_type", getConfig().getSalesforceJwt().getGrantType())
                .queryParam("assertion", assertion)
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<JWTToken> response;
        try {
            response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(null, headers),
                    JWTToken.class
            );
        } catch (HttpStatusCodeException e) {
            LOGGER.debug("access token request is invalid: " + e.getResponseBodyAsString());
            throw new UnauthorizedException("access token request is invalid: " + e.getResponseBodyAsString());
        }
        return response.getBody();
    }


}
