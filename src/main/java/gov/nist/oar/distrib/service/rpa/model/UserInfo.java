package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;


/** Represents the user information.
 * This contains the information the user submitted through the request form.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class UserInfo {

    /** Represents the full name of the user.
     */
    @JsonProperty("fullName")
    private String fullName;

    /** Represents the organization the user belongs to.
     */
    @JsonProperty("organization")
    private String organization;

    /** Represents the email of the user.
     */
    @JsonProperty("email")
    private String email;

    /** Represents a boolean flag whether the user wants to subscribe to future emails or not.
     */
    @JsonProperty("receiveEmails")
    private String receiveEmails;

    /** Represents the country of origin of the user.
     */
    @JsonProperty("country")
    private String country;

    /** Represents the approval status of the record.
     * This is not required to be set by the user during form submission.
     * This is mainly used when received information about a record from the records service.
     */
    @JsonProperty("approvalStatus")
    private String approvalStatus;

    /** Represents the title of the product the user is trying to download.
     */
    @JsonProperty("productTitle")
    private String productTitle;

    /** Represents the ID of the product the user is trying to download.
     */
    @JsonProperty("subject")
    private String subject;

    /** Represents a description of the product.
     */
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
