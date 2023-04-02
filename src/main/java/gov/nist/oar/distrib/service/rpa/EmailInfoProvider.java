package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.EmailInfo;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.apache.commons.text.StringSubstitutor;

import java.util.Map;

/**
 * A helper class to help construct {@EmailInfo} objects for different use cases.
 */
public class EmailInfoHelper {

    private static final String DATE_PATTERN = "EEEE, MM/dd/yyyy 'at' hh:mm a z";

    private final RPAConfiguration rpaConfiguration;

    /**
     * Constructs an instance of EmailHelper with the given RPA configuration, or a default configuration if none is provided.
     *
     * @param rpaConfiguration the RPA configuration (optional)
     */
    public EmailInfoHelper(RPAConfiguration rpaConfiguration) {
        if (rpaConfiguration != null) {
            this.rpaConfiguration = rpaConfiguration;
        } else {
            this.rpaConfiguration = new RPAConfiguration();
        }
    }

    /**
     * Generates an email to be sent to the SME for approving a request.
     *
     * @param record the record this email is related to
     * @return the email information (recipient, content, subject)
     */
    public EmailInfo getSMEApprovalEmailInfo(Record record) {
        // Implementation
    }

    /**
     * Generates an email to be sent to the end user to confirm receiving their request.
     *
     * @param record the record this email is related to
     * @return the email information (recipient, content, subject)
     */
    public EmailInfo getEndUserConfirmationEmailInfo(Record record) {
        // Implementation
    }

    /**
     * Generates an email to be sent to the end user notifying them that the request has been approved.
     *
     * @param record the record this email is related to
     * @param downloadUrl the URL where the end user will download their data
     * @return the email information (recipient, content, subject)
     */
    public EmailInfo getEndUserDownloadEmailInfo(Record record, String downloadUrl) {
        // Implementation
    }

    /**
     * Generates an email to be sent to the end user notifying them that the request has been declined.
     *
     * @param record the record this email is related to
     * @return the email information (recipient, content, subject)
     */
    public EmailInfo getEndUserDeclinedEmailInfo(Record record) {
        String recordId = record.getId();
        String datasetId = record.getUserInfo().getSubject();
        String smeEmailAddress = rpaConfiguration.getApprovers().get(datasetId).getEmail();
        String subject = rpaConfiguration.SMEApprovalEmail().getSubject() + record.getCaseNum();
        String content = StringSubstitutor.replace(
                rpaConfiguration.SMEApprovalEmail().getContent(),
                getNamedPlaceholders(record, null, null, rpaConfiguration.getSmeAppUrl()),
                "${", "}");
        return new EmailInfo(recordId, smeEmailAddress, subject, content);
    }

    /**
     * Helper method to load named placeholders used with email content templates.
     *
     * @param record the record this email is related to
     * @param downloadUrl the URL where the end user will download their data
     * @param expirationDate the expiration date of the data
     * @param smeAppUrl the URL of the SME approval application
     * @return a map of named placeholders and their values
     */
    private Map<String, String> getNamedPlaceholders(Record record, String downloadUrl, String expirationDate, String smeAppUrl) {
        // Implementation
    }

    /**
     * Returns a string representation of the date that is {@code days} days in the future.
     *
     * @param days the number of days to add to the current date
     * @return a string representing the date that is {@code days} days in the future
     */
    private String getDateInXDays(int days) {
        // Implementation
    }
}
