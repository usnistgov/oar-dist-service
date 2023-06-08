package gov.nist.oar.distrib.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.IRPARequestHandler;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaVerificationFailedException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecordNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.service.rpa.model.RecordPatch;
import gov.nist.oar.distrib.service.rpa.model.RecordStatus;
import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;
import gov.nist.oar.distrib.service.rpa.model.UserInfo;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(MockitoJUnitRunner.class)
public class RPARequestHandlerControllerTest {
    @Mock
    private IRPARequestHandler service;

    @Mock
    RPAServiceProvider mockRPAServiceProvider;

    @Mock
    RPACachingService mockRPACachingService;

    @Mock
    RequestSanitizer mockRequestSanitizer;

    private RPARequestHandlerController controller;

    private MockMvc mockMvc;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockRPAServiceProvider.getIRPARequestHandler(mockRPACachingService)).thenReturn(service);
        // create a test instance of the RPARequestHandlerController class
        controller = new RPARequestHandlerController(mockRPAServiceProvider, mockRPACachingService);
        controller.setRequestSanitizer(mockRequestSanitizer);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .build();
    }

    /**
     * Unit test to verify the connection to Salesforce APIs.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testConnectionToSalesforceAPIs() throws Exception {
        ResponseEntity result = controller.testConnectionToSalesforceAPIs();
        // assert that the HTTP status code is HttpStatus.OK
        assertEquals(HttpStatus.OK, result.getStatusCode());

        // assert that the response body is not null
        assertNotNull(result.getBody());

        // assert that the response body is of type RecordWrapper
        assertTrue(result.getBody() instanceof String);

        assertEquals("Salesforce API is available.", result.getBody());

        // Test using url path
        mockMvc.perform(get("/ds/rpa/test"))
                .andExpect(status().isOk())
                .andExpect(content().string("Salesforce API is available."));
    }

    /**
     * Tests the "getRecord" method of the {@link RPARequestHandlerController} class.
     * Asserts that the HTTP status code is 200, response body is not null, and response body is of type
     * {@link RecordWrapper}.
     * Also tests the same using url path with MockMvc.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void getRecord() throws Exception {
        // Set a test record ID
        String recordId = "123";
        // Create a test RecordWrapper object
        RecordWrapper recordWrapper = new RecordWrapper();
        recordWrapper.setRecord(new Record(recordId, "12345", new UserInfo()));

        // mock the getRecord() method of the IRPARequestHandler object to return the test RecordWrapper object
        when(service.getRecord(recordId)).thenReturn(recordWrapper);

        // Call the getRecord() method of the RPARequestHandlerController class with the test record ID
        ResponseEntity result = controller.getRecord(recordId);

        // Assert that the HTTP status code is HttpStatus.OK
        assertEquals(HttpStatus.OK, result.getStatusCode());

        // Assert that the response body is not null
        assertNotNull(result.getBody());

        // Assert that the response body is of type RecordWrapper
        assertTrue(result.getBody() instanceof RecordWrapper);

        Record actualRecord = ((RecordWrapper) result.getBody()).getRecord();
        // Assert that the "record" key in the response body is not null
        assertNotNull(actualRecord);

        // Test using url path
        // Assert that the "record" key in the response body is not null
        mockMvc.perform(get("/ds/rpa/request/accepted/" + recordId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.record").exists()) // verify that key "record" exists
                .andExpect(jsonPath("$.record").value(notNullValue()));
    }

    /**
     * Unit test for the getRecord method in {@link RPARequestHandlerController} that checks if the controller can
     * correctly handle the {@link RecordNotFoundException} and return a 404 status with the appropriate message in the
     * response body.
     *
     * @throws Exception if an error occurs during the test.
     */
    @Test
    public void testGetRecord_RecordNotFoundException() throws Exception {
        // Create a test record ID
        String recordId = "123";

        // Mock the getRecord() method of the IRPARequestHandler object to throw a RecordNotFoundException
        when(service.getRecord(recordId)).thenThrow(RecordNotFoundException.fromRecordId(recordId));

        // Call the getRecord() method of the RPARequestHandlerController class with the test record ID
        mockMvc.perform(get("/ds/rpa/request/accepted/" + recordId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("record not found: " +
                        "Record with ID=123 could not be found"));
    }


    /**
     * This method tests the behavior of the {@link RPARequestHandlerController#getRecord(String)} method
     * when it throws a {@link RequestProcessingException}, and verifies that the controller returns the
     * correct error response when the exception is thrown.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testGetRecord_RequestProcessingException() throws Exception {
        // Create a test record ID
        String recordId = "123";

        // Mock the getRecord() method of the IRPARequestHandler object to throw a RecordNotFoundException
        when(service.getRecord(recordId)).thenThrow(new RequestProcessingException("Something went wrong"));

        // Call the getRecord() method of the RPARequestHandlerController class with the test record ID
        mockMvc.perform(get("/ds/rpa/request/accepted/" + recordId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("internal server error: Something went wrong"));
    }


    /**
     * Unit test for {@link RPARequestHandlerController#createRecord(UserInfoWrapper, String)}.
     * Verifies that a record is created successfully and returns the expected response with HTTP status code 200 OK.
     *
     * @throws Exception if an error occurs while running the test
     */
    @Test
    public void createRecord() throws Exception {
        // Arrange
        UserInfoWrapper userInfoWrapper = new UserInfoWrapper();

        UserInfo userInfo = new UserInfo();
        userInfo.setFullName("Jane Doe");
        userInfo.setOrganization("NASA");
        userInfo.setEmail("jane.doe@test.gov");
        userInfo.setReceiveEmails("True");
        userInfo.setCountry("United States");
        userInfo.setApprovalStatus("Pending");
        userInfo.setProductTitle("Some product title");
        userInfo.setSubject("ark:\\88434\\mds2\\2106");
        userInfo.setDescription("Some description");

        userInfoWrapper.setUserInfo(userInfo);

        RecordWrapper recordWrapper = new RecordWrapper();
        recordWrapper.setRecord(new Record("123", "12345", userInfo));
        // mock the getRecord() method of the IRPARequestHandler object to return the test RecordWrapper object
        when(service.createRecord(any(UserInfoWrapper.class), isNull())).thenReturn(recordWrapper);

        // Call the createRecord() method of the RPARequestHandlerController class with the test record ID
        ResponseEntity result = controller.createRecord(userInfoWrapper, null);

        // Assert that the HTTP status code is HttpStatus.OK
        assertEquals(HttpStatus.OK, result.getStatusCode());

        // Assert that the response body is not null
        assertNotNull(result.getBody());

        // Assert that the response body is of type RecordWrapper
        assertTrue(result.getBody() instanceof RecordWrapper);

        Record actualRecord = ((RecordWrapper) result.getBody()).getRecord();
        // Assert that the "record" key in the response body is not null
        assertNotNull(actualRecord);

        // Test using url path
        // Assert
        mockMvc.perform(post("/ds/rpa/request/form")
                        .content(new ObjectMapper().writeValueAsString(userInfoWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.record").exists());
    }

    @Test
    public void createRecord_withAuthorizationHeader() throws Exception {
        // Arrange
        UserInfoWrapper userInfoWrapper = new UserInfoWrapper();

        UserInfo userInfo = new UserInfo();
        userInfo.setFullName("Jane Doe");
        userInfo.setOrganization("NASA");
        userInfo.setEmail("jane.doe@test.gov");
        userInfo.setReceiveEmails("True");
        userInfo.setCountry("United States");
        userInfo.setApprovalStatus("Pending");
        userInfo.setProductTitle("Some product title");
        userInfo.setSubject("ark:\\88434\\mds2\\2106");
        userInfo.setDescription("Some description");

        userInfoWrapper.setUserInfo(userInfo);

        RecordWrapper recordWrapper = new RecordWrapper();
        recordWrapper.setRecord(new Record("123", "12345", new UserInfo()));
        // mock the getRecord() method of the IRPARequestHandler object to return the test RecordWrapper object
        when(service.createRecord(any(UserInfoWrapper.class), any(String.class))).thenReturn(recordWrapper);

        // Call the createRecord() method of the RPARequestHandlerController class with the test record ID
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer TEST123");

        ResponseEntity result = controller.createRecord(userInfoWrapper, headers.getFirst(HttpHeaders.AUTHORIZATION));

        // Assert that the HTTP status code is HttpStatus.OK
        assertEquals(HttpStatus.OK, result.getStatusCode());

        // Assert that the response body is not null
        assertNotNull(result.getBody());

        // Assert that the response body is of type RecordWrapper
        assertTrue(result.getBody() instanceof RecordWrapper);

        Record actualRecord = ((RecordWrapper) result.getBody()).getRecord();
        // Assert that the "record" key in the response body is not null
        assertNotNull(actualRecord);

        // Test using url path
        // Assert
        mockMvc.perform(post("/ds/rpa/request/form")
                        .content(new ObjectMapper().writeValueAsString(userInfoWrapper))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, headers.getFirst(HttpHeaders.AUTHORIZATION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.record").exists());
    }

    /**
     * This method tests the behavior of the {@link RPARequestHandlerController#createRecord(UserInfoWrapper, String)} method
     * when it throws a {@link RequestProcessingException}, and verifies that the controller returns the
     * correct error response when the exception is thrown.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testCreateRecord_RequestProcessingException() throws Exception {
        // Arrange
        RequestProcessingException exception = new RequestProcessingException("Test error message");

        // Mock the createRecord() method of the IRPARequestHandler object to throw an InvalidRequestException
        when(service.createRecord(any(UserInfoWrapper.class), isNull())).thenThrow(exception);

        UserInfoWrapper userInfoWrapper = new UserInfoWrapper();

        UserInfo userInfo = new UserInfo();
        userInfo.setFullName("Jane Doe");
        userInfo.setOrganization("NASA");
        userInfo.setEmail("jane.doe@test.gov");
        userInfo.setReceiveEmails("True");
        userInfo.setCountry("United States");
        userInfo.setApprovalStatus("Pending");
        userInfo.setProductTitle("Some product title");
        userInfo.setSubject("ark:\\88434\\mds2\\2106");
        userInfo.setDescription("Some description");

        userInfoWrapper.setUserInfo(userInfo);
        // Act and Assert
        mockMvc.perform(post("/ds/rpa/request/form")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(userInfoWrapper)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("internal server error: Test error message"));
    }

    /**
     * This method tests the behavior of the {@link RPARequestHandlerController#createRecord(UserInfoWrapper, String)} method
     * when it throws a {@link InvalidRequestException}, and verifies that the controller returns the
     * correct error response when the exception is thrown.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testCreateRecord_InvalidRequestException() throws Exception {
        // Arrange
        InvalidRequestException exception = new InvalidRequestException("Test error message");

        // Mock the createRecord() method of the IRPARequestHandler object to throw an InvalidRequestException
        when(service.createRecord(any(UserInfoWrapper.class), isNull())).thenThrow(exception);

        UserInfoWrapper userInfoWrapper = new UserInfoWrapper();

        UserInfo userInfo = new UserInfo();
        userInfo.setFullName("Jane Doe");
        userInfo.setOrganization("NASA");
        userInfo.setEmail("jane.doe@test.gov");
        userInfo.setReceiveEmails("True");
        userInfo.setCountry("United States");
        userInfo.setApprovalStatus("Pending");
        userInfo.setProductTitle("Some product title");
        userInfo.setSubject("ark:\\88434\\mds2\\2106");
        userInfo.setDescription("Some description");

        userInfoWrapper.setUserInfo(userInfo);

        // Act and Assert
        mockMvc.perform(post("/ds/rpa/request/form")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(userInfoWrapper)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("invalid request: Test error message"));
    }

    /**
     * This method tests the behavior of the {@link RPARequestHandlerController#createRecord(UserInfoWrapper, String)} method
     * when it throws a {@link InvalidRequestException} due to a sanitization error, and checks if it returns the
     * correct error response when the exception is thrown.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testCreateRecord_invalidInput_sanitizerException() throws Exception {
        // Arrange
        InvalidRequestException exception = new InvalidRequestException("Test error message");

        // Mock the sanitizeAndValidate() method of the RequestSanitizer object to throw an InvalidRequestException
        doThrow(exception).when(mockRequestSanitizer).sanitizeAndValidate(any(UserInfoWrapper.class));

        UserInfoWrapper userInfoWrapper = new UserInfoWrapper();

        // Act and Assert
        mockMvc.perform(post("/ds/rpa/request/form")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(userInfoWrapper)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("invalid request: Test error message"));
    }

    /**
     * This method tests the behavior of the {@link RPARequestHandlerController#createRecord(UserInfoWrapper, String)} method
     * when it throws a {@link RecaptchaVerificationFailedException}, and verifies that the controller returns the
     * correct error response when the exception is thrown.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testCreateRecord_RecaptchaVerificationFailedException() throws Exception {
        // Arrange
        RecaptchaVerificationFailedException exception = new RecaptchaVerificationFailedException("reCAPTCHA failed");

        // Mock the createRecord() method of the IRPARequestHandler object to throw an InvalidRequestException
        when(service.createRecord(any(UserInfoWrapper.class), isNull())).thenThrow(exception);

        UserInfoWrapper userInfoWrapper = new UserInfoWrapper();

        UserInfo userInfo = new UserInfo();
        userInfo.setFullName("Jane Doe");
        userInfo.setOrganization("NASA");
        userInfo.setEmail("jane.doe@test.gov");
        userInfo.setReceiveEmails("True");
        userInfo.setCountry("United States");
        userInfo.setApprovalStatus("Pending");
        userInfo.setProductTitle("Some product title");
        userInfo.setSubject("ark:\\88434\\mds2\\2106");
        userInfo.setDescription("Some description");

        userInfoWrapper.setUserInfo(userInfo);
        // Act and Assert
        mockMvc.perform(post("/ds/rpa/request/form")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(userInfoWrapper)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Unauthorized: reCAPTCHA failed"));
    }

    /**
     * Unit test for {@link RPARequestHandlerController#updateRecord(String, RecordPatch)}.
     * Verifies that a record is updated successfully and returns the expected response with HTTP status code 200 OK.
     *
     * @throws Exception if an error occurs while running the test
     */
    @Test
    public void testUpdateRecord() throws Exception {
        String recordId = "123";
        String status = "Approved";
        RecordStatus recordStatus = new RecordStatus(recordId, status);
        when(service.updateRecord(anyString(), anyString())).thenReturn(recordStatus);

        RecordPatch recordPatch = new RecordPatch(status);
        // Call the updateRecord() method of the RPARequestHandlerController class with the test record ID and
        // recordPatch
        ResponseEntity result = controller.updateRecord(recordId, recordPatch);

        // Assert that the HTTP status code is HttpStatus.OK
        assertEquals(HttpStatus.OK, result.getStatusCode());

        // Assert that the response body is not null
        assertNotNull(result.getBody());

        // Assert that the response body is of type RecordStatus
        assertTrue(result.getBody() instanceof RecordStatus);

        RecordStatus actualRecordStatus = (RecordStatus) result.getBody();
        assertNotNull(actualRecordStatus);
        assertEquals("123", actualRecordStatus.getRecordId());
        assertEquals("Approved", actualRecordStatus.getApprovalStatus());

        // Use MockMvc approach to test with URL path
        mockMvc.perform(patch("/ds/rpa/request/accepted/" + recordId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(recordPatch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordId").value("123"));
    }


    /**
     * This method tests the behavior of the {@link RPARequestHandlerController#updateRecord(String, RecordPatch)} method
     * when it throws a {@link RecordNotFoundException}, and verifies that the controller returns the
     * correct error response when the exception is thrown.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testUpdateRecord_RecordNotFoundException() throws Exception {
        // Arrange
        RecordNotFoundException exception = RecordNotFoundException.fromRecordId("123");

        // Mock the createRecord() method of the IRPARequestHandler object to throw an InvalidRequestException
        when(service.updateRecord(anyString(), anyString())).thenThrow(exception);

        // Act and Assert
        mockMvc.perform(patch("/ds/rpa/request/accepted/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(new RecordPatch("Pending"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("record not found: " +
                        "Record with ID=123 could not be found"));
    }

    /**
     * This method tests the behavior of the {@link RPARequestHandlerController#updateRecord(String, RecordPatch)} method
     * when it throws a {@link RequestProcessingException}, and verifies that the controller returns the
     * correct error response when the exception is thrown.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testUpdateRecord_RequestProcessingException() throws Exception {
        // Arrange
        RequestProcessingException exception = new RequestProcessingException("Something went wrong");

        // Mock the updateRecord() method of the IRPARequestHandler object to throw an RequestProcessingException
        when(service.updateRecord(anyString(), anyString())).thenThrow(exception);

        // Act and Assert
        mockMvc.perform(patch("/ds/rpa/request/accepted/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(new RecordPatch("Pending"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("internal server error: Something went wrong"));
    }

    /**
     * This method tests the behavior of the {@link RPARequestHandlerController#updateRecord(String, RecordPatch)} method
     * when it throws a {@link InvalidRequestException}, and verifies that the controller returns the
     * correct error response when the exception is thrown.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testUpdateRecord_InvalidRequestException() throws Exception {
        // Arrange
        InvalidRequestException exception = new InvalidRequestException("Bad Request");

        // Mock the updateRecord() method of the IRPARequestHandler object to throw an InvalidRequestException
        when(service.updateRecord(anyString(), anyString())).thenThrow(exception);

        // Act and Assert
        mockMvc.perform(patch("/ds/rpa/request/accepted/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(new RecordPatch("Pending"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("invalid request: Bad Request"));
    }

    /**
     * Unit test {@link RPARequestHandlerController#handleRecordNotFoundException(RecordNotFoundException)}.
     * <p>
     * Call the handleRecordNotFoundException method with the exception and verify that the returned {@link ErrorInfo}
     * contains the expected status code and message.
     */
    @Test
    public void testHandleRecordNotFoundException() {
        // Arrange
        String recordId = "123";
        String errorMessage = "Record with ID=" + recordId + " could not be found";
        RecordNotFoundException exception = RecordNotFoundException.fromRecordId(recordId);

        // Call the handleRecordNotFoundException() method with the exception
        ErrorInfo errorInfo = controller.handleRecordNotFoundException(exception);

        // Verify that the returned ErrorInfo contains the expected status code and message
        assertEquals(HttpStatus.NOT_FOUND.value(), errorInfo.status);
        assertEquals("record not found: " + errorMessage, errorInfo.message);
    }

    /**
     * Unit test {@link RPARequestHandlerController#handleRequestProcessingException(RequestProcessingException)}.
     * <p>
     * Call the handleRequestProcessingException method with the exception and verify that the returned
     * {@link ErrorInfo}
     * contains the expected status code and message.
     */
    @Test
    public void testHandleRequestProcessingException() {
        // Arrange
        String message = "Test error message";
        RequestProcessingException ex = new RequestProcessingException(message);

        // Call the handleRequestProcessingException() method with the exception
        ErrorInfo errorInfo = controller.handleRequestProcessingException(ex);

        // Verify that the returned ErrorInfo contains the expected status code and message
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), errorInfo.status);
        assertEquals("internal server error: " + message, errorInfo.message);
    }

    /**
     * Unit test {@link RPARequestHandlerController#handleInvalidRequestException(InvalidRequestException)}.
     * <p>
     * Call the handleInvalidRequestException method with the exception and verify that the returned {@link ErrorInfo}
     * contains the expected status code and message.
     */
    @Test
    public void testHandleInvalidRequestException() {
        // Arrange
        String errorMessage = "Invalid input data";
        InvalidRequestException exception = new InvalidRequestException(errorMessage);

        // Call the handleInvalidRequestException() method with the exception
        ErrorInfo errorInfo = controller.handleInvalidRequestException(exception);

        // Verify that the returned ErrorInfo contains the expected status code and message
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorInfo.status);
        assertEquals("invalid request: " + errorMessage, errorInfo.message);
    }

    /**
     * Unit test
     * {@link RPARequestHandlerController#handleRecaptchaVerificationFailedException(RecaptchaVerificationFailedException)}.
     * <p>
     * Call the handleRecaptchaVerificationFailedException method with the exception and verify that the returned
     * {@link ErrorInfo}
     * contains the expected status code and message.
     */
    @Test
    public void testRecaptchaVerificationFailedException() {
        // Arrange
        String errorMessage = "reCAPTCHA failed";
        RecaptchaVerificationFailedException exception = new RecaptchaVerificationFailedException(errorMessage);

        // Call the handleInvalidRequestException() method with the exception
        ErrorInfo errorInfo = controller.handleRecaptchaVerificationFailedException(exception);

        // Verify that the returned ErrorInfo contains the expected status code and message
        assertEquals(HttpStatus.UNAUTHORIZED.value(), errorInfo.status);
        assertEquals("Unauthorized: " + errorMessage, errorInfo.message);
    }
}
