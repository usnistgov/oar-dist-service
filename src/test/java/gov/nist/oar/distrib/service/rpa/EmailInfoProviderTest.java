package gov.nist.oar.distrib.service.rpa;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import gov.nist.oar.distrib.service.rpa.model.EmailInfo;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.service.rpa.model.UserInfo;
import gov.nist.oar.distrib.web.RPAConfiguration;

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
    @Mock
    private RPAConfiguration.EmailTemplate metadataFailureEmailInfo;

    private AutoCloseable closeable;  // For closing open mocks

    @BeforeEach
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);  // Use openMocks instead of initMocks
        emailInfoProvider = new EmailInfoProvider(rpaConfiguration);
        prepareMockRecord();
        prepareMockRPAConfiguration();
    }

    @AfterEach
    public void tearDown() throws Exception {
        closeable.close();  // Ensure mocks are closed after each test
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

        when(rpaConfiguration.endUserFailureNotificationEmail()).thenReturn(metadataFailureEmailInfo);
        when(rpaConfiguration.getSupportEmail()).thenReturn("rpa-support@nist.gov");
        when(metadataFailureEmailInfo.getSubject()).thenReturn("Processing Error Notification");
        when(metadataFailureEmailInfo.getContent()).thenReturn("We could not process your request related to ${DATASET_NAME}. Please contact ${SUPPORT_EMAIL}.");
    }

    @Test
    public void testGetSMEApprovalEmailInfo() {
        // Create a list of approvers for the subject
        List<RPAConfiguration.Approver.ApproverData> approvers = new ArrayList<>();
        approvers.add(new RPAConfiguration.Approver.ApproverData("John Doe", "john.doe@test.com"));

        // Add the list of approvers to the map using the subject as the key
        Map<String, List<RPAConfiguration.Approver.ApproverData>> map = new HashMap<>();
        map.put(record.getUserInfo().getSubject(), approvers);

        // Stub the configuration to return the map
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
    public void testGetSMEApprovalEmailInfo_withMultipleApprovers() {
        // Create a list of approvers for the subject
        List<RPAConfiguration.Approver.ApproverData> approvers = new ArrayList<>();
        approvers.add(new RPAConfiguration.Approver.ApproverData("John Doe", "john.doe@test.com"));
        approvers.add(new RPAConfiguration.Approver.ApproverData("Jane Doe", "jane.doe@test.com"));

        // Add the list of approvers to the map using the subject as the key
        Map<String, List<RPAConfiguration.Approver.ApproverData>> map = new HashMap<>();
        map.put(record.getUserInfo().getSubject(), approvers);

        // Stub the configuration to return the map
        when(rpaConfiguration.getApprovers()).thenReturn(map);

        EmailInfo emailInfo = emailInfoProvider.getSMEApprovalEmailInfo(record);

        String expectedSMEEmailAddress = "john.doe@test.com;jane.doe@test.com";
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

    @Test
    public void testGetEndUserFailureNotificationEmailInfo() {
        // Act
        EmailInfo emailInfo = emailInfoProvider.getEndUserFailureNotificationEmailInfo(record);

        // Expected values based on mock setup
        String expectedRecipient = "test@test.com";
        String expectedSubject = "Processing Error Notification";
        String expectedContent = "We could not process your request related to TEST_DATASET_TITLE. Please contact rpa-support@nist.gov.";

        // Assert values match expectations
        assertEquals("1", emailInfo.getRecordId());
        assertEquals(expectedRecipient, emailInfo.getRecipient());
        assertEquals(expectedSubject, emailInfo.getSubject());
        assertEquals(expectedContent, emailInfo.getContent());
    }

}

