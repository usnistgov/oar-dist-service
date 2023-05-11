package gov.nist.oar.distrib.service.rpa.external.salesforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SalesforceCreateRecordPayloadTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerialization() throws Exception {
        SalesforceCreateRecordPayload.UserInfo userInfo = new SalesforceCreateRecordPayload.UserInfo();
        userInfo.setFullName("John Doe");
        userInfo.setOrganization("Example Org");
        userInfo.setEmail("john.doe@example.com");
        userInfo.setReceiveEmails("true");
        userInfo.setCountry("USA");
        userInfo.setApprovalStatus("Pending");
        userInfo.setProductTitle("Example Product");
        userInfo.setSubject("Example Subject");
        userInfo.setDescription("Example Description");

        SalesforceCreateRecordPayload payload = new SalesforceCreateRecordPayload();
        payload.setUserInfo(userInfo);

        String expectedJson = "{\"userInfo\":{\"fullName\":\"John Doe\",\"organization\":\"Example Org\",\"email\":\"john.doe@example.com\",\"receiveEmails\":\"true\",\"country\":\"USA\",\"approvalStatus\":\"Pending\",\"productTitle\":\"Example Product\",\"subject\":\"Example Subject\",\"description\":\"Example Description\"}}";

        String actualJson = objectMapper.writeValueAsString(payload);

        assertEquals(expectedJson, actualJson);
    }

    @Test
    public void testDeserialization() throws Exception {
        String json = "{\"userInfo\":{\"fullName\":\"John Doe\",\"organization\":\"Example Org\",\"email\":\"john.doe@example.com\",\"receiveEmails\":\"true\",\"country\":\"USA\",\"approvalStatus\":\"Pending\",\"productTitle\":\"Example Product\",\"subject\":\"Example Subject\",\"description\":\"Example Description\"}}";

        SalesforceCreateRecordPayload actualPayload = objectMapper.readValue(json, SalesforceCreateRecordPayload.class);

        assertEquals("John Doe", actualPayload.getUserInfo().getFullName());
        assertEquals("Example Org", actualPayload.getUserInfo().getOrganization());
        assertEquals("john.doe@example.com", actualPayload.getUserInfo().getEmail());
        assertEquals("true", actualPayload.getUserInfo().getReceiveEmails());
        assertEquals("USA", actualPayload.getUserInfo().getCountry());
        assertEquals("Pending", actualPayload.getUserInfo().getApprovalStatus());
        assertEquals("Example Product", actualPayload.getUserInfo().getProductTitle());
        assertEquals("Example Subject", actualPayload.getUserInfo().getSubject());
        assertEquals("Example Description", actualPayload.getUserInfo().getDescription());
    }
}

