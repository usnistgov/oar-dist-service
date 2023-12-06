package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import gov.nist.oar.distrib.service.rpa.model.UserInfo;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class RequestSanitizerTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private RequestSanitizer requestSanitizer;

    @Before
    public void setup() {
        requestSanitizer = new RequestSanitizer();
    }

    @Test
    public void testSanitizeAndValidate_validInput() {

        // create a UserInfoWrapper instance with valid data
        UserInfo validUserInfo = new UserInfo();
        validUserInfo.setFullName("Jane Doe");
        validUserInfo.setOrganization("NASA");
        validUserInfo.setEmail("jane.doe@test.gov");
        validUserInfo.setReceiveEmails("True");
        validUserInfo.setCountry("United States");
        validUserInfo.setApprovalStatus("");
        validUserInfo.setProductTitle("Some product title");
        validUserInfo.setSubject("ark:\\88434\\mds2\\2106");
        validUserInfo.setDescription("Some description");

        UserInfoWrapper validWrapper = new UserInfoWrapper(validUserInfo, "test_recaptcha");

        // no exception should be thrown for valid input
        requestSanitizer.sanitizeAndValidate(validWrapper);
    }

    @Test(expected = InvalidRequestException.class)
    public void testSanitizeAndValidate_invalidInput_emptyField() {

        // create a UserInfoWrapper instance with invalid data
        UserInfo invalidUserInfo = new UserInfo();
        invalidUserInfo.setFullName("");
        invalidUserInfo.setOrganization("NASA");
        invalidUserInfo.setEmail("jane.doe@test.gov");
        invalidUserInfo.setReceiveEmails("True");
        invalidUserInfo.setCountry("United States");
        invalidUserInfo.setApprovalStatus("Pending");
        invalidUserInfo.setProductTitle("Some product title");
        invalidUserInfo.setSubject("ark:\\88434\\mds2\\2106");
        invalidUserInfo.setDescription("Some description");

        UserInfoWrapper invalidWrapper = new UserInfoWrapper(invalidUserInfo, "test_recaptcha");

        // exception should be thrown for invalid input
        requestSanitizer.sanitizeAndValidate(invalidWrapper);
    }

    @Test
    public void testSanitizeAndValidate_invalidInput_unsupportedParam() {

        // create a UserInfo instance with valid data
        // create a UserInfoWrapper instance with invalid data
        UserInfo invalidUserInfo = new UserInfo();
        invalidUserInfo.setFullName("Jane Doe");
        invalidUserInfo.setOrganization("NASA");
        invalidUserInfo.setEmail("jane.doe@test.gov");
        invalidUserInfo.setReceiveEmails("True");
        invalidUserInfo.setCountry("United States");
        invalidUserInfo.setApprovalStatus("Pending");
        invalidUserInfo.setProductTitle("Some product title");
        invalidUserInfo.setSubject("ark:\\88434\\mds2\\2106");
        invalidUserInfo.setDescription("Some description");

        // Add an unsupported parameter to UserInfo
        invalidUserInfo.setUnknownProperty("unsupportedParam", "someValue");
        invalidUserInfo.setUnknownProperty("anotherUnsupportedParam", "someOtherValue");

        UserInfoWrapper invalidWrapper = new UserInfoWrapper(invalidUserInfo, "test_recaptcha");

        // exception should be thrown for invalid input
        try {
            requestSanitizer.sanitizeAndValidate(invalidWrapper);
            fail("Expected an InvalidRequestException to be thrown");
        } catch (InvalidRequestException e) {
            assertEquals("Request payload contains unsupported parameters: [anotherUnsupportedParam, unsupportedParam]", e.getMessage());
        }
    }

    @Test
    public void testValidateKnownFields_MissingRequiredField() {
        // create a UserInfo instance with a missing required field
        UserInfo invalidUserInfo = new UserInfo();
        // fullName is a required field and we are not setting it
        invalidUserInfo.setOrganization("NASA");
        invalidUserInfo.setEmail("jane.doe@test.gov");
        invalidUserInfo.setReceiveEmails("True");
        invalidUserInfo.setCountry("United States");
        invalidUserInfo.setApprovalStatus("Pending");
        invalidUserInfo.setProductTitle("Some product title");
        invalidUserInfo.setSubject("ark:\\88434\\mds2\\2106");
        invalidUserInfo.setDescription("Some description");

        UserInfoWrapper invalidWrapper = new UserInfoWrapper(invalidUserInfo, "test_recaptcha");

        try {
            requestSanitizer.sanitizeAndValidate(invalidWrapper);
            fail("Expected InvalidRequestException to be thrown");
        } catch (InvalidRequestException e) {
            assertEquals("fullName cannot be blank.", e.getMessage());
        }
    }

    @Test
    public void testValidateKnownFields_BlankRequiredField() {
        // create a UserInfo instance with a blank required field
        UserInfo invalidUserInfo = new UserInfo();
        // fullName is a required field and we are setting it to a blank string
        invalidUserInfo.setFullName("");
        invalidUserInfo.setOrganization("NASA");
        invalidUserInfo.setEmail("jane.doe@test.gov");
        invalidUserInfo.setReceiveEmails("True");
        invalidUserInfo.setCountry("United States");
        invalidUserInfo.setApprovalStatus("Pending");
        invalidUserInfo.setProductTitle("Some product title");
        invalidUserInfo.setSubject("ark:\\88434\\mds2\\2106");
        invalidUserInfo.setDescription("Some description");

        UserInfoWrapper invalidWrapper = new UserInfoWrapper(invalidUserInfo, "test_recaptcha");

        try {
            requestSanitizer.sanitizeAndValidate(invalidWrapper);
            fail("Expected InvalidRequestException to be thrown");
        } catch (InvalidRequestException e) {
            assertEquals("fullName cannot be blank.", e.getMessage());
        }
    }

    @Test
    public void testSanitizeAndValidate_validInput_nonRequiredFieldMissing() {
        // create a UserInfo instance with valid data
        UserInfo validUserInfo = new UserInfo();
        validUserInfo.setFullName("Jane Doe");
        validUserInfo.setOrganization("NASA");
        validUserInfo.setEmail("jane.doe@test.gov");
        validUserInfo.setCountry("United States");
        validUserInfo.setProductTitle("Some product title");
        validUserInfo.setSubject("ark:\\88434\\mds2\\2106");
        validUserInfo.setDescription("Some description");

        // leave out non-required fields
        // validUserInfo.setApprovalStatus(null);
        // validUserInfo.setReceiveEmails(null);

        UserInfoWrapper validWrapper = new UserInfoWrapper(validUserInfo, "test_recaptcha");

        // no exception should be thrown
        requestSanitizer.sanitizeAndValidate(validWrapper);

        // test will pass if no exception is thrown
    }


}
