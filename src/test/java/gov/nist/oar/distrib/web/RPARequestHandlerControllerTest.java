package gov.nist.oar.distrib.web;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.RPARequestHandler;
import gov.nist.oar.distrib.service.rpa.RecordCreationResult;
import gov.nist.oar.distrib.service.rpa.UpdateRecordResult;
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

@ExtendWith(MockitoExtension.class)
public class RPARequestHandlerControllerTest {

    @Mock
    private RPARequestHandler service;

    @Mock
    RPACachingService mockRPACachingService;

    @Mock
    RPAServiceProvider mockRPAServiceProvider;

    @Mock
    RequestSanitizer mockRequestSanitizer;

    @Mock
    JwtTokenValidator mockJwtTokenValidator;

    @Mock
    RPAConfiguration mockRPAConfiguration;

    @Mock
    RecaptchaVerificationHelper mockRecaptchaHelper;

    @Mock
    private RPAAsyncExecutor mockAsyncExecutor;

    private RPARequestHandlerController controller;
    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        when(mockRPAServiceProvider.getRPARequestHandler(mockRPACachingService)).thenReturn(service);
        controller = new RPARequestHandlerController(mockRPAServiceProvider, mockRPACachingService, mockAsyncExecutor);
        controller.setRequestSanitizer(mockRequestSanitizer);
        controller.setJwtTokenValidator(mockJwtTokenValidator);
        controller.setConfiguration(mockRPAConfiguration);
        mockRecaptchaHelper.setRpaConfiguration(mockRPAConfiguration);
        controller.setRecaptchaHelper(mockRecaptchaHelper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testConnectionToSalesforceAPIs() throws Exception {
        ResponseEntity result = controller.testConnectionToSalesforceAPIs();
        assertEquals(HttpStatus.OK, result.getStatusCode(), "Expected status to be OK");
        assertNotNull(result.getBody(), "Expected body to be non-null");
        assertTrue(result.getBody() instanceof String, "Expected body to be a String");
        assertEquals("Salesforce API is available.", result.getBody(), "Expected Salesforce API message");

        mockMvc.perform(get("/ds/rpa/test"))
                .andExpect(status().isOk())
                .andExpect(content().string("Salesforce API is available."));
    }

    @Test
    void testGetRecord() throws Exception {
        String recordId = "123";
        RecordWrapper recordWrapper = new RecordWrapper();
        recordWrapper.setRecord(new Record(recordId, "12345", new UserInfo()));

        when(service.getRecord(recordId)).thenReturn(recordWrapper);

        Map<String, String> expectedTokenDetails = new HashMap<>();
        expectedTokenDetails.put("username", "john.doe");
        expectedTokenDetails.put("email", "john.doe@example.com");
        expectedTokenDetails.put("expiry", "1643218800");
        expectedTokenDetails.put("user_id", "12345");

        when(mockJwtTokenValidator.validate(any())).thenReturn(expectedTokenDetails);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer TEST123");

        ResponseEntity result = controller.getRecord(recordId, headers.getFirst(HttpHeaders.AUTHORIZATION));

        assertEquals(HttpStatus.OK, result.getStatusCode(), "Expected status to be OK");
        assertNotNull(result.getBody(), "Expected body to be non-null");
        assertTrue(result.getBody() instanceof RecordWrapper, "Expected body to be a RecordWrapper");

        Record actualRecord = ((RecordWrapper) result.getBody()).getRecord();
        assertNotNull(actualRecord, "Expected record to be non-null");

        mockMvc.perform(get("/ds/rpa/request/accepted/" + recordId)
                .header("Authorization", "Bearer TEST123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.record").exists())
                .andExpect(jsonPath("$.record").value(notNullValue()));
    }

    @Test
    void testGetRecord_RecordNotFoundException() throws Exception {
        String recordId = "123";

        Map<String, String> expectedTokenDetails = new HashMap<>();
        expectedTokenDetails.put("username", "john.doe");
        expectedTokenDetails.put("email", "john.doe@example.com");
        expectedTokenDetails.put("expiry", "1643218800");
        expectedTokenDetails.put("user_id", "12345");

        when(mockJwtTokenValidator.validate(any())).thenReturn(expectedTokenDetails);
        when(service.getRecord(recordId)).thenThrow(RecordNotFoundException.fromRecordId(recordId));

        mockMvc.perform(get("/ds/rpa/request/accepted/" + recordId)
                .header("Authorization", "Bearer TEST123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("record not found: Record with ID=123 could not be found"));
    }

    @Test
    void testGetRecord_RequestProcessingException() throws Exception {
        String recordId = "123";

        Map<String, String> expectedTokenDetails = new HashMap<>();
        expectedTokenDetails.put("username", "john.doe");
        expectedTokenDetails.put("email", "john.doe@example.com");
        expectedTokenDetails.put("expiry", "1643218800");
        expectedTokenDetails.put("user_id", "12345");

        when(mockJwtTokenValidator.validate(any())).thenReturn(expectedTokenDetails);
        when(service.getRecord(recordId)).thenThrow(new RequestProcessingException("Something went wrong"));

        mockMvc.perform(get("/ds/rpa/request/accepted/" + recordId)
                .header("Authorization", "Bearer TEST123"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("internal server error: Something went wrong"));
    }

    @Test
    void testGetRecord_UnauthorizedInvalidToken() throws Exception {
        String recordId = "123";

        when(mockJwtTokenValidator.validate(any())).thenReturn(null);

        mockMvc.perform(get("/ds/rpa/request/accepted/" + recordId)
                .header("Authorization", "Bearer TEST123"))
                .andExpect(status().isUnauthorized())
                .andDo(print())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Unauthorized: invalid token"));
    }

    @Test
    void testCreateRecord() throws Exception {
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

        Record record = new Record("record-id", "12345", userInfo);
        RecordWrapper wrapper = new RecordWrapper();
        wrapper.setRecord(record);

        RecordCreationResult creationResult = new RecordCreationResult(wrapper, 200);

        // Mock service call
        when(service.createRecord(any(UserInfoWrapper.class))).thenReturn(creationResult);

        mockMvc.perform(post("/ds/rpa/request/form")
                .content(new ObjectMapper().writeValueAsString(userInfoWrapper))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.record").exists());
    }

    @Test
    void testCreateRecord_RequestProcessingException() throws Exception {
        RequestProcessingException exception = new RequestProcessingException("Test error message");

        when(service.createRecord(any(UserInfoWrapper.class))).thenThrow(exception);

        UserInfoWrapper userInfoWrapper = new UserInfoWrapper();
        mockMvc.perform(post("/ds/rpa/request/form")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(userInfoWrapper)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("internal server error: Test error message"));
    }

    @Test
    void testCreateRecord_InvalidRequestException() throws Exception {
        InvalidRequestException exception = new InvalidRequestException("Test error message");

        when(service.createRecord(any(UserInfoWrapper.class))).thenThrow(exception);

        UserInfoWrapper userInfoWrapper = new UserInfoWrapper();
        mockMvc.perform(post("/ds/rpa/request/form")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(userInfoWrapper)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("invalid request: Test error message"));
    }

    @Test
    void testCreateRecord_TriggersAsyncPostProcessing() throws Exception {
        // Arrange
        UserInfoWrapper userInfoWrapper = new UserInfoWrapper();
        UserInfo userInfo = new UserInfo();
        userInfo.setFullName("Jane Doe");
        userInfo.setOrganization("NASA");
        userInfo.setEmail("jane.doe@test.gov");
        userInfo.setReceiveEmails("True");
        userInfo.setCountry("United States");
        userInfo.setApprovalStatus("pre-approved"); // to trigger post-processing
        userInfo.setProductTitle("Dataset Title");
        userInfo.setSubject("ark:/88434/mds2-1234");
        userInfo.setDescription("Test dataset access");
        userInfoWrapper.setUserInfo(userInfo);

        RecordWrapper recordWrapper = new RecordWrapper();
        recordWrapper.setRecord(new Record("123", "12345", userInfo));

        RecordCreationResult creationResult = new RecordCreationResult(recordWrapper, 200);

        // Mock service call
        when(service.createRecord(any(UserInfoWrapper.class))).thenReturn(creationResult);

        // Act
        mockMvc.perform(post("/ds/rpa/request/form")
                .content(new ObjectMapper().writeValueAsString(userInfoWrapper))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Assert that async post-processing was triggered
        verify(mockAsyncExecutor).handleAfterRecordCreationAsync(
                eq(creationResult.getRecordWrapper()),
                argThat(actual -> areUserInfoWrappersEqual(userInfoWrapper, actual)),
                eq(200));
    
    }

    private boolean areUserInfoWrappersEqual(UserInfoWrapper expected, UserInfoWrapper actual) {
        if (expected == null || actual == null) return false;
        UserInfo exp = expected.getUserInfo();
        UserInfo act = actual.getUserInfo();
        if (exp == null || act == null) return false;
    
        return Objects.equals(exp.getEmail(), act.getEmail()) &&
               Objects.equals(exp.getFullName(), act.getFullName()) &&
               Objects.equals(exp.getSubject(), act.getSubject()) &&
               Objects.equals(exp.getCountry(), act.getCountry()) &&
               Objects.equals(exp.getOrganization(), act.getOrganization()) &&
               Objects.equals(exp.getProductTitle(), act.getProductTitle()) &&
               Objects.equals(exp.getDescription(), act.getDescription()) &&
               Objects.equals(exp.getApprovalStatus(), act.getApprovalStatus());
    }
    


    @Test
    void testUpdateRecord() throws Exception {
        String recordId = "123";
        String status = "Approved";
        Record record = new Record(recordId, "12345", new UserInfo());
        RecordStatus recordStatus = new RecordStatus(recordId, status);
        UpdateRecordResult updateResult = new UpdateRecordResult(recordStatus, record, "randomId");
        when(service.updateRecord(anyString(), anyString(), anyString())).thenReturn(updateResult);

        Map<String, String> expectedTokenDetails = new HashMap<>();
        expectedTokenDetails.put("userFullname", "john.doe");
        expectedTokenDetails.put("userEmail", "john.doe@example.com");
        expectedTokenDetails.put("expiry", "1643218800");
        expectedTokenDetails.put("user_id", "12345");

        when(mockJwtTokenValidator.validate(any())).thenReturn(expectedTokenDetails);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer TEST123");

        RecordPatch recordPatch = new RecordPatch(status);
        mockMvc.perform(patch("/ds/rpa/request/accepted/" + recordId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(recordPatch))
                .header(HttpHeaders.AUTHORIZATION, headers.getFirst(HttpHeaders.AUTHORIZATION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordId").value("123"));
    }

    @Test
    void testUpdateRecord_RecordNotFoundException() throws Exception {
        String recordId = "123";
        RecordNotFoundException exception = RecordNotFoundException.fromRecordId(recordId);
        when(service.updateRecord(anyString(), anyString(), anyString())).thenThrow(exception);

        Map<String, String> expectedTokenDetails = new HashMap<>();
        expectedTokenDetails.put("userFullname", "john.doe");
        expectedTokenDetails.put("userEmail", "john.doe@example.com");
        expectedTokenDetails.put("expiry", "1643218800");
        expectedTokenDetails.put("user_id", "12345");

        when(mockJwtTokenValidator.validate(any())).thenReturn(expectedTokenDetails);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer TEST123");

        mockMvc.perform(patch("/ds/rpa/request/accepted/" + recordId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(new RecordPatch("Pending")))
                .header(HttpHeaders.AUTHORIZATION, headers.getFirst(HttpHeaders.AUTHORIZATION)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("record not found: Record with ID=123 could not be found"));
    }

    @Test
    void testUpdateRecord_RequestProcessingException() throws Exception {
        String recordId = "123";
        RequestProcessingException exception = new RequestProcessingException("Something went wrong");
        when(service.updateRecord(anyString(), anyString(), anyString())).thenThrow(exception);

        Map<String, String> expectedTokenDetails = new HashMap<>();
        expectedTokenDetails.put("userFullName", "john.doe");
        expectedTokenDetails.put("userEmail", "john.doe@example.com");
        expectedTokenDetails.put("expiry", "1643218800");
        expectedTokenDetails.put("user_id", "12345");

        when(mockJwtTokenValidator.validate(any())).thenReturn(expectedTokenDetails);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer TEST123");

        mockMvc.perform(patch("/ds/rpa/request/accepted/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(new RecordPatch("Pending")))
                .header(HttpHeaders.AUTHORIZATION, headers.getFirst(HttpHeaders.AUTHORIZATION)))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("internal server error: Something went wrong"));
    }

    @Test
    void testHandleRecordNotFoundException() {
        // Arrange
        String recordId = "123";
        String errorMessage = "Record with ID=" + recordId + " could not be found";
        RecordNotFoundException exception = RecordNotFoundException.fromRecordId(recordId);

        // Act: Call the exception handler method
        ResponseEntity<ErrorInfo> response = controller.handleRecordNotFoundException(exception);

        // Assert: Check the status code
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(), "Expected 404 NOT FOUND");

        // Assert: Check the response body (ErrorInfo)
        ErrorInfo errorInfo = response.getBody();
        assertNotNull(errorInfo, "Expected non-null ErrorInfo in the response body");
        assertEquals(404, errorInfo.status, "Expected status to be 404 in the error info");
        assertEquals("record not found: " + errorMessage, errorInfo.message, "Expected error message to match");
    }

    @Test
    void testHandleRequestProcessingException() {
        String message = "Test error message";
        RequestProcessingException ex = new RequestProcessingException(message);
        ResponseEntity<ErrorInfo> response = controller.handleRequestProcessingException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getBody().status);
        assertEquals("internal server error: " + message, response.getBody().message);
    }

    @Test
    void testHandleInvalidRequestException() {
        String errorMessage = "Invalid input data";
        InvalidRequestException exception = new InvalidRequestException(errorMessage);
        ResponseEntity<ErrorInfo> response = controller.handleInvalidRequestException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().status);
        assertEquals("invalid request: " + errorMessage, response.getBody().message);
    }

    @Test
    void testRecaptchaVerificationFailedException() {
        String errorMessage = "reCAPTCHA failed";
        RecaptchaVerificationFailedException exception = new RecaptchaVerificationFailedException(errorMessage);
        ResponseEntity<ErrorInfo> response = controller.handleRecaptchaVerificationFailedException(exception);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getBody().status);
        assertEquals("Unauthorized: " + errorMessage, response.getBody().message);
    }

    @Test
    void testHandleUnauthorizedException() {
        String errorMessage = "invalid token";
        UnauthorizedException exception = new UnauthorizedException(errorMessage);
        ResponseEntity<ErrorInfo> response = controller.handleUnauthorizedException(exception);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getBody().status);
        assertEquals("Unauthorized: " + errorMessage, response.getBody().message);
    }

}
