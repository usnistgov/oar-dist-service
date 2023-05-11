package gov.nist.oar.distrib.service.rpa.external.salesforce;

import com.fasterxml.jackson.annotation.JsonProperty;
import gov.nist.oar.distrib.service.rpa.client.CreateRecordPayload;
import gov.nist.oar.distrib.service.rpa.external.ExternalCreateRecordPayload;
import gov.nist.oar.distrib.service.rpa.utils.JsonSerializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SalesforceCreateRecordPayload extends JsonSerializable implements ExternalCreateRecordPayload {

    @JsonProperty("userInfo")
    private UserInfo userInfo = new UserInfo();

    public void setFullName(String fullName) {
        this.userInfo.setFullName(fullName);
    }

    public void setOrganization(String organization) {
        this.userInfo.setOrganization(organization);
    }

    public void setEmail(String email) {
        this.userInfo.setEmail(email);
    }

    public void setReceiveEmails(String receiveEmails) {
        this.userInfo.setReceiveEmails(receiveEmails);
    }

    public void setCountry(String country) {
        this.userInfo.setCountry(country);
    }

    public void setApprovalStatus(String approvalStatus) {
        this.userInfo.setApprovalStatus(approvalStatus);
    }

    public void setProductTitle(String productTitle) {
        this.userInfo.setProductTitle(productTitle);
    }

    public void setSubject(String subject) {
        this.userInfo.setSubject(subject);
    }

    public void setDescription(String description) {
        this.userInfo.setDescription(description);
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class UserInfo {

        @JsonProperty("fullName")
        private String fullName;

        @JsonProperty("organization")
        private String organization;

        @JsonProperty("email")
        private String email;

        @JsonProperty("receiveEmails")
        private String receiveEmails;

        @JsonProperty("country")
        private String country;

        @JsonProperty("approvalStatus")
        private String approvalStatus;

        @JsonProperty("productTitle")
        private String productTitle;

        @JsonProperty("subject")
        private String subject;

        @JsonProperty("description")
        private String description;
    }

}
