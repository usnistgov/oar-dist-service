package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UserInfoTest {

    @Test
    public void testSerialization() throws Exception {
        UserInfo userInfo = new UserInfo();
        userInfo.setFullName("John Doe");
        userInfo.setEmail("johndoe@example.com");
        userInfo.setCountry("United States");
        userInfo.setReceiveEmails("True");
        userInfo.setProductTitle("Some product title");
        userInfo.setSubject("1234");
        userInfo.setDescription("Some description");

        // Add an unsupported parameter to UserInfo
        userInfo.setUnknownProperty("unsupportedParam", "some value");

        ObjectMapper mapper = new ObjectMapper();

        String jsonStr = mapper.writeValueAsString(userInfo);

        // Check that the serialized string does not contain "unknownProperties"
        assertFalse(jsonStr.contains("unknownProperties"));

        // Validate the contents of the serialized string
        assertTrue(jsonStr.contains("John Doe"));
        assertTrue(jsonStr.contains("johndoe@example.com"));
        assertTrue(jsonStr.contains("United States"));
    }

    @Test
    public void testDeserialization() throws Exception {
        // Prepare a JSON string for deserialization
        String jsonStr = "{\"fullName\":\"John Doe\", \"organization\":\"NASA\", " +
                "\"email\":\"johndoe@example.com\", \"country\":\"United States\", \"receiveEmails\":\"True\", " +
                "\"approvalStatus\":\"Pending\", \"productTitle\":\"Some product title\", \"subject\":\"1234\", " +
                "\"description\":\"Some description\"}";

        ObjectMapper mapper = new ObjectMapper();

        // Deserialize the JSON string into a UserInfo object
        UserInfo userInfo = mapper.readValue(jsonStr, UserInfo.class);

        // Validate the contents of the UserInfo object
        assertEquals("John Doe", userInfo.getFullName());
        assertEquals("NASA", userInfo.getOrganization());
        assertEquals("johndoe@example.com", userInfo.getEmail());
        assertEquals("United States", userInfo.getCountry());
        assertEquals("True", userInfo.getReceiveEmails());
        assertEquals("Pending", userInfo.getApprovalStatus());
        assertEquals("Some product title", userInfo.getProductTitle());
        assertEquals("1234", userInfo.getSubject());
        assertEquals("Some description", userInfo.getDescription());
    }
}
