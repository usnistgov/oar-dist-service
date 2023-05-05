package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.UserInfo;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for the {@link HTMLSanitizer} class.
 * The tests cover the different use cases for the {@link HTMLSanitizer#sanitize()} ()} method, which is designed to help protect against
 * cross-site scripting (XSS) attacks by sanitizing input strings and objects that were provided by the user.
 * <p>
 * The test cases include:
 * <ul>
 *     <li>Sanitizing a simple input string containing HTML tags</li>
 *     <li>Sanitizing a StringBuilder object containing HTML tags</li>
 *     <li>Sanitizing a Map object containing HTML tags</li>
 *     <li>Sanitizing an array of strings containing HTML tags</li>
 *     <li>Sanitizing a custom object {@link UserInfoWrapper} containing string fields with HTML tags</li>
 * </ul>
 */
public class HTMLSanitizerTest {

    @Test
    public void testSanitize_StringObject() {
        String input = "<a>Some simple text</a>";
        String sanitizedInput = HTMLSanitizer.sanitize(input);
        Assert.assertEquals("Some simple text", sanitizedInput);
    }

    @Test
    public void testSanitize_NullStringObject() {
        String sanitizedInput = HTMLSanitizer.sanitize(null);
        Assert.assertEquals(null, sanitizedInput);
    }

    @Test
    public void testSanitize_StringBuilderObject() {
        // input sb
        StringBuilder sb = new StringBuilder().append("<a>").append("Hello, there!").append("</a>");
        // sanitized sb
        StringBuilder sanitizedSb = HTMLSanitizer.sanitize(sb);
        Assert.assertEquals("Hello, there!", sanitizedSb.toString());
    }

    @Test
    public void testSanitize_MapObject() {
        // input map
        Map<String, String> map = new HashMap<>();
        map.put("fullName", "<a>John Doe</a>");
        map.put("organization", "<script>alert('malicious script');</script>NIST");
        // sanitized map
        Map<String, String> sanitizedMap = HTMLSanitizer.sanitize(map);
        // expected map
        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put("fullName", "John Doe");
        expectedMap.put("organization", "NIST");
        // assert
        Assert.assertEquals(expectedMap, sanitizedMap);
    }

    @Test
    public void testSanitize_ArrayObject() {

        // input array
        String[] array = new String[] {
                "<a href='https://example.com'>John Doe</a>",
                "<script>alert('malicious script');</script>NIST"
        };

        // sanitized array
        String[] sanitizedArray = HTMLSanitizer.sanitize(array);

        // expected array
        String[] expectedArray = new String[] {
                "John Doe",
                "NIST"
        };
        // assert
        Assert.assertArrayEquals(expectedArray, sanitizedArray);
    }

    @Test
    public void testSanitize_UserInfoWrapperObject() {
        String testDescription = "Product Title: Example Title " +
                "Set\n\nPurpose of Use: Research purposes for a publication\n\nAddress:\n100 Bureau " +
                "Drive\nGaithersburg, MD, 20899";
        // Create a test object
        UserInfoWrapper userInfoWrapper = new UserInfoWrapper();
        UserInfo userInfo = new UserInfo();
        userInfo.setFullName("<a>John Doe</a>");
        userInfo.setOrganization("<script>alert('malicious script');</script>NIST");
        userInfo.setCountry("<a href='http://example.com'>USA</a>");
        userInfo.setEmail("john.doe@example.com");
        userInfo.setReceiveEmails("true");
        userInfo.setSubject("Test subject");
        userInfo.setDescription(testDescription);
        userInfo.setProductTitle("<div>Test dataset</div>");
        userInfo.setApprovalStatus("pending");
        userInfoWrapper.setUserInfo(userInfo);

        // Sanitize the test object
        HTMLSanitizer.sanitize(userInfoWrapper);

        // Verify that the HTML tags and attributes have been sanitized
        Assert.assertEquals("NIST", userInfoWrapper.getUserInfo().getOrganization());
        Assert.assertEquals("John Doe", userInfoWrapper.getUserInfo().getFullName());
        Assert.assertEquals("USA", userInfoWrapper.getUserInfo().getCountry());
        Assert.assertEquals("Test dataset", userInfoWrapper.getUserInfo().getProductTitle());
        // This checks line breaks as well
        Assert.assertEquals(testDescription, userInfoWrapper.getUserInfo().getDescription());
    }
}
