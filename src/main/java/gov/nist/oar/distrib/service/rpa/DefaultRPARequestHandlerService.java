package gov.nist.oar.distrib.service.rpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.rpa.model.*;
import gov.nist.oar.distrib.web.RPAConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

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
    private RestTemplate patchRestTemplate;

    public DefaultRPARequestHandlerService(RPAConfiguration rpaConfiguration, KeyRetriever keyRetriever) {
        this.rpaConfiguration = rpaConfiguration;
        this.keyRetriever = keyRetriever;
    }

    public RPAConfiguration getConfig() { return this.rpaConfiguration; }


    @Override
    public RecordWrapper getRecord(String recordId) {
        String getRecordUri = getConfig().getSalesforceEndpoints().get(GET_RECORD_ENDPOINT_KEY);
        SalesforceToken token = getToken();
        HttpHeaders headers = getHttpHeaders(token, null);
        String url = UriComponentsBuilder.fromUriString(token.getInstanceUrl())
                .path(getRecordUri + "/" + recordId)
                .toUriString();
        ResponseEntity<RecordWrapper> response = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), RecordWrapper.class);
        return response.getBody();
    }

    @Override
    public RecordWrapper createRecord(UserInfoWrapper userInfoWrapper) {
        String createRecordUri = getConfig().getSalesforceEndpoints().get(CREATE_RECORD_ENDPOINT_KEY);
        SalesforceToken token = getToken();
        HttpHeaders headers = getHttpHeaders(token, MediaType.APPLICATION_JSON);
        HttpEntity<String> request;
        ObjectMapper mapper = new ObjectMapper();
        try {
            String payload = mapper.writeValueAsString(userInfoWrapper);
            LOGGER.debug("PAYLOAD=" + payload);
            request = new HttpEntity<>(mapper.writeValueAsString(userInfoWrapper), headers);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
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


    private void onSuccessfulRecordCreation(Record record) {
        LOGGER.info("Record created successfully! Now sending emails...");
        if (sendConfirmationEmailToEndUser(record).equals(HttpStatus.OK)) {
            LOGGER.info("Confirmation email sent to end user successfully!");
        }
        if (sendApprovalEmailToSME(record).equals(HttpStatus.OK)) {
            LOGGER.info("Request approval email sent to subject matter expert successfully!");
        }
    }

    private void onFailedRecordCreation(HttpStatus statusCode) {
        // handle failed record creation
    }


    @Override
    public RecordStatus updateRecord(String recordId, String status) {
        String updateRecordUri = getConfig().getSalesforceEndpoints().get(UPDATE_RECORD_ENDPOINT_KEY);
        HttpClient httpClient = HttpClientBuilder.create().build();
        patchRestTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));
        JSONObject updateBody = new JSONObject();
        updateBody.put("Approval_Status__c", status);
        SalesforceToken token = getToken();
        HttpHeaders headers = getHttpHeaders(token, MediaType.APPLICATION_JSON);
        String patch = updateBody.toString();
        LOGGER.debug("PATCH_DATA=" + patch);
        HttpEntity<String> request = new HttpEntity<>(patch, headers);
        String url = UriComponentsBuilder.fromUriString(token.getInstanceUrl())
                .path(updateRecordUri + "/" + recordId)
                .toUriString();
        ResponseEntity<RecordStatus> responseEntity = patchRestTemplate.exchange(
                url, HttpMethod.PATCH, request, RecordStatus.class
        );
        // check if status is approved and trigger the caching process
        LOGGER.debug("APPROVAL_STATUS=" + responseEntity.getBody().getApprovalStatus());
        if (responseEntity.getBody().getApprovalStatus().equalsIgnoreCase("approved")) {
            onEndUserApproved(recordId);
        }
        // check if status is declined and send email notification to user
        if (responseEntity.getBody().getApprovalStatus().equalsIgnoreCase("declined")) {
            onEndUserDeclined(recordId);
        }
        return responseEntity.getBody();
    }

    private HttpStatus onEndUserApproved(String recordId) {
        LOGGER.info("User was approved by SME. Starting caching...");
        Record record = getRecord(recordId).getRecord();
        // ID/subject starts with 'RPA: ', so we need to remove to extract the ID
        String datasetId = record.getUserInfo().getSubject().replace("RPA: ", "");
        String dataCartUrl = startCaching(datasetId);
        LOGGER.info("Dataset was cached successfully. Sending email to user...");
        return sendDownloadEmailToEndUser(record, dataCartUrl);
    }

    private HttpStatus onEndUserDeclined(String recordId) {
        LOGGER.info("User was declined by SME. Sending declined email to user...");
        Record record = getRecord(recordId).getRecord();
        return sendDeclinedEmailToEndUser(record);
    }

    // Function to call the caching the service to start the caching process
    // This function returns a temporary URL to the datacart that contains the cached dataset
    private String startCaching(String datasetId) {
        String url = getConfig().getPdrCachingUrl() + datasetId;
        HttpEntity<String> request = new HttpEntity<>(null, new HttpHeaders());
        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
        return responseEntity.getBody();
    }

    private HttpStatus sendApprovalEmailToSME(Record record) {
        ResponseEntity<EmailInfoWrapper> responseEntity = this.sendEmail(
                EmailHelper.getSMEApprovalEmailInfo(record, getConfig())
        );
        return responseEntity.getStatusCode();
    }

    private HttpStatus sendConfirmationEmailToEndUser(Record record) {
        if (record.getUserInfo().getReceiveEmails().equalsIgnoreCase("no")) {
            return HttpStatus.CONTINUE;
        }
        ResponseEntity<EmailInfoWrapper> responseEntity = this.sendEmail(
                EmailHelper.getEndUserConfirmationEmailInfo(record, getConfig())
        );
        return responseEntity.getStatusCode();
    }

    private HttpStatus sendDownloadEmailToEndUser(Record record, String datacartUrl) {
        if (record.getUserInfo().getReceiveEmails().equalsIgnoreCase("no")) {
            return HttpStatus.CONTINUE;
        }
        ResponseEntity<EmailInfoWrapper> responseEntity = this.sendEmail(
                EmailHelper.getEndUserDownloadEmailInfo(record, getConfig(), datacartUrl)
        );
        return responseEntity.getStatusCode();
    }

    private HttpStatus sendDeclinedEmailToEndUser(Record record) {
        if (record.getUserInfo().getReceiveEmails().equalsIgnoreCase("no")) {
            return HttpStatus.CONTINUE;
        }
        ResponseEntity<EmailInfoWrapper> responseEntity = this.sendEmail(
                EmailHelper.getEndUserDeclinedEmailInfo(record, getConfig())
        );
        return responseEntity.getStatusCode();
    }


    private ResponseEntity<EmailInfoWrapper> sendEmail(EmailInfo emailInfo) {
        String sendEmailUri = getConfig().getSalesforceEndpoints().get(SEND_EMAIL_ENDPOINT_KEY);
        SalesforceToken token = getToken();
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


    public String testSalesforceAPIConnection() {
        String testUri = getConfig().getSalesforceEndpoints().get(API_TEST_ENDPOINT_KEY);
        SalesforceToken token = getToken();
        HttpHeaders headers = getHttpHeaders(token, null);
        ResponseEntity<String> response = restTemplate.exchange(token.getInstanceUrl() + testUri,
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return response.getBody();
    }

    private HttpHeaders getHttpHeaders(SalesforceToken token, MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token.getAccessToken());
        if (mediaType != null)
            headers.setContentType(mediaType);
        return headers;
    }

    // JWT methods
    private SalesforceToken getToken() {
        return sendTokenRequest(createAssertion());
    }

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

    private SalesforceToken sendTokenRequest(String assertion) {
        String url = UriComponentsBuilder.fromUriString(getConfig().getSalesforceInstanceUrl())
                .path("/services/oauth2/token")
                .queryParam("grant_type", getConfig().getSalesforceJwt().getGrantType())
                .queryParam("assertion", assertion)
                .toUriString();
        LOGGER.info("Token request URL = " + url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return restTemplate.postForObject(url, new HttpEntity<>(null, headers), SalesforceToken.class);
    }


}
