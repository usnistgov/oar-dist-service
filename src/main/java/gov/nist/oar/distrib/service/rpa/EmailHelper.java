package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.EmailInfo;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.apache.commons.text.StringSubstitutor;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for sending emails.
 */
public class EmailHelper {

    /**
     * Get email to be sent to the SME for approving a request.
     * @param record - the record this email is related to
     * @param rpaConfiguration - the RPA configuration
     *
     * @return EmailInfo - email information (recipient, content, subject)
     */
    public static EmailInfo getSMEApprovalEmailInfo(Record record, RPAConfiguration rpaConfiguration) {
        String recordId = record.getId();
        String datasetId = record.getUserInfo().getSubject().replace("RPA: ", "");
        String smeEmailAddress = rpaConfiguration.getApprovers().get(datasetId).getEmail();
        String subject = rpaConfiguration.SMEApprovalEmail().getSubject() + record.getCaseNum();
        String content = StringSubstitutor.replace(
                rpaConfiguration.SMEApprovalEmail().getContent(),
                getNamedPlaceholders(record, null),
                "${", "}");
        return new EmailInfo(recordId, smeEmailAddress, subject, content);
    }

    /**
     * Get email to be sent to the End User to confirm receiving their request.
     * @param record - the record this email is related to
     * @param rpaConfiguration - the RPA configuration
     *
     * @return EmailInfo - email information (recipient, content, subject)
     */
    public static EmailInfo getEndUserConfirmationEmailInfo(Record record, RPAConfiguration rpaConfiguration) {
        String recordId = record.getId();
        String endUserEmailAddress = record.getUserInfo().getEmail();
        String subject = rpaConfiguration.endUserConfirmationEmail().getSubject();
        String content = StringSubstitutor.replace(
                rpaConfiguration.endUserConfirmationEmail().getContent(),
                getNamedPlaceholders(record, null),
                "${", "}");
        return new EmailInfo(recordId, endUserEmailAddress, subject, content);
    }

    /**
     * Get email to be sent to the End User notifying them that the request has been approved.
     * @param record - the record this email is related to
     * @param rpaConfiguration - the RPA configuration
     * @param downloadUrl - the URL where the End User will download their data
     *
     * @return EmailInfo - email information (recipient, content, subject)
     */
    public static EmailInfo getEndUserDownloadEmailInfo(Record record, RPAConfiguration rpaConfiguration, String downloadUrl) {
        String recordId = record.getId();
        String endUserEmailAddress = record.getUserInfo().getEmail();
        String subject = rpaConfiguration.endUserApprovedEmail().getSubject();
        String content = StringSubstitutor.replace(
                rpaConfiguration.endUserApprovedEmail().getContent(),
                getNamedPlaceholders(record, downloadUrl),
                "${", "}");
        return new EmailInfo(recordId, endUserEmailAddress, subject, content);
    }

    /**
     * Get email to be sent to the End User notifying them that the request has been declined.
     * @param record - the record this email is related to
     * @param rpaConfiguration - the RPA configuration
     *
     * @return EmailInfo - email information (recipient, content, subject)
     */
    public static EmailInfo getEndUserDeclinedEmailInfo(Record record, RPAConfiguration rpaConfiguration) {
        String recordId = record.getId();
        String endUserEmailAddress = record.getUserInfo().getEmail();
        String subject = rpaConfiguration.endUserDeclinedEmail().getSubject();
        String content = StringSubstitutor.replace(
                rpaConfiguration.endUserApprovedEmail().getContent(),
                getNamedPlaceholders(record, null),
                "${", "}");
        return new EmailInfo(recordId, endUserEmailAddress, subject, content);
    }

    /**
     * Helper method to load named placeholders used with email content templates.
     */
    private static Map<String ,Object> getNamedPlaceholders(Record record, String downloadUrl) {
        Map<String, Object> map = new HashMap<>();
        map.put("RECORD_ID", record.getId());
        map.put("CASE_NUMBER", record.getCaseNum());
        map.put("FULL_NAME", record.getUserInfo().getFullName());
        map.put("ORGANIZATION", record.getUserInfo().getOrganization());
        map.put("COUNTRY", record.getUserInfo().getCountry());
        map.put("EMAIL", record.getUserInfo().getEmail());
        map.put("APPROVAL_STATUS", record.getUserInfo().getApprovalStatus());
        map.put("DATASET_ID", record.getUserInfo().getSubject().replace("RPA: ", ""));
        map.put("DATASET_NAME", record.getUserInfo().getProductTitle());
        if (downloadUrl != null)
            map.put("DOWNLOAD_URL", downloadUrl);
        return map;
    }
}