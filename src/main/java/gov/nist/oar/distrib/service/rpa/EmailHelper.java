package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.EmailInfo;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.apache.commons.text.StringSubstitutor;

import java.util.HashMap;
import java.util.Map;

public class EmailHelper {

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
