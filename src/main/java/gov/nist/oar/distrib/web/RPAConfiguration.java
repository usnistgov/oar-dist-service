package gov.nist.oar.distrib.web;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.NonFinal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private Map<String, List<Approver.ApproverData>> approvers = null;
    @JsonProperty("salesforceInstanceUrl")
    private String salesforceInstanceUrl = null;
    @JsonProperty("baseDownloadUrl")
    private String baseDownloadUrl = null;
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
    @JsonProperty("authorized")
    List<String> authorized = null;
    @JsonProperty("jwtSecretKey")
    String jwtSecretKey = null;
    @JsonProperty("headbagCacheSize")
    long hbCacheSize = 50000000; // 50 MB
    @JsonProperty("bagstore-location")
    String bagStore = null;
    @JsonProperty("bagstore-mode")
    String mode = null;
    @JsonProperty("blacklists")
    private Map<String, BlacklistConfig> blacklists = new HashMap<>();

    @JsonProperty("expiresAfterMillis")
    long expiresAfterMillis = 0L;

    @JsonProperty("supportEmail")
    private String supportEmail;

    public long getHeadbagCacheSize() {
        return hbCacheSize;
    }

    public void setHeadbagCacheSize(long size) {
        hbCacheSize = size;
    }

    public String getBagstoreLocation() {
        return bagStore;
    }

    public void setBagstoreLocation(String loc) {
        bagStore = loc;
    }

    public String getBagstoreMode() {
        return mode;
    }

    public void setBagstoreMode(String mode) {
        this.mode = mode;
    }

    public long getExpiresAfterMillis() {
        return expiresAfterMillis;
    }

    public void setExpiresAfterMillis(long expiresAfterMillis) {
        this.expiresAfterMillis = expiresAfterMillis;
    }

    public String getSupportEmail() {
        return supportEmail;
    }

    public void setSupportEmail(String supportEmail) {
        this.supportEmail = supportEmail;
    }

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

    public Map<String, List<Approver.ApproverData>> getApprovers() {
        return approvers;
    }

    public void setApprovers(Map<String, List<Approver.ApproverData>> approvers) {
        this.approvers = approvers;
    }

    public String getSalesforceInstanceUrl() {
        return salesforceInstanceUrl;
    }

    public void setSalesforceInstanceUrl(String salesforceInstanceUrl) {
        this.salesforceInstanceUrl = salesforceInstanceUrl;
    }

    public String getBaseDownloadUrl() {
        return baseDownloadUrl;
    }

    public void setBaseDownloadUrl(String baseDownloadUrl) {
        this.baseDownloadUrl = baseDownloadUrl;
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

    public List<String> getAuthorized() {
        return authorized;
    }

    public void setAuthorized(List<String> authorized) {
        this.authorized = authorized;
    }

    public boolean isAuthorized(String token) {
        for (String str : authorized) {
            if (str.equals(token)) {
                return true;
            }
        }
        return false;
    }

    public String getJwtSecretKey() {
        return jwtSecretKey;
    }

    public void setJwtSecretKey(String jwtSecretKey) {
        this.jwtSecretKey = jwtSecretKey;
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

    public Map<String, BlacklistConfig> getBlacklists() {
        return blacklists;
    }
    
    public void setBlacklists(Map<String, BlacklistConfig> blacklists) {
        this.blacklists = blacklists;
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
    @AllArgsConstructor
    public static class Approver {
        private List<ApproverData> approvers;

        public List<ApproverData> getApprovers() {
            return approvers;
        }

        public void setApprovers(List<ApproverData> approvers) {
            this.approvers = approvers;
        }

        @NoArgsConstructor
        @AllArgsConstructor
        public static class ApproverData {
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

    public EmailTemplate preApprovedEmail() {
        return this.getEmailTemplates().get("pre-approved-user");
    }

    public EmailTemplate endUserFailureNotificationEmail() {
        return this.getEmailTemplates().get("failure-notification-user");
    }


    @Override
    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        String jsonStr;
        try {
            jsonStr = mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return jsonStr;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlacklistConfig {
        @JsonProperty("disallowed-emails")
        private List<String> disallowedEmails = new ArrayList<>();

        @JsonProperty("disallowed-countries")
        private List<String> disallowedCountries = new ArrayList<>();

        public List<String> getDisallowedEmails() {
            return disallowedEmails;
        }

        public void setDisallowedEmails(List<String> disallowedEmails) {
            this.disallowedEmails = disallowedEmails;
        }

        public List<String> getDisallowedCountries() {
            return disallowedCountries;
        }

        public void setDisallowedCountries(List<String> disallowedCountries) {
            this.disallowedCountries = disallowedCountries;
        }
    }

}