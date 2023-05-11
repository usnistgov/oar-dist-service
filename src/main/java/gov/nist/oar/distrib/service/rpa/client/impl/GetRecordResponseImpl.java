package gov.nist.oar.distrib.service.rpa.client.impl;

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

    @Override
    public void setRecordId(String recordId) {
        this.record.setId(recordId);
    }

    @Override
    public void setCaseNum(String caseNum) {
        this.record.setCaseNum(caseNum);
    }

    @Override
    public void setUserInfo_FullName(String fullName) {
        this.record.getUserInfo().setFullName(fullName);
    }

    @Override
    public void setUserInfo_Organization(String organization) {
        this.record.getUserInfo().setOrganization(organization);
    }

    @Override
    public void setUserInfo_Email(String email) {
        this.record.getUserInfo().setEmail(email);
    }

    @Override
    public void setUserInfo_ReceiveEmails(String receiveEmails) {
        this.record.getUserInfo().setReceiveEmails(receiveEmails);
    }

    @Override
    public void setUserInfo_Country(String country) {
        this.record.getUserInfo().setCountry(country);
    }

    @Override
    public void setUserInfo_ApprovalStatus(String approvalStatus) {
        this.record.getUserInfo().setApprovalStatus(approvalStatus);
    }

    @Override
    public void setUserInfo_ProductTitle(String productTitle) {
        this.record.getUserInfo().setProductTitle(productTitle);
    }

    @Override
    public void setUserInfo_Subject(String subject) {
        this.record.getUserInfo().setSubject(subject);
    }

    @Override
    public void setUserInfo_Description(String description) {
        this.record.getUserInfo().setDescription(description);
    }

    @Override
    public String getRecordId() {
        return record.getId();
    }

    @Override
    public String getCaseNum() {
        return record.getCaseNum();
    }

    @Override
    public String getUserInfo_FullName() {
        return record.getUserInfo().getFullName();
    }

    @Override
    public String getUserInfo_Organization() {
        return record.getUserInfo().getOrganization();
    }

    @Override
    public String getUserInfo_Email() {
        return record.getUserInfo().getEmail();
    }

    @Override
    public String getUserInfo_ReceiveEmails() {
        return record.getUserInfo().getReceiveEmails();
    }

    @Override
    public String getUserInfo_Country() {
        return record.getUserInfo().getCountry();
    }

    @Override
    public String getUserInfo_ApprovalStatus() {
        return record.getUserInfo().getApprovalStatus();
    }

    @Override
    public String getUserInfo_ProductTitle() {
        return record.getUserInfo().getProductTitle();
    }

    @Override
    public String getUserInfo_Subject() {
        return record.getUserInfo().getSubject();
    }

    @Override
    public String getUserInfo_Description() {
        return record.getUserInfo().getDescription();
    }

}
