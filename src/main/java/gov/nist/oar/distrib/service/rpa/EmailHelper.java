package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.EmailInfo;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.apache.commons.text.StringSubstitutor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        String datasetId = record.getUserInfo().getSubject();
        List<RPAConfiguration.Approver.ApproverData> approvers = rpaConfiguration.getApprovers().get(datasetId);
        // For multiple approvers, we check if there are more than one approver
        // then join their email addresses using ';'
        String smeEmailAddresses = approvers.stream().map(RPAConfiguration.Approver.ApproverData::getEmail).collect(Collectors.joining(";"));
        String subject = rpaConfiguration.SMEApprovalEmail().getSubject() + record.getCaseNum();
        String content = StringSubstitutor.replace(
                rpaConfiguration.SMEApprovalEmail().getContent(),
                getNamedPlaceholders(record, null, null, rpaConfiguration.getSmeAppUrl()),
                "${", "}");
        return new EmailInfo(recordId, smeEmailAddresses, subject, content);
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
                getNamedPlaceholders(record, null, null, null),
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
                getNamedPlaceholders(record, downloadUrl, getDateInXDays(14), null),
                "${", "}");
        return new EmailInfo(recordId, endUserEmailAddress, subject, content);
    }

    public static String getDateInXDays(int days) {
        String datePattern = "EEEE, MM/dd/yyyy 'at' hh:mm aa z";
        DateFormat dateFormat = new SimpleDateFormat(datePattern);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DATE, days);
        return dateFormat.format(calendar.getTime());
    }

    /**
     * Get email to be sent to the End User notifying them that the request has been declined.
     *
     * @param record - the record this email is related to
     * @param rpaConfiguration - the RPA configuration
     *Z
     * @return EmailInfo - email information (recipient, content, subject)
     */
    public static EmailInfo getEndUserDeclinedEmailInfo(Record record, RPAConfiguration rpaConfiguration) {
        String recordId = record.getId();
        String endUserEmailAddress = record.getUserInfo().getEmail();
        String subject = rpaConfiguration.endUserDeclinedEmail().getSubject();
        String content = StringSubstitutor.replace(
                rpaConfiguration.endUserApprovedEmail().getContent(),
                getNamedPlaceholders(record, null, null, null),
                "${", "}");
        return new EmailInfo(recordId, endUserEmailAddress, subject, content);
    }

    /**
     * Helper method to load named placeholders used with email content templates.
     */
    private static Map<String ,Object> getNamedPlaceholders(Record record, String downloadUrl, String expirationDate, String smeAppUrl) {
        Map<String, Object> map = new HashMap<>();
        map.put("RECORD_ID", record.getId());
        map.put("CASE_NUMBER", record.getCaseNum());
        map.put("FULL_NAME", record.getUserInfo().getFullName());
        map.put("ORGANIZATION", record.getUserInfo().getOrganization());
        map.put("COUNTRY", record.getUserInfo().getCountry());
        map.put("EMAIL", record.getUserInfo().getEmail());
        map.put("APPROVAL_STATUS", record.getUserInfo().getApprovalStatus());
        map.put("DATASET_ID", record.getUserInfo().getSubject());
        map.put("DATASET_NAME", record.getUserInfo().getProductTitle());
        if (downloadUrl != null)
            map.put("DOWNLOAD_URL", downloadUrl);
        if (expirationDate != null)
            map.put("EXPIRATION_DATE", expirationDate);
        if (smeAppUrl != null)
            map.put("SME_APP_URL", smeAppUrl);
        return map;
    }
}