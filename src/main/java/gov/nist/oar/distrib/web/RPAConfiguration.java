package gov.nist.oar.distrib.web;

import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Value
@NonFinal
@Validated
@ConfigurationProperties(prefix = "rpa")
public class RPAConfiguration {

    private SalesforceJwt salesforceJwt;
    private Map<String, EmailTemplate> emailTemplates;
    private Map<String, Approver> approvers;
    private String salesforceInstanceUrl;
    private String pdrCachingUrl;
    private Map<String, String> salesforceEndpoints;
    private JksConfig jksConfig;

    @Getter
    @Setter
    public static class SalesforceJwt {
        String clientId;
        String subject;
        String audience;
        Integer expirationInMinutes;
        String grantType;
    }

    @Getter
    @Setter
    public static class JksConfig {
        String keyStoreType;
        String keyStorePath;
        String keyStorePassword;
        String keyAlias;
        String keyPassword;
    }

    @Getter
    @Setter
    public static class EmailTemplate {
        private String subject;
        private String content;
    }

    @Getter
    @Setter
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
