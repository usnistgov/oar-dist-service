package gov.nist.oar.distrib.service.rpa;
import gov.nist.oar.distrib.service.rpa.model.EmailInfo;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.service.rpa.model.UserInfo;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the EmailInfoProvider class.
 * <p>
 * This test class relies on Mockito to mock dependencies and isolate the EmailInfoProvider class
 * during testing. It verifies the behavior of the EmailInfoProvider's functions, to ensure they
 * return the correct EmailInfo objects based on the provided input.
 * <p>
 * The tests cover various scenarios, including different email types (e.g., SME approval,
 * end-user confirmation, etc.) and different input data. Additional tests can be added as needed
 * to cover any new functionality or edge cases.
 * <p>
 * The test cases include:
 * <ul>
 *     <li>Getting the Subject-Matter Expert (SME) approval EmailInfo</li>
 *     <li>Getting the end-user confirmation EmailInfo</li>
 *     <li>Getting the EmailInfo for when user is approved</li>
 * </ul>
 */
public class EmailInfoProviderTest {
    private EmailInfoProvider emailInfoProvider;
    @Mock
    private Record record;
    @Mock
    private UserInfo userInfo;
    @Mock
    private RPAConfiguration rpaConfiguration;
    @Mock
    private RPAConfiguration.EmailTemplate endUserConfirmationEmail;
    @Mock
    private RPAConfiguration.EmailTemplate endUserApprovedEmailInfo;
    @Mock
    private RPAConfiguration.EmailTemplate endUserDeclinedEmailInfo;
    @Mock
    private RPAConfiguration.EmailTemplate smeApprovalEmailInfo;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        emailInfoProvider = new EmailInfoProvider(rpaConfiguration);
        prepareMockRecord();
        prepareMockRPAConfiguration();
    }

    private void prepareMockRecord() {
        when(record.getId()).thenReturn("1");
        when(record.getCaseNum()).thenReturn("12345");
        when(record.getUserInfo()).thenReturn(userInfo);
        when(userInfo.getFullName()).thenReturn("John Doe");
        when(userInfo.getEmail()).thenReturn("test@test.com");
        when(userInfo.getSubject()).thenReturn("TEST_SUBJECT");
        when(userInfo.getProductTitle()).thenReturn("TEST_DATASET_TITLE");
    }

    private void prepareMockRPAConfiguration() {
        when(rpaConfiguration.endUserConfirmationEmail()).thenReturn(endUserConfirmationEmail);
        when(endUserConfirmationEmail.getSubject()).thenReturn("Confirmation Email - Subject");
        when(endUserConfirmationEmail.getContent()).thenReturn("Confirmation email for download of datatset: ${DATASET_NAME}");

        when(rpaConfiguration.endUserApprovedEmail()).thenReturn(endUserApprovedEmailInfo);
        when(endUserApprovedEmailInfo.getSubject()).thenReturn("Approval Email - Subject");
        when(endUserApprovedEmailInfo.getContent()).thenReturn("Approved for download of datatset: ${DATASET_NAME}. Download here ${DOWNLOAD_URL}");

        when(rpaConfiguration.endUserDeclinedEmail()).thenReturn(endUserDeclinedEmailInfo);
        when(endUserDeclinedEmailInfo.getSubject()).thenReturn("Declined Email - Subject");
        when(endUserDeclinedEmailInfo.getContent()).thenReturn("Declined for datatset: ${DATASET_NAME}");

        when(rpaConfiguration.SMEApprovalEmail()).thenReturn(smeApprovalEmailInfo);
        when(smeApprovalEmailInfo.getSubject()).thenReturn("SME Email - Case: ");
        when(smeApprovalEmailInfo.getContent()).thenReturn("Download of dataset ${DATASET_NAME} by ${FULL_NAME} requires your approval.");
    }

    @Test
    public void testGetSMEApprovalEmailInfo() {
        Map<String, RPAConfiguration.Approver> map = new HashMap<>();
        map.put(record.getUserInfo().getSubject(), new RPAConfiguration.Approver("John Doe", "john.doe@test.com"));
        when(rpaConfiguration.getApprovers()).thenReturn(map);
        EmailInfo emailInfo = emailInfoProvider.getSMEApprovalEmailInfo(record);

        String expectedSMEEmailAddress = "john.doe@test.com";
        String expectedSubject = "SME Email - Case: 12345";
        String expectedContent = "Download of dataset TEST_DATASET_TITLE by John Doe requires your approval.";

        assertEquals("1", emailInfo.getRecordId());
        assertEquals(expectedSMEEmailAddress, emailInfo.getRecipient());
        assertEquals(expectedSubject, emailInfo.getSubject());
        assertEquals(expectedContent, emailInfo.getContent());
    }

    @Test
    public void testGetEndUserConfirmationEmailInfo() {
        EmailInfo emailInfo = emailInfoProvider.getEndUserConfirmationEmailInfo(record);

        String expectedEndUserEmailAddress = "test@test.com";
        String expectedSubject = "Confirmation Email - Subject";
        String expectedContent = "Confirmation email for download of datatset: TEST_DATASET_TITLE";

        assertEquals("1", emailInfo.getRecordId());
        assertEquals(expectedEndUserEmailAddress, emailInfo.getRecipient());
        assertEquals(expectedSubject, emailInfo.getSubject());
        assertEquals(expectedContent, emailInfo.getContent());
    }

    @Test
    public void testGetEndUserApprovedEmailInfo() {
        String downloadUrl = "https://example.com/download/TEST_DATASET_TITLE";
        EmailInfo emailInfo = emailInfoProvider.getEndUserApprovedEmailInfo(record, downloadUrl);

        String expectedEndUserEmailAddress = "test@test.com";
        String expectedSubject = "Approval Email - Subject";
        String expectedContent = "Approved for download of datatset: TEST_DATASET_TITLE. Download here https://example.com/download/TEST_DATASET_TITLE";

        assertEquals("1", emailInfo.getRecordId());
        assertEquals(expectedEndUserEmailAddress, emailInfo.getRecipient());
        assertEquals(expectedSubject, emailInfo.getSubject());
        assertEquals(expectedContent, emailInfo.getContent());
    }

}

