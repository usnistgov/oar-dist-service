package gov.nist.oar.distrib.service.rpa.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.rpa.client.impl.GetRecordResponseImpl;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class GetRecordResponseImplTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Test
    public void testDeserialization() throws IOException {
        String json = "{\n" +
                "    \"record\": {\n" +
                "        \"id\": \"5003R000004olaFQAQ\",\n" +
                "        \"caseNum\": \"00229300\",\n" +
                "        \"userInfo\": {\n" +
                "            \"fullName\": \"Omar I EL MIMOUNI\",\n" +
                "            \"organization\": \"NIST\",\n" +
                "            \"email\": \"omarilias.elmimouni@nist.gov\",\n" +
                "            \"receiveEmails\": \"No\",\n" +
                "            \"country\": \"United States\",\n" +
                "            \"approvalStatus\": \"Declined_2023-05-10T22:10:28.223Z\",\n" +
                "            \"productTitle\": \"NIST Fingerprint Image Quality (NFIQ) 2 Conformance Test Set\",\n" +
                "            \"subject\": \"ark:/88434/mds2-2909\",\n" +
                "            \"description\": \"Product Title: NIST Fingerprint Image Quality (NFIQ) 2 Conformance Test Set\\n\\nAddress:\\n100 Bureau Dr.\\nGaithersburg, MD 20899\"\n" +
                "        }\n" +
                "    }\n" +
                "}";

        // Deserialize the JSON to GetRecordResponseImpl
        GetRecordResponseImpl response = objectMapper.readValue(json, GetRecordResponseImpl.class);

        // Perform assertions
        assertEquals("5003R000004olaFQAQ", response.getRecordId());
        assertEquals("00229300", response.getCaseNum());
        assertEquals("Omar I EL MIMOUNI", response.getUserInfo_FullName());
        assertEquals("NIST", response.getUserInfo_Organization());
        assertEquals("omarilias.elmimouni@nist.gov", response.getUserInfo_Email());
        assertEquals("No", response.getUserInfo_ReceiveEmails());
        assertEquals("United States", response.getUserInfo_Country());
        assertEquals("Declined_2023-05-10T22:10:28.223Z", response.getUserInfo_ApprovalStatus());
        assertEquals("NIST Fingerprint Image Quality (NFIQ) 2 Conformance Test Set", response.getUserInfo_ProductTitle());
        assertEquals("ark:/88434/mds2-2909", response.getUserInfo_Subject());
        assertEquals("Product Title: NIST Fingerprint Image Quality (NFIQ) 2 Conformance Test Set\n\nAddress:\n100 Bureau Dr.\nGaithersburg, MD 20899", response.getUserInfo_Description());

    }

    @Test
    public void testSerialization() throws Exception {

        String expectedJson = "{\"record\":{\"id\":\"5003R000004olaFQAQ\",\"caseNum\":\"00229300\"," +
                "\"userInfo\":{\"fullName\":\"Omar I EL MIMOUNI\",\"organization\":\"NIST\"," +
                "\"email\":\"omarilias.elmimouni@nist.gov\",\"receiveEmails\":\"No\"," +
                "\"country\":\"United States\",\"approvalStatus\":\"Declined_2023-05-10T22:10:28.223Z\"," +
                "\"productTitle\":\"NIST Fingerprint Image Quality (NFIQ) 2 Conformance Test Set\"," +
                "\"subject\":\"ark:/88434/mds2-2909\",\"description\":\"Product Title: NIST Fingerprint Image Quality " +
                "(NFIQ) 2 Conformance Test Set\\n\\nAddress:\\n100 Bureau Dr.\\nGaithersburg, MD 20899\"}}}";

        // Create an instance of GetRecordResponseImpl
        GetRecordResponseImpl response = new GetRecordResponseImpl();

        // Set the necessary fields
        response.setRecordId("5003R000004olaFQAQ");
        response.setCaseNum("00229300");
        response.setUserInfo_FullName("Omar I EL MIMOUNI");
        response.setUserInfo_Organization("NIST");
        response.setUserInfo_Email("omarilias.elmimouni@nist.gov");
        response.setUserInfo_ReceiveEmails("No");
        response.setUserInfo_Country("United States");
        response.setUserInfo_ApprovalStatus("Declined_2023-05-10T22:10:28.223Z");
        response.setUserInfo_ProductTitle("NIST Fingerprint Image Quality (NFIQ) 2 Conformance Test Set");
        response.setUserInfo_Subject("ark:/88434/mds2-2909");
        response.setUserInfo_Description("Product Title: NIST Fingerprint Image Quality (NFIQ) 2 Conformance Test " +
                "Set\n\nAddress:\n100 Bureau Dr.\nGaithersburg, MD 20899");

        // Serialize the response to JSON
        String json = objectMapper.writeValueAsString(response);

        assertEquals(expectedJson, json);
    }
}
