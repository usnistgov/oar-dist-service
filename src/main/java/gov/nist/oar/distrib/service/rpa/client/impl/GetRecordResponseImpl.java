package gov.nist.oar.distrib.service.rpa.client.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gov.nist.oar.distrib.service.rpa.client.GetRecordResponse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class GetRecordResponseImpl implements GetRecordResponse {

    @JsonProperty("record")
    private Record record = new Record();;

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    public static class Record {

        @JsonProperty("id")
        private String id;

        @JsonProperty("caseNum")
        private String caseNum;

        @JsonProperty("userInfo")
        private UserInfo userInfo = new UserInfo();

    }

    @AllArgsConstructor
    @NoArgsConstructor
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

    @JsonIgnore
    @Override
    public void setRecordId(String recordId) {
        this.record.setId(recordId);
    }

    @JsonIgnore
    @Override
    public void setCaseNum(String caseNum) {
        this.record.setCaseNum(caseNum);
    }

    @JsonIgnore
    @Override
    public void setUserInfo_FullName(String fullName) {
        this.record.getUserInfo().setFullName(fullName);
    }

    @JsonIgnore
    @Override
    public void setUserInfo_Organization(String organization) {
        this.record.getUserInfo().setOrganization(organization);
    }

    @JsonIgnore
    @Override
    public void setUserInfo_Email(String email) {
        this.record.getUserInfo().setEmail(email);
    }

    @JsonIgnore
    @Override
    public void setUserInfo_ReceiveEmails(String receiveEmails) {
        this.record.getUserInfo().setReceiveEmails(receiveEmails);
    }

    @JsonIgnore
    @Override
    public void setUserInfo_Country(String country) {
        this.record.getUserInfo().setCountry(country);
    }

    @JsonIgnore
    @Override
    public void setUserInfo_ApprovalStatus(String approvalStatus) {
        this.record.getUserInfo().setApprovalStatus(approvalStatus);
    }

    @JsonIgnore
    @Override
    public void setUserInfo_ProductTitle(String productTitle) {
        this.record.getUserInfo().setProductTitle(productTitle);
    }

    @JsonIgnore
    @Override
    public void setUserInfo_Subject(String subject) {
        this.record.getUserInfo().setSubject(subject);
    }

    @JsonIgnore
    @Override
    public void setUserInfo_Description(String description) {
        this.record.getUserInfo().setDescription(description);
    }

    @JsonIgnore
    @Override
    public String getRecordId() {
        return record.getId();
    }

    @JsonIgnore
    @Override
    public String getCaseNum() {
        return record.getCaseNum();
    }

    @JsonIgnore
    @Override
    public String getUserInfo_FullName() {
        return record.getUserInfo().getFullName();
    }

    @JsonIgnore
    @Override
    public String getUserInfo_Organization() {
        return record.getUserInfo().getOrganization();
    }

    @JsonIgnore
    @Override
    public String getUserInfo_Email() {
        return record.getUserInfo().getEmail();
    }

    @JsonIgnore
    @Override
    public String getUserInfo_ReceiveEmails() {
        return record.getUserInfo().getReceiveEmails();
    }

    @JsonIgnore
    @Override
    public String getUserInfo_Country() {
        return record.getUserInfo().getCountry();
    }

    @JsonIgnore
    @Override
    public String getUserInfo_ApprovalStatus() {
        return record.getUserInfo().getApprovalStatus();
    }

    @JsonIgnore
    @Override
    public String getUserInfo_ProductTitle() {
        return record.getUserInfo().getProductTitle();
    }

    @JsonIgnore
    @Override
    public String getUserInfo_Subject() {
        return record.getUserInfo().getSubject();
    }

    @JsonIgnore
    @Override
    public String getUserInfo_Description() {
        return record.getUserInfo().getDescription();
    }

}
