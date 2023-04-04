package gov.nist.oar.distrib.service.rpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaClientException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaServerException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaVerificationFailedException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecordNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.service.rpa.model.JWTToken;
import gov.nist.oar.distrib.service.rpa.model.RecaptchaResponse;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.service.rpa.model.RecordPatch;
import gov.nist.oar.distrib.service.rpa.model.RecordStatus;
import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;
import gov.nist.oar.distrib.service.rpa.model.UserInfo;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpURLConnectionRPARequestHandlerServiceTest {

    private static final String TEST_ACCESS_TOKEN = "test_access_token";
    private static final String TEST_INSTANCE_URL = "https://test.salesforce.com";
    private static final String TEST_RECORD_ID = "1";
    private static final String TEST_RECORD_RESPONSE = "{\"record\":{\"id\":\"1\",\"caseNum\":\"123\"," +
            "\"userInfo\":{\"fullName\":\"John Doe\",\"organization\":\"NIST\",\"email\":\"test@test.com\"," +
            "\"receiveEmails\":\"true\",\"country\":\"USA\",\"approvalStatus\":\"Pending\",\"productTitle\":" +
            "\"Test Product\",\"subject\":\"1234567890\",\"description\":\"Test Product Description\"}}}";

    private static final String RECAPTCHA_SECRET = "recaptcha_secret";
    private static final String RECAPTCHA_RESPONSE = "recaptcha_response";
    @Mock
    private RPAConfiguration rpaConfiguration;
    @Mock
    private HttpURLConnectionFactory mockConnectionFactory;
    @Mock
    private HttpURLConnection mockConnection;
    @Mock
    JWTHelper mockJwtHelper;

    @Mock
    RecaptchaHelper recaptchaHelper;

    @Mock
    RecaptchaResponse recaptchaResponse;

    @Mock
    RecordResponseHandler recordResponseHandler;

    @Mock
    private IRPARequestHandler mockRequestHandler;

    @Mock
    Map<String, String> map;

    private HttpURLConnectionRPARequestHandlerService service;

    JWTToken testToken = null;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        service = spy(new HttpURLConnectionRPARequestHandlerService(rpaConfiguration));
        service.setJWTHelper(mockJwtHelper);
        service.setRecaptchaHelper(recaptchaHelper);
        service.setHttpURLConnectionFactory(url -> mockConnection);
        service.setRecordResponseHandler(recordResponseHandler);

        // Set up mock behavior for mockJwtHelper
        testToken = new JWTToken(TEST_ACCESS_TOKEN, TEST_INSTANCE_URL);
        when(mockJwtHelper.getToken()).thenReturn(testToken);

        // Set GET endpoint
        when(rpaConfiguration.getSalesforceEndpoints()).thenReturn(map);
        when(map.get("get-record-endpoint")).thenReturn("/records");
        when(map.get("create-record-endpoint")).thenReturn("/records");
        when(map.get("update-record-endpoint")).thenReturn("/records");
    }


    @Test
    public void getRecord_success() throws Exception {
        RecordWrapper expectedRecord = new ObjectMapper().readValue(TEST_RECORD_RESPONSE, RecordWrapper.class);

        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(TEST_RECORD_RESPONSE.getBytes()));

        RecordWrapper actualRecord = service.getRecord(TEST_RECORD_ID);

        assertEquals(expectedRecord.toString(), actualRecord.toString());
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
    }

    @Test
    public void testGetRecord_internalServerError() throws Exception {
        // Set up mock behavior for mockConnection
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);
        when(mockConnection.getResponseMessage()).thenReturn("Bad Request");

        // Verify exception is thrown
        try {
            // Call method to test
            service.getRecord(TEST_RECORD_ID);
            fail("Expected InternalServerErrorException to be thrown");
        } catch (RequestProcessingException e) {
            // Verify exception message is correct
            assertEquals("Error response from salesforce service: Bad Request", e.getMessage());
        }
    }

    @Test
    public void testCreateRecord_success()
            throws RecaptchaVerificationFailedException, RecaptchaServerException, RecaptchaClientException,
            InvalidRequestException, IOException {
        // Mock RPA Config to return the RECAPTCHA_SECRET
        when(rpaConfiguration.getRecaptchaSecret()).thenReturn(RECAPTCHA_SECRET);

        // Mock the recaptcha response
        when(recaptchaResponse.isSuccess()).thenReturn(true);
        when(recaptchaResponse.toString()).thenReturn("whatever"); // this just for the debug message in the service
        when(recaptchaHelper.verifyRecaptcha(RECAPTCHA_SECRET, RECAPTCHA_RESPONSE)).thenReturn(recaptchaResponse);

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

        // Call method under test
        UserInfo userInfo = getTestRecordWrapper("Pending").getRecord().getUserInfo();
        RecordWrapper actualRecord = service.createRecord(new UserInfoWrapper(userInfo, RECAPTCHA_RESPONSE));

        // Assert
        assertEquals(actualRecord.getRecord().getId(), getTestRecordWrapper("Pending").getRecord().getId());

        // Verify that the mock output stream was written to as expected
        byte[] expectedPayloadBytes = userInfo.toString().getBytes(StandardCharsets.UTF_8);
        verify(osMock).write(expectedPayloadBytes);
        verify(osMock).flush();
        verify(osMock).close();
        // Verify connection was closed
        verify(mockConnection).disconnect();

    }

    private RecordWrapper getTestRecordWrapper(String status) {
        UserInfo userInfo = new UserInfo();
        userInfo.setFullName("Jane Doe");
        userInfo.setSubject("1234");
        userInfo.setCountry("United States");
        userInfo.setOrganization("NASA");
        userInfo.setDescription("Some description goes here");
        userInfo.setEmail("jane.doe@test.gov");
        userInfo.setProductTitle("Product title");
        userInfo.setApprovalStatus(status);
        userInfo.setReceiveEmails("Yes");
        Record record = new Record("5003R000003ErErQAK", "00228987", userInfo);
        RecordWrapper recordWrapper = new RecordWrapper(record);

        return recordWrapper;
    }

    @Test
    public void testCreateRecord_failure_verificationFailed()
            throws RecaptchaServerException, RecaptchaClientException, InvalidRequestException {
        // Mock RPA Config to return the RECAPTCHA_SECRET
        when(rpaConfiguration.getRecaptchaSecret()).thenReturn(RECAPTCHA_SECRET);
        // Mock the recaptcha response
        when(recaptchaHelper.verifyRecaptcha(RECAPTCHA_SECRET, RECAPTCHA_RESPONSE)).thenReturn(recaptchaResponse);
        when(recaptchaResponse.isSuccess()).thenReturn(false);

        RecordWrapper actualRecord = null;
        try {
            actualRecord = service.createRecord(
                    new UserInfoWrapper(
                            getTestRecordWrapper("Pending").getRecord().getUserInfo(),
                            RECAPTCHA_RESPONSE
                    ));
            fail("Expected InvalidRequestException to be thrown");
        } catch (RecaptchaVerificationFailedException e) {
            // recaptchaResponse.getSuccess() == false, record should be null
            assertEquals("reCAPTCHA verification failed", e.getMessage());
            assertNull(actualRecord);
        }
    }

    @Test
    public void testCreateRecord_failure_badRequest()
            throws RecaptchaVerificationFailedException, RecaptchaServerException, RecaptchaClientException,
            IOException {
        // Mock RPA Config to return the RECAPTCHA_SECRET
        when(rpaConfiguration.getRecaptchaSecret()).thenReturn(RECAPTCHA_SECRET);
        // Mock the recaptcha response
        when(recaptchaResponse.isSuccess()).thenReturn(true);
        when(recaptchaResponse.toString()).thenReturn("whatever"); // this just for the debug message in the service
        when(recaptchaHelper.verifyRecaptcha(RECAPTCHA_SECRET, RECAPTCHA_RESPONSE)).thenReturn(recaptchaResponse);

        // Set up mock behavior for mockConnection
        // Here the dummy record doesn't matter since we are expecting an exception to be thrown
        // Create a mock output stream for the connection
        OutputStream osMock = mock(OutputStream.class);
        // Set up the mock to return the mock output stream when getOutputStream is called
        when(mockConnection.getOutputStream()).thenReturn(osMock);
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);
        when(mockConnection.getResponseMessage()).thenReturn("Bad Request");

        // Assert
        try {
            service.createRecord(
                    new UserInfoWrapper(
                            getTestRecordWrapper("Pending").getRecord().getUserInfo(),
                            RECAPTCHA_RESPONSE
                    ));
            fail("Expected InvalidRequestException to be thrown");
        } catch (InvalidRequestException e) {
            assertEquals("Invalid request: Bad Request", e.getMessage());
        }

        verify(mockConnection).disconnect();

    }

    @Test
    public void testUpdateRecord_success() throws IOException {
        // Prepare mocks
        String expectedRecordStatus = "{\"recordId\":\"123\",\"approvalStatus\":\"Approved\"}";
        // Create an input stream with the dummy data
        InputStream inputStream = new ByteArrayInputStream(expectedRecordStatus.getBytes());
        // When getInputStream() is called on the mockConnection, return the dummy input stream
        when(mockConnection.getInputStream()).thenReturn(inputStream);
        // Create a mock output stream for the connection
        OutputStream osMock = mock(OutputStream.class);
        // Set up the mock to return the mock output stream when getOutputStream is called
        when(mockConnection.getOutputStream()).thenReturn(osMock);
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);

        // mock behavior of getRecord method
        doReturn(getTestRecordWrapper("Approved")).when(service).getRecord("123");

        // Assert
        RecordStatus recordStatus = service.updateRecord("123", "Approved");
        assertEquals("123", recordStatus.getRecordId());
        assertEquals("Approved", recordStatus.getApprovalStatus());

        verify(service).getRecord("123");
        verify(mockConnection).setRequestMethod("PATCH");
        verify(mockConnection).setRequestProperty("Content-Type", "application/json");
        verify(mockConnection).setRequestProperty("Authorization", "Bearer " + mockJwtHelper.getToken().getAccessToken());
        verify(recordResponseHandler).onRecordUpdateApproved(any(Record.class));

        // Verify that the mock output stream was written to as expected
        RecordPatch patchData = new RecordPatch("Approved");
        byte[] expectedPayloadBytes = patchData.toString().getBytes(StandardCharsets.UTF_8);
        verify(osMock).write(expectedPayloadBytes);
        verify(osMock).flush();
        verify(osMock).close();

        // Verify connection was closed
        verify(mockConnection).disconnect();
    }

    @Test
    public void testUpdateRecord_failure_badRequest() throws IOException {
        // Create a mock output stream for the connection
        OutputStream osMock = mock(OutputStream.class);
        // Set up the mock to return the mock output stream when getOutputStream is called
        when(mockConnection.getOutputStream()).thenReturn(osMock);

        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);
        when(mockConnection.getResponseMessage()).thenReturn("Bad Request");

        try {
            // Act
            service.updateRecord("123", "approved");
            fail("Expected InvalidRequestException to be thrown");
        } catch (InvalidRequestException e) {
            // Assert
            assertEquals("Invalid request: Bad Request", e.getMessage());
        }
    }

    @Test
    public void testUpdateRecord_failure_requestProcessingException() throws IOException {
        // Create a mock output stream for the connection
        OutputStream osMock = mock(OutputStream.class);
        // Set up the mock to return the mock output stream when getOutputStream is called
        when(mockConnection.getOutputStream()).thenReturn(osMock);

        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_INTERNAL_ERROR);
        when(mockConnection.getResponseMessage()).thenReturn("Service Unavailable");

        try {
            // Act
            service.updateRecord("123", "approved");
            fail("Expected RequestProcessingException to be thrown");
        } catch (RequestProcessingException e) {
            // Assert
            assertEquals("Error response from Salesforce service: Service Unavailable", e.getMessage());
        }
    }

    @Test
    public void testUpdateRecord_failure_recordNotFound() throws IOException {

        // Prepare mocks
        String expectedRecordStatus = "{\"recordId\":\"123\",\"approvalStatus\":\"Approved\"}";
        // Create an input stream with the dummy data
        InputStream inputStream = new ByteArrayInputStream(expectedRecordStatus.getBytes());
        // When getInputStream() is called on the mockConnection, return the dummy input stream
        when(mockConnection.getInputStream()).thenReturn(inputStream);
        // Create a mock output stream for the connection
        OutputStream osMock = mock(OutputStream.class);
        // Set up the mock to return the mock output stream when getOutputStream is called
        when(mockConnection.getOutputStream()).thenReturn(osMock);
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);

        // mock behavior of getRecord method to throw RecordNotFoundException
        doThrow(RecordNotFoundException.fromRecordId("123")).when(service).getRecord("123");

        try {
            service.updateRecord("123", "Approved");
            fail("Expected RecordNotFoundException to be thrown");
        } catch (RecordNotFoundException e) {
            // Assert
            assertEquals("Record with ID=123 could not be found", e.getMessage());
        }
    }

}
