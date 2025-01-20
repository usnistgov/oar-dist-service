package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UserInfoWrapperTest {

    @Test
    public void testSerialization() throws Exception {
        UserInfoWrapper userInfoWrapper = new UserInfoWrapper();
        UserInfo userInfo = new UserInfo();
        userInfo.setFullName("John Doe");
        userInfo.setEmail("johndoe@example.com");
        userInfo.setCountry("United States");
        userInfo.setReceiveEmails("True");
        userInfo.setProductTitle("Some product title");
        userInfo.setSubject("1234");
        userInfo.setDescription("Some description");

        userInfoWrapper.setUserInfo(userInfo);

        ObjectMapper mapper = new ObjectMapper();

        String jsonStr = mapper.writeValueAsString(userInfoWrapper);

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
        String jsonStr = "{\"userInfo\":{\"fullName\":\"John Doe\", \"organization\":\"NASA\", " +
                "\"email\":\"johndoe@example.com\", \"country\":\"United States\",\"receiveEmails\":\"True\", " +
                "\"approvalStatus\":\"Pending\", \"productTitle\":\"Some product title\", \"subject\":\"1234\", " +
                "\"description\":\"Some description\"},\"recaptcha\":\"test_recaptcha\"}";

        ObjectMapper mapper = new ObjectMapper();

        // Deserialize the JSON string into a UserInfoWrapper object
        UserInfoWrapper userInfoWrapper = mapper.readValue(jsonStr, UserInfoWrapper.class);

        // Validate the contents of the UserInfoWrapper object
        assertEquals("John Doe", userInfoWrapper.getUserInfo().getFullName());
        assertEquals("NASA", userInfoWrapper.getUserInfo().getOrganization());
        assertEquals("johndoe@example.com", userInfoWrapper.getUserInfo().getEmail());
        assertEquals("United States", userInfoWrapper.getUserInfo().getCountry());
        assertEquals("True", userInfoWrapper.getUserInfo().getReceiveEmails());
        assertEquals("Pending", userInfoWrapper.getUserInfo().getApprovalStatus());
        assertEquals("Some product title", userInfoWrapper.getUserInfo().getProductTitle());
        assertEquals("1234", userInfoWrapper.getUserInfo().getSubject());
        assertEquals("Some description", userInfoWrapper.getUserInfo().getDescription());
        assertEquals("test_recaptcha", userInfoWrapper.getRecaptcha());
    }

    @Test
    public void testUnknownPropertiesCaptured() throws Exception {
        // Create a JSON string with known and unknown fields
        String jsonStr = "{\"userInfo\":{\"fullName\":\"John Doe\",\"email\":\"johndoe@example.com\"," +
                "\"country\":\"United States\"},\"recaptcha\":\"test_recaptcha\",\"unsupportedParam\":\"someValue\"" +
                ",\"anotherUnsupportedParam\":\"someOtherValue\"}";

        // Use ObjectMapper to deserialize the JSON string into a UserInfoWrapper object
        ObjectMapper mapper = new ObjectMapper();
        UserInfoWrapper userInfoWrapper = mapper.readValue(jsonStr, UserInfoWrapper.class);

        // Retrieve the unknown properties from the UserInfoWrapper object
        Map<String, Object> unknownProperties = userInfoWrapper.getUnknownProperties();

        // Verify that the unknown properties were correctly captured
        assertEquals("someValue", unknownProperties.get("unsupportedParam"));
        assertEquals("someOtherValue", unknownProperties.get("anotherUnsupportedParam"));
    }
}
