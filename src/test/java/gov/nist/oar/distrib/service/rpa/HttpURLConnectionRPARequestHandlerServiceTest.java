package gov.nist.oar.distrib.service.rpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecordNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.service.rpa.model.JWTToken;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.service.rpa.model.RecordStatus;
import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;
import gov.nist.oar.distrib.service.rpa.model.UserInfo;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This class contains unit tests for the {@link HttpURLConnectionRPARequestHandlerService} class.
 * It tests that the service works correctly and as expected.
 * <p>
 * The tests mock the necessary dependencies and interactions with external systems
 * using the Mockito framework.
 * <p>
 * The tests cover the following scenarios:
 * <p>
 * * {@link IRPARequestHandler#createRecord(UserInfoWrapper, String)}
 * <ul>
 *     <li>Success case: creating a new record (200 ok)</li>
 *     <li>Failure case: invalid request (400 bad request)</li>
 *     <li>Failure case: error processing request (500 internal server error)</li>
 * </ul>
 * * {@link HttpURLConnectionRPARequestHandlerService#getRecord(String)}
 * <ul>
 *     <li>Success case: getting a record (200 ok)</li>
 *     <li>Failure case: invalid request (400 bad request)</li>
 *     <li>Failure case: error processing request (500 internal server error)</li>
 * </ul>
 * * {@link HttpURLConnectionRPARequestHandlerService#updateRecord(String, String)}
 * <ul>
 *     <li>Success case: update recaptcha response and update record (200 ok)</li>
 *     <li>Failure case: invalid request (400 bad request)</li>
 *     <li>Failure case: record not found (404 not found)</li>
 *     <li>Failure case: error processing request (500 internal server error)</li>
 * </ul>
 * <p>
 * Note: All tests should pass if the service is working correctly.
 */

@RunWith(MockitoJUnitRunner.class)
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

    private HttpURLConnectionRPARequestHandlerService service;
    JWTToken testToken = null;
    Map<String, String> map = new HashMap<String, String>() {{
        put("get-record-endpoint", "/records/get");
        put("create-record-endpoint", "/records/create");
        put("update-record-endpoint", "/records/update");
    }};

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        service = spy(new HttpURLConnectionRPARequestHandlerService(rpaConfiguration, rpaCachingService));
        service.setJWTHelper(mockJwtHelper);
        service.setRecaptchaHelper(recaptchaHelper);
        service.setHttpURLConnectionFactory(url -> mockConnection);
        service.setRecordResponseHandler(recordResponseHandler);
        service.setHttpClient(mockHttpClient);

        // Set up mock behavior for mockJwtHelper
        testToken = new JWTToken(TEST_ACCESS_TOKEN, TEST_INSTANCE_URL);
        when(mockJwtHelper.getToken()).thenReturn(testToken);

        // Set Endpoints
        when(rpaConfiguration.getSalesforceEndpoints()).thenReturn(map);
    }

    private String getGetUrl(String recordId) {
        // Build the URL used by get request
        String url;
        try {
            url = new URIBuilder(testToken.getInstanceUrl())
                    .setPath(rpaConfiguration.getSalesforceEndpoints().get("get-record-endpoint") + "/" + recordId)
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new RequestProcessingException("Error building URI: " + e.getMessage());
        }
        return url;
    }

    @Test
    public void getRecord_success() throws Exception {
        // Arrange
        RecordWrapper expectedRecord = new ObjectMapper().readValue(TEST_RECORD_RESPONSE, RecordWrapper.class);

        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(TEST_RECORD_RESPONSE.getBytes()));
        // Set URL to return, similar to the one used inside getRecord
        URL url = new URL(getGetUrl(TEST_RECORD_ID));
        when(mockConnection.getURL()).thenReturn(url);

        // Act
        RecordWrapper actualRecord = service.getRecord(TEST_RECORD_ID);

        // Assert
        assertEquals(expectedRecord.toString(), actualRecord.toString());
        assertEquals("https://test.salesforce.com/records/get/1", mockConnection.getURL().toString());

        // Verify
        // verify right headers were set
        verify(mockConnection).setRequestMethod("GET");
        verify(mockConnection).setRequestProperty("Content-Type", "application/json");
        verify(mockConnection).setRequestProperty("Authorization", "Bearer " + testToken.getAccessToken());
        // verify that connection was closed
        verify(mockConnection).disconnect();

    }

    @Test
    public void testGetRecord_notFound() throws Exception {
        // Set up mock behavior for mockConnection
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);

        // Verify exception is thrown
        try {
            // Call method to test
            RecordWrapper actualRecord = service.getRecord(TEST_RECORD_ID);
            fail("Expected RecordNotFoundException to be thrown");
        } catch (RecordNotFoundException e) {
            // Verify exception message is correct
            assertEquals("Record with ID=" + TEST_RECORD_ID + " could not be found", e.getMessage());
        }

        // Verify connection is closed
        verify(mockConnection).disconnect();
    }

    @Test
    public void testGetRecord_internalServerError() throws Exception {
        // Arrange
        // Set up mock behavior for mockConnection
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);
        when(mockConnection.getResponseMessage()).thenReturn("Bad Request");


        try {
            // Act
            // Call method to test
            service.getRecord(TEST_RECORD_ID);
            fail("Expected InternalServerErrorException to be thrown");
        } catch (RequestProcessingException e) {
            // Assert exception message is correct
            assertEquals("Error response from salesforce service: Bad Request", e.getMessage());
        }

        // Verify connection is closed
        verify(mockConnection).disconnect();
    }

    private String getCreateRecordUrl() {
        // Build the URL used by post request
        String url;
        try {
            url = new URIBuilder(testToken.getInstanceUrl())
                    .setPath(rpaConfiguration.getSalesforceEndpoints().get("create-record-endpoint"))
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new RequestProcessingException("Error building URI: " + e.getMessage());
        }
        return url;
    }


    @Test
    public void testCreateRecord_success()
            throws InvalidRequestException, IOException {
        // Arrange
        // Mock the RPAConfiguration to return non-blacklisted email strings and countries
        when(rpaConfiguration.getDisallowedEmails()).thenReturn(Arrays.asList("@disallowed\\.com$"));
        when(rpaConfiguration.getDisallowedCountries()).thenReturn(Arrays.asList("Disallowed Country"));

        // Set up mock behavior for mockConnection
        // Set expected dummy response
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
        // Create an input stream with the dummy data
        InputStream inputStream = new ByteArrayInputStream(expectedResponseData.getBytes());
        // When getInputStream() is called on the mockConnection, return the dummy input stream
        when(mockConnection.getInputStream()).thenReturn(inputStream);
        // Create a mock output stream for the connection
        OutputStream osMock = mock(OutputStream.class);
        // Set up the mock to return the mock output stream when getOutputStream is called
        when(mockConnection.getOutputStream()).thenReturn(osMock);
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        URL url = new URL(getCreateRecordUrl());
        when(mockConnection.getURL()).thenReturn(url);
        // Call method under test

        RecordWrapper testRecordWrapper = getTestRecordWrapper("Some_random_status"
                , "jane.doe@test.gov", "United States");
        UserInfoWrapper userInfoWrapper = new UserInfoWrapper(
                testRecordWrapper.getRecord().getUserInfo(),
                RECAPTCHA_RESPONSE,
                new HashMap<>()
        );

        // Act
        RecordWrapper actualRecord = service.createRecord(userInfoWrapper);

        // Assert
        assertEquals(actualRecord.getRecord().getId(), testRecordWrapper.getRecord().getId());
        // Assert that the status was set by the server and not the user's input
        assertEquals(actualRecord.getRecord().getUserInfo().getApprovalStatus(), "Pending");
        assertEquals("United States", actualRecord.getRecord().getUserInfo().getCountry());
        assertEquals("jane.doe@test.gov", actualRecord.getRecord().getUserInfo().getEmail());

        // Verify that the mock output stream was written to as expected
        byte[] expectedPayloadBytes = userInfoWrapper.toString().getBytes(StandardCharsets.UTF_8);
        verify(osMock).write(expectedPayloadBytes);
        verify(osMock).flush();
        verify(osMock).close();
        verify(mockConnection).setRequestMethod("POST");
        verify(mockConnection).setDoOutput(true);
        verify(mockConnection).setRequestProperty("Content-Type", "application/json");
        verify(mockConnection).setRequestProperty("Authorization", "Bearer " + testToken.getAccessToken());
        assertEquals(getCreateRecordUrl(), mockConnection.getURL().toString());

        // Verify connection was closed
        verify(mockConnection).disconnect();

    }

    @Test(expected = InvalidRequestException.class)
    public void testCreateRecord_withBlacklistedEmail_shouldThrowInvalidRequestException() throws Exception {
        // Arrange
        RecordWrapper testRecordWrapper = getTestRecordWrapper("Some_random_status"
                , "jane.doe@123.com", "United States");
        UserInfoWrapper userInfoWrapper = new UserInfoWrapper(
                testRecordWrapper.getRecord().getUserInfo(),
                RECAPTCHA_RESPONSE,
                new HashMap<>()
        );

        when(rpaConfiguration.getDisallowedEmails()).thenReturn(Arrays.asList("@123\\."));

        // Act
        service.createRecord(userInfoWrapper);
    }

    @Test(expected = InvalidRequestException.class)
    public void testCreateRecord_withBlacklistedCountry_shouldThrowInvalidRequestException() throws Exception {
        // Arrange
        RecordWrapper testRecordWrapper = getTestRecordWrapper("Some_random_status"
                , "jane.doe@test.gov", "Cuba");
        UserInfoWrapper userInfoWrapper = new UserInfoWrapper(
                testRecordWrapper.getRecord().getUserInfo(),
                RECAPTCHA_RESPONSE,
                new HashMap<>()
        );
        // Mocking blacklisted country
        when(rpaConfiguration.getDisallowedEmails()).thenReturn(Arrays.asList("@123\\.", "@gmail\\.com"));
        when(rpaConfiguration.getDisallowedCountries()).thenReturn(Arrays.asList("Cuba"));

        // Act
        service.createRecord(userInfoWrapper);
    }

    @Test
    public void testCreateRecord_withAuthorizedUser_shouldSucceed()
            throws InvalidRequestException, IOException {
        // Recaptcha stubbings ar no longer needed here since we skip verification

        // Set up mock behavior for mockConnection
        // Set expected dummy response
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
        // Create an input stream with the dummy data
        InputStream inputStream = new ByteArrayInputStream(expectedResponseData.getBytes());
        // When getInputStream() is called on the mockConnection, return the dummy input stream
        when(mockConnection.getInputStream()).thenReturn(inputStream);
        // Create a mock output stream for the connection
        OutputStream osMock = mock(OutputStream.class);
        // Set up the mock to return the mock output stream when getOutputStream is called
        when(mockConnection.getOutputStream()).thenReturn(osMock);
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        URL url = new URL(getCreateRecordUrl());
        when(mockConnection.getURL()).thenReturn(url);
        // Call method under test

        RecordWrapper testRecordWrapper = getTestRecordWrapper("Some_random_status"
                , "jane.doe@test.gov", "United States");
        UserInfoWrapper userInfoWrapper = new UserInfoWrapper(
                testRecordWrapper.getRecord().getUserInfo(),
                RECAPTCHA_RESPONSE
        );

        // Act
        RecordWrapper actualRecord = service.createRecord(userInfoWrapper);

        // Assert
        assertEquals(actualRecord.getRecord().getId(), testRecordWrapper.getRecord().getId());
        // Assert that the status was set by the server and not the user's input
        assertEquals(actualRecord.getRecord().getUserInfo().getApprovalStatus(), "Pending");

        // Verify that the mock output stream was written to as expected
        byte[] expectedPayloadBytes = userInfoWrapper.toString().getBytes(StandardCharsets.UTF_8);
        verify(osMock).write(expectedPayloadBytes);
        verify(osMock).flush();
        verify(osMock).close();
        verify(mockConnection).setRequestMethod("POST");
        verify(mockConnection).setDoOutput(true);
        verify(mockConnection).setRequestProperty("Content-Type", "application/json");
        verify(mockConnection).setRequestProperty("Authorization", "Bearer " + testToken.getAccessToken());
        assertEquals(getCreateRecordUrl(), mockConnection.getURL().toString());

        // Verify connection was closed
        verify(mockConnection).disconnect();

    }

    @Test
    public void testCreateRecord_with_NON_AuthorizedUser_shouldSucceed()
            throws InvalidRequestException, IOException {
        // Arrange

        // Set up mock behavior for mockConnection
        // Set expected dummy response
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
        // Create an input stream with the dummy data
        InputStream inputStream = new ByteArrayInputStream(expectedResponseData.getBytes());
        // When getInputStream() is called on the mockConnection, return the dummy input stream
        when(mockConnection.getInputStream()).thenReturn(inputStream);
        // Create a mock output stream for the connection
        OutputStream osMock = mock(OutputStream.class);
        // Set up the mock to return the mock output stream when getOutputStream is called
        when(mockConnection.getOutputStream()).thenReturn(osMock);
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        URL url = new URL(getCreateRecordUrl());
        when(mockConnection.getURL()).thenReturn(url);
        // Call method under test

        RecordWrapper testRecordWrapper = getTestRecordWrapper("Some_random_status"
                , "jane.doe@test.gov", "United States");
        UserInfoWrapper userInfoWrapper = new UserInfoWrapper(
                testRecordWrapper.getRecord().getUserInfo(),
                RECAPTCHA_RESPONSE
        );

        // Act
        RecordWrapper actualRecord = service.createRecord(userInfoWrapper);

        // Assert
        assertEquals(actualRecord.getRecord().getId(), testRecordWrapper.getRecord().getId());
        // Assert that the status was set by the server and not the user's input
        assertEquals(actualRecord.getRecord().getUserInfo().getApprovalStatus(), "Pending");

        // Verify that the mock output stream was written to as expected
        byte[] expectedPayloadBytes = userInfoWrapper.toString().getBytes(StandardCharsets.UTF_8);
        verify(osMock).write(expectedPayloadBytes);
        verify(osMock).flush();
        verify(osMock).close();
        verify(mockConnection).setRequestMethod("POST");
        verify(mockConnection).setDoOutput(true);
        verify(mockConnection).setRequestProperty("Content-Type", "application/json");
        verify(mockConnection).setRequestProperty("Authorization", "Bearer " + testToken.getAccessToken());
        assertEquals(getCreateRecordUrl(), mockConnection.getURL().toString());

        // Verify connection was closed
        verify(mockConnection).disconnect();

    }

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

    @Test
    public void testCreateRecord_failure_badRequest()
            throws IOException {
        // Arrange

        // Set up mock behavior for mockConnection
        // Here the dummy record doesn't matter since we are expecting an exception to be thrown
        // Create a mock output stream for the connection
        OutputStream osMock = mock(OutputStream.class);
        // Set up the mock to return the mock output stream when getOutputStream is called
        when(mockConnection.getOutputStream()).thenReturn(osMock);
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);
        when(mockConnection.getResponseMessage()).thenReturn("Bad Request");

        try {
            // Act
            service.createRecord(
                    new UserInfoWrapper(
                            getTestRecordWrapper("Pending",
                                    "jane.doe@test.gov", "United States").getRecord().getUserInfo(),
                            RECAPTCHA_RESPONSE
                    ));
            fail("Expected RequestProcessingException to be thrown");
        } catch (RequestProcessingException e) {
            // Assert
            assertEquals("Error response from Salesforce service: Bad Request", e.getMessage());
        }

        // Verify connection is closed
        verify(mockConnection).disconnect();

    }

    private String getUpdateUrl(String recordId) {
        // Build the URL used by get request
        String url;
        try {
            url = new URIBuilder(testToken.getInstanceUrl())
                    .setPath(rpaConfiguration.getSalesforceEndpoints().get("update-record-endpoint") + "/" + recordId)
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new RequestProcessingException("Error building URI: " + e.getMessage());
        }
        return url;
    }

    @Test
    public void testUpdateRecord_success() throws Exception {
        String recordId = "record12345";
        String email = "test@example.com";
        String country = "United States";
        String expectedApprovalStatus = "Approved_2023-05-09T15:59:03.872Z_" + email;

        // Mock behavior of getRecord method
        doReturn(getTestRecordWrapper(expectedApprovalStatus, email, country)).when(service).getRecord("record12345");

        // Mock HttpResponse
        CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
        when(httpResponse.getStatusLine()).thenReturn(mock(StatusLine.class));
        when(httpResponse.getStatusLine().getStatusCode()).thenReturn(200);
        when(httpResponse.getEntity()).thenReturn(
                new StringEntity("{\"approvalStatus\":\"Approved_2023-05-09T15:59:03.872Z_test@example.com\"," +
                        "\"recordId\":\"record12345\"}",
                        ContentType.APPLICATION_JSON)
        );

        // It's tricky to mock the HttpPatch without using PowerMock
        // Instead we capture the argument passed to the execute method of the mocked HttpClient
        ArgumentCaptor<HttpPatch> captor = ArgumentCaptor.forClass(HttpPatch.class);
        doReturn(httpResponse).when(mockHttpClient).execute(captor.capture());

        // Act
        RecordStatus result = service.updateRecord(recordId, "Approved", email);

        // Assert
        assertEquals(expectedApprovalStatus, result.getApprovalStatus());
        assertEquals(recordId, result.getRecordId());

        // Capture the HttpPatch argument
        HttpPatch patchRequest = captor.getValue();
        assertEquals(getUpdateUrl(recordId), patchRequest.getURI().toString());
        assertEquals("Bearer " + testToken.getAccessToken(), patchRequest.getFirstHeader("Authorization").getValue());
        assertEquals("application/json", patchRequest.getFirstHeader("Content-Type").getValue());
        // Get the patch payload from the captured argument and check the format of the approval status
        // We can't test the exact time as it changes when we run the test, but we can verify the format
        String patchPayload = EntityUtils.toString(patchRequest.getEntity(), StandardCharsets.UTF_8);
        JSONObject payloadObject = new JSONObject(patchPayload);
        // Pattern to match ISO 8601 format
        // This pattern matches a string in the format "Approved_YYYY-MM-DDTHH:MM:SS.SSSZ"
        String expectedFormat = "Approved_\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z_[\\w.-]+@[\\w.-]+\\.\\w+";
        assertTrue(payloadObject.get("Approval_Status__c").toString().matches(expectedFormat));

    }

    @Test
    public void testUpdateRecord_withUnknownStatus() {
        String recordId = "record12345";
        String status = "HelloWorld";
        String email = "test@example.com";
        String expectedErrorMessage = "Invalid approval status: HelloWorld";

        // Call the method and catch the exception
        try {
            // Act
            service.updateRecord(recordId, status, email);
            fail("Expected InvalidRequestException to be thrown");
        } catch (InvalidRequestException e) {
            // Assert the message of the exception
            assertEquals(expectedErrorMessage, e.getMessage());
        }
    }


}
