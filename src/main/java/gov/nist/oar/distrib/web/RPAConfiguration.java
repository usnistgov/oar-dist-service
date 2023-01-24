package gov.nist.oar.distrib.web;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.Map;

@Value
@NonFinal
@Validated
@ConfigurationProperties("distrib.rpa")
public class RPAConfiguration {


    private SalesforceJwt salesforceJwt = null;
    private Map<String, EmailTemplate> emailTemplates = null;
    private Map<String, Approver> approvers = null;
    private String salesforceInstanceUrl = null;
    private String pdrCachingUrl = null;
    private Map<String, String> salesforceEndpoints = null;
    private JksConfig jksConfig = null;

    public SalesforceJwt getSalesforceJwt() {
        return salesforceJwt;
    }

    public Map<String, EmailTemplate> getEmailTemplates() {
        return emailTemplates;
    }

    public Map<String, Approver> getApprovers() {
        return approvers;
    }

    public String getSalesforceInstanceUrl() {
        return salesforceInstanceUrl;
    }

    public String getPdrCachingUrl() {
        return pdrCachingUrl;
    }

    public Map<String, String> getSalesforceEndpoints() {
        return salesforceEndpoints;
    }

    public JksConfig getJksConfig() {
        return jksConfig;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SalesforceJwt {
        String clientId;
        String subject;
        String audience;
        Integer expirationInMinutes;
        String grantType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class JksConfig {
        String keyStoreType;
        String keyStorePath;
        String keyStorePassword;
        String keyAlias;
        String keyPassword;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class EmailTemplate {
        private String subject;
        private String content;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Approver {
        private String name;
        private String email;
    }

    public EmailTemplate getEndUserConfirmationEmail() {
        return this.getEmailTemplates().get("ack-user");
    }

    public EmailTemplate getSMEApprovalEmail() {
        return this.getEmailTemplates().get("to-sme");
    }

    public EmailTemplate getEndUserApprovedEmail() {
        return this.getEmailTemplates().get("approved-user");
    }

    public EmailTemplate getEndUserDeclinedEmail() {
        return this.getEmailTemplates().get("declined-user");
    }
}