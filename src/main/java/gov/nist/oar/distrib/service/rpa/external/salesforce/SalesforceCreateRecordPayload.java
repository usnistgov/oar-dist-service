package gov.nist.oar.distrib.service.rpa.external.salesforce;

import com.fasterxml.jackson.annotation.JsonProperty;
import gov.nist.oar.distrib.service.rpa.client.CreateRecordPayload;
import gov.nist.oar.distrib.service.rpa.external.ExternalCreateRecordPayload;
import lombok.NoArgsConstructor;

public class SalesforceCreateRecordPayload implements ExternalCreateRecordPayload {

    @JsonProperty("userInfo")
    private final UserInfo userInfo;

    public SalesforceCreateRecordPayload(CreateRecordPayload payload) {
        this.userInfo = payload.getUserInfo();
    }

    public SalesforceCreateRecordPayload(UserInfo userInfo) {
        this.userInfo = userInfo;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public static class UserInfo {

        @JsonProperty("fullName")
        private final String fullName;

        @JsonProperty("organization")
        private final String organization;

        @JsonProperty("email")
        private final String email;

        @JsonProperty("receiveEmails")
        private final String receiveEmails;

        @JsonProperty("country")
        private final String country;

        @JsonProperty("approvalStatus")
        private final String approvalStatus;

        @JsonProperty("productTitle")
        private final String productTitle;

        @JsonProperty("subject")
        private final String subject;

        @JsonProperty("description")
        private final String description;

        public UserInfo(String fullName, String organization, String email, String receiveEmails,
                        String country, String approvalStatus, String productTitle,
                        String subject, String description) {
            this.fullName = fullName;
            this.organization = organization;
            this.email = email;
            this.receiveEmails = receiveEmails;
            this.country = country;
            this.approvalStatus = approvalStatus;
            this.productTitle = productTitle;
            this.subject = subject;
            this.description = description;
        }

        public String getFullName() {
            return fullName;
        }

        public String getOrganization() {
            return organization;
        }

        public String getEmail() {
            return email;
        }

        public String getReceiveEmails() {
            return receiveEmails;
        }

        public String getCountry() {
            return country;
        }

        public String getApprovalStatus() {
            return approvalStatus;
        }

        public String getProductTitle() {
            return productTitle;
        }

        public String getSubject() {
            return subject;
        }

        public String getDescription() {
            return description;
        }
    }


}
