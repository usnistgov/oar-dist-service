package gov.nist.oar.distrib.web;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

@Value
@NonFinal
@Validated
@Component
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "distrib.rpa")
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