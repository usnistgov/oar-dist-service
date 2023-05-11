package gov.nist.oar.distrib.service.rpa.client.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import gov.nist.oar.distrib.service.rpa.client.CreateRecordPayload;
import gov.nist.oar.distrib.service.rpa.external.ExternalCreateRecordPayload;
import gov.nist.oar.distrib.service.rpa.external.ExternalCreateRecordResponse;
import gov.nist.oar.distrib.service.rpa.external.salesforce.SalesforceCreateRecordPayload;
import gov.nist.oar.distrib.service.rpa.utils.JsonSerializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CreateRecordPayloadImpl extends JsonSerializable implements CreateRecordPayload {

    @JsonProperty("userInfo")
    private UserInfo userInfo = new UserInfo();

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class UserInfo {

        @JsonProperty("fullName")
        @NotBlank(message = "Full name cannot be empty")
        private String fullName;

        @JsonProperty("organization")
        @NotBlank(message = "Organization cannot be empty")
        private String organization;

        @JsonProperty("email")
        @NotBlank(message = "Email cannot be empty")
        @Email(message = "Invalid email format")
        private String email;

        @JsonProperty("receiveEmails")
        @NotBlank(message = "Receive emails cannot be empty")
        private String receiveEmails;

        @JsonProperty("country")
        @NotBlank(message = "Country cannot be empty")
        private String country;

        @JsonProperty("approvalStatus")
        private String approvalStatus;

        @JsonProperty("productTitle")
        private String productTitle;

        @JsonProperty("subject")
        @NotBlank(message = "Subject cannot be empty")
        private String subject;

        @JsonProperty("description")
        private String description;

    }

    @Override
    public ExternalCreateRecordPayload toExternalPayload() {
        SalesforceCreateRecordPayload externalPayload = new SalesforceCreateRecordPayload();
        externalPayload.setFullName(this.userInfo.getFullName());
        externalPayload.setOrganization(userInfo.getOrganization());
        externalPayload.setEmail(userInfo.getEmail());
        externalPayload.setReceiveEmails(userInfo.getReceiveEmails());
        externalPayload.setCountry(userInfo.getCountry());
        externalPayload.setApprovalStatus(userInfo.getApprovalStatus());
        externalPayload.setProductTitle(userInfo.getProductTitle());
        externalPayload.setSubject(userInfo.getSubject());
        externalPayload.setDescription(userInfo.getDescription());
        return externalPayload;
    }

}

