package gov.nist.oar.distrib.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import lombok.experimental.NonFinal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.Map;

/**
 * Configuration model for Restricted Public Access
 */
@NonFinal
@Validated
@ConfigurationProperties("distrib.rpa")
public class RPAConfiguration {


    @JsonProperty("salesforceJwt")
    private SalesforceJwt salesforceJwt = null;
    @JsonProperty("emailTemplates")
    private Map<String, EmailTemplate> emailTemplates = null;
    @JsonProperty("approvers")
    private Map<String, Approver> approvers = null;
    @JsonProperty("salesforceInstanceUrl")
    private String salesforceInstanceUrl = null;
    @JsonProperty("pdrCachingUrl")
    private String pdrCachingUrl = null;
    @JsonProperty("datacartUrl")
    private String datacartUrl = null;
    @JsonProperty("smeAppUrl")
    private String smeAppUrl = null;
    @JsonProperty("salesforceEndpoints")
    private Map<String, String> salesforceEndpoints = null;
    @JsonProperty("jksConfig")
    private JksConfig jksConfig = null;
    @JsonProperty("recaptchaSecret")
    String recaptchaSecret;

    public SalesforceJwt getSalesforceJwt() {
        return salesforceJwt;
    }

    public void setSalesforceJwt(SalesforceJwt salesforceJwt) {
        this.salesforceJwt = salesforceJwt;
    }

    public Map<String, EmailTemplate> getEmailTemplates() {
        return emailTemplates;
    }

    public void setEmailTemplates(Map<String, EmailTemplate> emailTemplates) {
        this.emailTemplates = emailTemplates;
    }

    public Map<String, Approver> getApprovers() {
        return approvers;
    }

    public void setApprovers(Map<String, Approver> approvers) {
        this.approvers = approvers;
    }

    public String getSalesforceInstanceUrl() {
        return salesforceInstanceUrl;
    }

    public void setSalesforceInstanceUrl(String salesforceInstanceUrl) {
        this.salesforceInstanceUrl = salesforceInstanceUrl;
    }

    public String getPdrCachingUrl() {
        return pdrCachingUrl;
    }

    public void setPdrCachingUrl(String pdrCachingUrl) {
        this.pdrCachingUrl = pdrCachingUrl;
    }

    public String getDatacartUrl() {
        return datacartUrl;
    }

    public void setDatacartUrl(String datacartUrl) {
        this.datacartUrl = datacartUrl;
    }

    public String getSmeAppUrl() {
        return smeAppUrl;
    }

    public void setSmeAppUrl(String smeAppUrl) {
        this.smeAppUrl = smeAppUrl;
    }

    public Map<String, String> getSalesforceEndpoints() {
        return salesforceEndpoints;
    }

    public void setSalesforceEndpoints(Map<String, String> salesforceEndpoints) {
        this.salesforceEndpoints = salesforceEndpoints;
    }

    public JksConfig getJksConfig() {
        return jksConfig;
    }

    public void setJksConfig(JksConfig jksConfig) {
        this.jksConfig = jksConfig;
    }

    public String getRecaptchaSecret() {
        return recaptchaSecret;
    }

    public void setRecaptchaSecret(String recaptchaSecret) {
        this.recaptchaSecret = recaptchaSecret;
    }

    @NoArgsConstructor
    public static class SalesforceJwt {
        @JsonProperty("clientId")
        @JsonIgnore
        String clientId;
        @JsonProperty("subject")
        String subject;
        @JsonProperty("audience")
        String audience;
        @JsonProperty("expirationInMinutes")
        Integer expirationInMinutes;
        @JsonProperty("grantType")
        String grantType;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public Integer getExpirationInMinutes() {
            return expirationInMinutes;
        }

        public void setExpirationInMinutes(Integer expirationInMinutes) {
            this.expirationInMinutes = expirationInMinutes;
        }

        public String getGrantType() {
            return grantType;
        }

        public void setGrantType(String grantType) {
            this.grantType = grantType;
        }
    }

    @NoArgsConstructor
    public static class JksConfig {
        @JsonProperty("keyStoreType")
        String keyStoreType;
        @JsonProperty("keyStorePath")
        String keyStorePath;
        @JsonProperty("keyStorePassword")
        @JsonIgnore
        String keyStorePassword;
        @JsonProperty("keyAlias")
        String keyAlias;
        @JsonProperty("keyPassword")
        @JsonIgnore
        String keyPassword;


        public String getKeyStoreType() {
            return keyStoreType;
        }

        public void setKeyStoreType(String keyStoreType) {
            this.keyStoreType = keyStoreType;
        }

        public String getKeyStorePath() {
            return keyStorePath;
        }

        public void setKeyStorePath(String keyStorePath) {
            this.keyStorePath = keyStorePath;
        }

        public String getKeyStorePassword() {
            return keyStorePassword;
        }

        public void setKeyStorePassword(String keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
        }

        public String getKeyAlias() {
            return keyAlias;
        }

        public void setKeyAlias(String keyAlias) {
            this.keyAlias = keyAlias;
        }

        public String getKeyPassword() {
            return keyPassword;
        }

        public void setKeyPassword(String keyPassword) {
            this.keyPassword = keyPassword;
        }
    }

    @NoArgsConstructor
    public static class EmailTemplate {
        @JsonProperty("subject")
        private String subject;
        @JsonProperty("content")
        private String content;

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    @NoArgsConstructor
    public static class Approver {
        @JsonProperty("name")
        private String name;
        @JsonProperty("email")
        private String email;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public EmailTemplate endUserConfirmationEmail() {
        return this.getEmailTemplates().get("ack-user");
    }

    public EmailTemplate SMEApprovalEmail() {
        return this.getEmailTemplates().get("to-sme");
    }

    public EmailTemplate endUserApprovedEmail() {
        return this.getEmailTemplates().get("approved-user");
    }

    public EmailTemplate endUserDeclinedEmail() {
        return this.getEmailTemplates().get("declined-user");
    }

    @Override
    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        String jsonStr;
        try {
            jsonStr = mapper.writeValueAsString(this);
        } catch (
                JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return jsonStr;
    }
}