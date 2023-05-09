package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.EmailInfo;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.apache.commons.text.StringSubstitutor;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A helper class to help construct {@EmailInfo} objects for different use cases.
 */
public class EmailInfoProvider {

    private static final String DATE_PATTERN = "EEEE, MM/dd/yyyy 'at' hh:mm a z";
    private static final int EXPIRATION_DAYS = 14;
    private final RPAConfiguration rpaConfiguration;

    /**
     * Constructs an instance of {@link EmailInfoProvider} with the given RPA configuration, or a default configuration if none is provided.
     *
     * @param rpaConfiguration the RPA configuration
     */
    public EmailInfoProvider(RPAConfiguration rpaConfiguration) {
        if (rpaConfiguration != null) {
            this.rpaConfiguration = rpaConfiguration;
        } else {
            throw new NullPointerException("RPAConfiguration cannot be null");
        }
    }

    /**
     * Generates an email to be sent to the Subject-Matter Expert (SME) for approving a request.
     *
     * @param record the record this email is related to
     * @return the email information (recipient, content, subject)
     */
    public EmailInfo getSMEApprovalEmailInfo(Record record) {
        String recordId = record.getId();
        String datasetId = record.getUserInfo().getSubject();
        List<RPAConfiguration.Approver.ApproverData> approvers = rpaConfiguration.getApprovers().get(datasetId);
        // For multiple approvers, we check if there are more than one approver
        // then join their email addresses using ';'
        String smeEmailAddresses = approvers.stream().map(RPAConfiguration.Approver.ApproverData::getEmail).collect(Collectors.joining(";"));
        String subject = rpaConfiguration.SMEApprovalEmail().getSubject() + record.getCaseNum();
        String content = createEmailContent(record);

        return new EmailInfo(recordId, smeEmailAddresses, subject, content);
    }

    private String createEmailContent(Record record) {
        Map<String, String> namedPlaceholders = getNamedPlaceholders(
                record, null, null, rpaConfiguration.getSmeAppUrl());
        return StringSubstitutor.replace(
                rpaConfiguration.SMEApprovalEmail().getContent(),
                namedPlaceholders,
                "${", "}");
    }

    /**
     * Generates an email to be sent to the end user to confirm receiving their request.
     *
     * @param record the record this email is related to
     * @return the email information (recipient, content, subject)
     */
    public EmailInfo getEndUserConfirmationEmailInfo(Record record) {
        String recordId = record.getId();
        String endUserEmailAddress = record.getUserInfo().getEmail();
        String subject = rpaConfiguration.endUserConfirmationEmail().getSubject();
        String content = createEndUserEmailContent(record);

        return new EmailInfo(recordId, endUserEmailAddress, subject, content);
    }

    private String createEndUserEmailContent(Record record) {
        Map<String, String> namedPlaceholders = getNamedPlaceholders(
                record, null, null, null);
        return StringSubstitutor.replace(
                rpaConfiguration.endUserConfirmationEmail().getContent(),
                namedPlaceholders,
                "${", "}");
    }

    /**
     * Generates an email to be sent to the end user notifying them that the request has been approved.
     *
     * @param record      the record this email is related to
     * @param downloadUrl the URL where the end user will download their data
     * @return the email information (recipient, content, subject)
     */
    public EmailInfo getEndUserApprovedEmailInfo(Record record, String downloadUrl) {
        String recordId = record.getId();
        String endUserEmailAddress = record.getUserInfo().getEmail();
        String subject = rpaConfiguration.endUserApprovedEmail().getSubject();
        String content = createEndUserApprovedEmailContent(record, downloadUrl);

        return new EmailInfo(recordId, endUserEmailAddress, subject, content);
    }

    private String createEndUserApprovedEmailContent(Record record, String downloadUrl) {
        String expirationDate = getExpirationDate();
        Map<String, String> namedPlaceholders = getNamedPlaceholders(record, downloadUrl, expirationDate, null);
        return StringSubstitutor.replace(
                rpaConfiguration.endUserApprovedEmail().getContent(),
                namedPlaceholders,
                "${", "}");
    }

    /**
     * Generates an email to be sent to the end user notifying them that the request has been declined.
     *
     * @param record the record this email is related to
     * @return the email information (recipient, content, subject)
     */
    public EmailInfo getEndUserDeclinedEmailInfo(Record record) {
        String recordId = record.getId();
        String endUserEmailAddress = record.getUserInfo().getEmail();
        String subject = this.rpaConfiguration.endUserDeclinedEmail().getSubject();
        String content = createEndUserDeclinedEmailContent(record);

        return new EmailInfo(recordId, endUserEmailAddress, subject, content);
    }

    private String createEndUserDeclinedEmailContent(Record record) {
        Map<String, String> namedPlaceholders = getNamedPlaceholders(
                record, null, null, null);
        return StringSubstitutor.replace(
                rpaConfiguration.endUserDeclinedEmail().getContent(),
                namedPlaceholders,
                "${", "}");
    }

    /**
     * Helper method to load named placeholders used with email content templates.
     *
     * This uses  {@link Stream#of(Object)} to create a stream of {@link AbstractMap.SimpleEntry}.  It filters out any entries with null value,
     * and collects the remaining entries into a map using the {@link Collectors#toMap(Function, Function)} method.
     *
     * @param record         the record this email is related to
     * @param downloadUrl    the URL where the end user will download their data, this will point to the rpa datacart.
     * @param expirationDate the expiration date of the data
     * @param smeAppUrl      the URL of the SME approval application
     *
     * @return a map of named placeholders and their values
     */
    private Map<String, String> getNamedPlaceholders(Record record, String downloadUrl, String expirationDate, String smeAppUrl) {
        return Stream.of(
                new AbstractMap.SimpleEntry<>("RECORD_ID", record.getId()),
                new AbstractMap.SimpleEntry<>("CASE_NUMBER", record.getCaseNum()),
                new AbstractMap.SimpleEntry<>("FULL_NAME", record.getUserInfo().getFullName()),
                new AbstractMap.SimpleEntry<>("ORGANIZATION", record.getUserInfo().getOrganization()),
                new AbstractMap.SimpleEntry<>("COUNTRY", record.getUserInfo().getCountry()),
                new AbstractMap.SimpleEntry<>("EMAIL", record.getUserInfo().getEmail()),
                new AbstractMap.SimpleEntry<>("APPROVAL_STATUS", record.getUserInfo().getApprovalStatus()),
                new AbstractMap.SimpleEntry<>("DATASET_ID", record.getUserInfo().getSubject()),
                new AbstractMap.SimpleEntry<>("DATASET_NAME", record.getUserInfo().getProductTitle()),
                new AbstractMap.SimpleEntry<>("DOWNLOAD_URL", downloadUrl),
                new AbstractMap.SimpleEntry<>("EXPIRATION_DATE", expirationDate),
                new AbstractMap.SimpleEntry<>("SME_APP_URL", smeAppUrl)
        )
        .filter(e -> e.getValue() != null)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Returns a string representation of the expiration date.
     *
     * @return a string representing the date that is {@code days} days in the future
     */
    private String getExpirationDate() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.ENGLISH);
        ZonedDateTime date = ZonedDateTime.now().plusDays(EXPIRATION_DAYS);
        return formatter.format(date);
    }
}
