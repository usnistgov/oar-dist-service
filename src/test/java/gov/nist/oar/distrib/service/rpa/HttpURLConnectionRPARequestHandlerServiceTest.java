package gov.nist.oar.distrib.service.rpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecordNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.service.rpa.model.JWTToken;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;
import gov.nist.oar.distrib.service.rpa.model.UserInfo;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;
import gov.nist.oar.distrib.web.RPAConfiguration;

/**
 * This class contains unit tests for the {@link HttpURLConnectionRPARequestHandlerService} class.
 * It tests that the service works correctly and as expected.
 */

@ExtendWith(MockitoExtension.class)
public class HttpURLConnectionRPARequestHandlerServiceTest {

    private static final String TEST_ACCESS_TOKEN = "test_access_token";
    private static final String TEST_INSTANCE_URL = "https://test.salesforce.com";
    private static final String TEST_RECORD_ID = "1";
    private static final String TEST_RECORD_RESPONSE = "{\"record\":{\"id\":\"1\",\"caseNum\":\"123\"," +
            "\"userInfo\":{\"fullName\":\"John Doe\",\"organization\":\"NIST\",\"email\":\"test@test.com\"," +
            "\"receiveEmails\":\"true\",\"country\":\"USA\",\"approvalStatus\":\"Pending\",\"productTitle\":" +
            "\"Test Product\",\"subject\":\"1234567890\",\"description\":\"Test Product Description\"}}}";

    private static final String RECAPTCHA_RESPONSE = "recaptcha_response";

    @Mock
    private RPAConfiguration rpaConfiguration;

    @Mock
    private HttpURLConnection mockConnection;

    @Mock
    private CloseableHttpClient mockHttpClient;

    @Mock
    JWTHelper mockJwtHelper;

    @Mock
    RecaptchaHelper recaptchaHelper;

    @Mock
    RecordResponseHandler recordResponseHandler;

    @Mock
    RPACachingService rpaCachingService;

    @Mock
    RPADatasetCacher rpaDatasetCacher;

    private HttpURLConnectionRPARequestHandlerService service;
    private JWTToken testToken = null;
    private Map<String, String> map = new HashMap<>() {{
        put("get-record-endpoint", "/records/get");
        put("create-record-endpoint", "/records/create");
        put("update-record-endpoint", "/records/update");
    }};

    @BeforeEach
    public void setUp() {
        service = spy(new HttpURLConnectionRPARequestHandlerService(rpaConfiguration, rpaCachingService));
        service.setJWTHelper(mockJwtHelper);
        service.setRecaptchaHelper(recaptchaHelper);
        service.setHttpURLConnectionFactory(url -> mockConnection);
        service.setRecordResponseHandler(recordResponseHandler);
        service.seRPADatasetCacher(rpaDatasetCacher);
        service.setHttpClient(mockHttpClient);

        testToken = new JWTToken(TEST_ACCESS_TOKEN, TEST_INSTANCE_URL);
        when(mockJwtHelper.getToken()).thenReturn(testToken);
        when(rpaConfiguration.getSalesforceEndpoints()).thenReturn(map);
    }

    private String getGetUrl(String recordId) {
        try {
            URI uri = new URIBuilder(testToken.getInstanceUrl())
                    .setPath(rpaConfiguration.getSalesforceEndpoints().get("get-record-endpoint") + "/" + recordId)
                    .build();
            return uri.toString();
        } catch (URISyntaxException e) {
            throw new RequestProcessingException("Error building URI: " + e.getMessage());
        }
    }

    @Test
    public void getRecord_success() throws Exception {
        RecordWrapper expectedRecord = new ObjectMapper().readValue(TEST_RECORD_RESPONSE, RecordWrapper.class);
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(TEST_RECORD_RESPONSE.getBytes()));

        // Use URI for URL generation instead of the deprecated URL(String)
        URI getUri = new URI(getGetUrl(TEST_RECORD_ID));
        when(mockConnection.getURL()).thenReturn(getUri.toURL());

        RecordWrapper actualRecord = service.getRecord(TEST_RECORD_ID);

        assertEquals(expectedRecord.toString(), actualRecord.toString());
        assertEquals(getUri.toString(), mockConnection.getURL().toString());

        verify(mockConnection).setRequestMethod("GET");
        verify(mockConnection).setRequestProperty("Content-Type", "application/json");
        verify(mockConnection).setRequestProperty("Authorization", "Bearer " + testToken.getAccessToken());
        verify(mockConnection).disconnect();
    }

    @Test
    public void testGetRecord_notFound() throws Exception {
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);

        try {
            service.getRecord(TEST_RECORD_ID);
            fail("Expected RecordNotFoundException to be thrown");
        } catch (RecordNotFoundException e) {
            assertEquals("Record with ID=" + TEST_RECORD_ID + " could not be found", e.getMessage());
        }

        verify(mockConnection).disconnect();
    }

    @Test
    public void testGetRecord_internalServerError() throws Exception {
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);
        when(mockConnection.getResponseMessage()).thenReturn("Bad Request");

        try {
            service.getRecord(TEST_RECORD_ID);
            fail("Expected InternalServerErrorException to be thrown");
        } catch (RequestProcessingException e) {
            assertEquals("Error response from salesforce service: Bad Request", e.getMessage());
        }

        verify(mockConnection).disconnect();
    }

    private String getCreateRecordUrl() {
        try {
            URI uri = new URIBuilder(testToken.getInstanceUrl())
                    .setPath(rpaConfiguration.getSalesforceEndpoints().get("create-record-endpoint"))
                    .build();
            return uri.toString();
        } catch (URISyntaxException e) {
            throw new RequestProcessingException("Error building URI: " + e.getMessage());
        }
    }

    @Test
    public void testCreateRecord_success() throws InvalidRequestException, IOException {
        when(rpaConfiguration.getDisallowedEmails()).thenReturn(Arrays.asList("@disallowed\\.com$"));
        when(rpaConfiguration.getDisallowedCountries()).thenReturn(Arrays.asList("Disallowed Country"));

        String expectedResponseData = "{\"record\":{\"id\":\"5003R000003ErErQAK\","
                + "\"caseNum\":\"00228987\","
                + "\"userInfo\":{\"fullName\":\"Jane Doe\","
                + "\"organization\":\"NASA\","
                + "\"email\":\"jane.doe@test.gov\","
                + "\"receiveEmails\":\"Yes\","
                + "\"country\":\"United States\","
                + "\"approvalStatus\":\"Pending\","
                + "\"productTitle\":\"Product title\","
                + "\"subject\":\"1234\","
                + "\"description\":\"Some description goes here\""
                + "}}}";
        InputStream inputStream = new ByteArrayInputStream(expectedResponseData.getBytes());
        when(mockConnection.getInputStream()).thenReturn(inputStream);
        OutputStream osMock = mock(OutputStream.class);
        when(mockConnection.getOutputStream()).thenReturn(osMock);
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);

        URI url = null;
        try {
            url = new URI(getCreateRecordUrl());
        } catch (URISyntaxException e) {
            fail("URI Syntax Exception: " + e.getMessage());
        }

        when(mockConnection.getURL()).thenReturn(url.toURL());

        RecordWrapper testRecordWrapper = getTestRecordWrapper("Some_random_status",
                "jane.doe@test.gov", "United States");
        UserInfoWrapper userInfoWrapper = new UserInfoWrapper(
                testRecordWrapper.getRecord().getUserInfo(),
                RECAPTCHA_RESPONSE
        );

        RecordWrapper actualRecord = service.createRecord(userInfoWrapper);

        assertEquals(actualRecord.getRecord().getId(), testRecordWrapper.getRecord().getId());
        assertEquals(actualRecord.getRecord().getUserInfo().getApprovalStatus(), "Pending");

        byte[] expectedPayloadBytes = userInfoWrapper.toString().getBytes(StandardCharsets.UTF_8);
        verify(osMock).write(expectedPayloadBytes);
        verify(osMock).flush();
        verify(osMock).close();
        verify(mockConnection).setRequestMethod("POST");
        verify(mockConnection).setDoOutput(true);
        verify(mockConnection).setRequestProperty("Content-Type", "application/json");
        verify(mockConnection).setRequestProperty("Authorization", "Bearer " + testToken.getAccessToken());
        assertEquals(getCreateRecordUrl(), mockConnection.getURL().toString());

        verify(mockConnection).disconnect();
    }

    // Additional tests for record creation, update, and other edge cases can follow the same structure...

    private RecordWrapper getTestRecordWrapper(String status, String email, String country) {
        UserInfo userInfo = new UserInfo();
        userInfo.setFullName("Jane Doe");
        userInfo.setSubject("1234");
        userInfo.setCountry(country);
        userInfo.setOrganization("NASA");
        userInfo.setDescription("Some description goes here");
        userInfo.setEmail(email);
        userInfo.setProductTitle("Product title");
        userInfo.setApprovalStatus(status);
        userInfo.setReceiveEmails("Yes");
        Record record = new Record("5003R000003ErErQAK", "00228987", userInfo);
        RecordWrapper recordWrapper = new RecordWrapper(record);

        return recordWrapper;
    }
}
