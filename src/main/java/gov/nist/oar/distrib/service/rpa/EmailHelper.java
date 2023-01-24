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
        String subject = rpaConfiguration.getSMEApprovalEmail().getSubject() + record.getCaseNum();
        String content = StringSubstitutor.replace(
                rpaConfiguration.getSMEApprovalEmail().getContent(),
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
        String subject = rpaConfiguration.getEndUserConfirmationEmail().getSubject();
        String content = StringSubstitutor.replace(
                rpaConfiguration.getEndUserConfirmationEmail().getContent(),
                getNamedPlaceholders(record, null),
                "${", "}");
        return new EmailInfo(recordId, endUserEmailAddress, subject, content);
    }

    /**
     * Get email to be sent to the End User notifying them that the request has been approved.
     * @param record - the record this email is related to
     * @param rpaConfiguration - the RPA configuration
     * @param datacartUrl - the URL where the End User will download their data
     *
     * @return EmailInfo - email information (recipient, content, subject)
     */
    public static EmailInfo getEndUserDownloadEmailInfo(Record record, RPAConfiguration rpaConfiguration, String datacartUrl) {
        String recordId = record.getId();
        String endUserEmailAddress = record.getUserInfo().getEmail();
        String subject = rpaConfiguration.getEndUserApprovedEmail().getSubject();
        String content = StringSubstitutor.replace(
                rpaConfiguration.getEndUserApprovedEmail().getContent(),
                getNamedPlaceholders(record, datacartUrl),
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
        String subject = rpaConfiguration.getEndUserDeclinedEmail().getSubject();
        String content = StringSubstitutor.replace(
                rpaConfiguration.getEndUserApprovedEmail().getContent(),
                getNamedPlaceholders(record, null),
                "${", "}");
        return new EmailInfo(recordId, endUserEmailAddress, subject, content);
    }

    /**
     * Helper method to load named placeholders used with email content templates.
     */
    private static Map<String ,Object> getNamedPlaceholders(Record record, String datacartUrl) {
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
        if (datacartUrl != null)
            map.put("DATA_CART_URL", datacartUrl);
        return map;
    }
}