package gov.nist.oar.distrib.service.rpa.external.salesforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.rpa.client.GetRecordResponse;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;


public class SalesforceGetRecordResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testToGetRecordResponse() throws IOException {
        // Sample JSON input
        String json = "{ \"record\": { \"id\": \"123\", \"caseNum\": \"abc123\", \"userInfo\": { \"fullName\": \"John Doe\", \"organization\": \"Example Org\", \"email\": \"john.doe@example.com\", \"receiveEmails\": \"true\", \"country\": \"USA\", \"approvalStatus\": \"Pending\", \"productTitle\": \"Example Product\", \"subject\": \"Example Subject\", \"description\": \"Example Description\" } } }";

        // Deserialize the JSON to SalesforceGetRecordResponse
        SalesforceGetRecordResponse response = objectMapper.readValue(json, SalesforceGetRecordResponse.class);

        // Convert to GetRecordResponse
        GetRecordResponse getRecordResponse = response.toGetRecordResponse();

        // Verify the converted values
        assertEquals("123", getRecordResponse.getRecordId());
        assertEquals("abc123", getRecordResponse.getCaseNum());
        assertEquals("John Doe", getRecordResponse.getUserInfo_FullName());
        assertEquals("Example Org", getRecordResponse.getUserInfo_Organization());
        assertEquals("john.doe@example.com", getRecordResponse.getUserInfo_Email());
        assertEquals("true", getRecordResponse.getUserInfo_ReceiveEmails());
        assertEquals("USA", getRecordResponse.getUserInfo_Country());
        assertEquals("Pending", getRecordResponse.getUserInfo_ApprovalStatus());
        assertEquals("Example Product", getRecordResponse.getUserInfo_ProductTitle());
        assertEquals("Example Subject", getRecordResponse.getUserInfo_Subject());
        assertEquals("Example Description", getRecordResponse.getUserInfo_Description());
    }
}
