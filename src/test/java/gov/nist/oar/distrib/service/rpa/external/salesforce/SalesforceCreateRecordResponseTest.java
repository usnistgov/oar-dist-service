package gov.nist.oar.distrib.service.rpa.external.salesforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.rpa.client.CreateRecordResponse;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SalesforceCreateRecordResponseTest {

    private ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    public void testSerializationDeserialization() throws Exception {
        // Create a sample SalesforceCreateRecordResponse object
        SalesforceCreateRecordResponse originalResponse = new SalesforceCreateRecordResponse();
        SalesforceCreateRecordResponse.Record record = new SalesforceCreateRecordResponse.Record();
        record.setId("123");
        record.setCaseNum("abc123");
        SalesforceCreateRecordResponse.UserInfo userInfo = new SalesforceCreateRecordResponse.UserInfo();
        userInfo.setFullName("John Doe");
        userInfo.setOrganization("Example Org");
        userInfo.setEmail("john.doe@example.com");
        userInfo.setReceiveEmails("true");
        userInfo.setCountry("USA");
        userInfo.setApprovalStatus("Pending");
        userInfo.setProductTitle("Example Product");
        userInfo.setSubject("Example Subject");
        userInfo.setDescription("Example Description");
        record.setUserInfo(userInfo);
        originalResponse.setRecord(record);

        // Serialize the object to JSON
        String json = objectMapper.writeValueAsString(originalResponse);

        // Assert serialization
        String expectedJson = "{\"record\":{\"id\":\"123\",\"caseNum\":\"abc123\"," +
                "\"userInfo\":{\"fullName\":\"John Doe\",\"organization\":\"Example Org\"," +
                "\"email\":\"john.doe@example.com\",\"receiveEmails\":\"true\",\"country\":\"USA\"," +
                "\"approvalStatus\":\"Pending\",\"productTitle\":\"Example Product\"," +
                "\"subject\":\"Example Subject\",\"description\":\"Example Description\"}}}";

        assertEquals(expectedJson, json);

        // Deserialize the JSON back to the object
        SalesforceCreateRecordResponse deserializedResponse = objectMapper.readValue(json, SalesforceCreateRecordResponse.class);

        // Assert that the deserialized object matches the original object
        assertEquals(originalResponse.getRecord().getId(), deserializedResponse.getRecord().getId());
        assertEquals(originalResponse.getRecord().getCaseNum(), deserializedResponse.getRecord().getCaseNum());
        assertEquals(originalResponse.getRecord().getUserInfo().getFullName(), deserializedResponse.getRecord().getUserInfo().getFullName());
        assertEquals(originalResponse.getRecord().getUserInfo().getOrganization(), deserializedResponse.getRecord().getUserInfo().getOrganization());
        assertEquals(originalResponse.getRecord().getUserInfo().getEmail(), deserializedResponse.getRecord().getUserInfo().getEmail());
        assertEquals(originalResponse.getRecord().getUserInfo().getReceiveEmails(), deserializedResponse.getRecord().getUserInfo().getReceiveEmails());
        assertEquals(originalResponse.getRecord().getUserInfo().getCountry(), deserializedResponse.getRecord().getUserInfo().getCountry());
        assertEquals(originalResponse.getRecord().getUserInfo().getApprovalStatus(), deserializedResponse.getRecord().getUserInfo().getApprovalStatus());
        assertEquals(originalResponse.getRecord().getUserInfo().getProductTitle(), deserializedResponse.getRecord().getUserInfo().getProductTitle());
        assertEquals(originalResponse.getRecord().getUserInfo().getSubject(), deserializedResponse.getRecord().getUserInfo().getSubject());
        assertEquals(originalResponse.getRecord().getUserInfo().getDescription(), deserializedResponse.getRecord().getUserInfo().getDescription());
    }

    @Test
    public void testToCreateRecordResponse() {
        // Create a sample SalesforceCreateRecordResponse object
        SalesforceCreateRecordResponse salesforceResponse = new SalesforceCreateRecordResponse();
        SalesforceCreateRecordResponse.Record record = new SalesforceCreateRecordResponse.Record();
        record.setId("123");
        record.setCaseNum("abc123");
        SalesforceCreateRecordResponse.UserInfo userInfo = new SalesforceCreateRecordResponse.UserInfo();
        userInfo.setFullName("John Doe");
        userInfo.setOrganization("Example Org");
        userInfo.setEmail("john.doe@example.com");
        userInfo.setReceiveEmails("true");
        userInfo.setCountry("USA");
        userInfo.setApprovalStatus("Pending");
        userInfo.setProductTitle("Example Product");
        userInfo.setSubject("Example Subject");
        userInfo.setDescription("Example Description");
        record.setUserInfo(userInfo);
        salesforceResponse.setRecord(record);

        // Call the toCreateRecordResponse() method
        CreateRecordResponse response = salesforceResponse.toCreateRecordResponse();

        // Assert the expected values
        assertEquals("123", response.getRecordId());
        assertEquals("abc123", response.getCaseNum());
        assertEquals("John Doe", response.getUserInfo_FullName());
        assertEquals("Example Org", response.getUserInfo_Organization());
        assertEquals("john.doe@example.com", response.getUserInfo_Email());
        assertEquals("true", response.getUserInfo_ReceiveEmails());
        assertEquals("USA", response.getUserInfo_Country());
        assertEquals("Pending", response.getUserInfo_ApprovalStatus());
        assertEquals("Example Product", response.getUserInfo_ProductTitle());
        assertEquals("Example Subject", response.getUserInfo_Subject());
        assertEquals("Example Description", response.getUserInfo_Description());
    }
}
