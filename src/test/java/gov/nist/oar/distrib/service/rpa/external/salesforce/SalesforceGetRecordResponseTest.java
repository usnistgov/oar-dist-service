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
        String json = "{\n" +
                "  \"record\": {\n" +
                "    \"id\": \"123\",\n" +
                "    \"caseNum\": \"abc123\",\n" +
                "    \"userInfo\": {\n" +
                "      \"fullName\": \"John Doe\",\n" +
                "      \"organization\": \"Example Org\",\n" +
                "      \"email\": \"john.doe@example.com\",\n" +
                "      \"receiveEmails\": \"true\",\n" +
                "      \"country\": \"USA\",\n" +
                "      \"approvalStatus\": \"Pending\",\n" +
                "      \"productTitle\": \"Example Product\",\n" +
                "      \"subject\": \"Example Subject\",\n" +
                "      \"description\": \"Example Description\"\n" +
                "    }\n" +
                "  }\n" +
                "}";

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
