package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class UserInfo {
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getReceiveEmails() {
        return receiveEmails;
    }

    public void setReceiveEmails(String receiveEmails) {
        this.receiveEmails = receiveEmails;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public void setProductTitle(String productTitle) {
        this.productTitle = productTitle;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
