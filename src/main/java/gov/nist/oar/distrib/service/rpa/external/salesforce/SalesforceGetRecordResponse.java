package gov.nist.oar.distrib.service.rpa.external.salesforce;

import com.fasterxml.jackson.annotation.JsonProperty;
import gov.nist.oar.distrib.service.rpa.client.GetRecordResponse;
import gov.nist.oar.distrib.service.rpa.client.impl.GetRecordResponseImpl;
import gov.nist.oar.distrib.service.rpa.external.ExternalGetRecordResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


public class SalesforceGetRecordResponse implements ExternalGetRecordResponse {

    @JsonProperty("record")
    private Record record;


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
        private UserInfo userInfo;

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
    public GetRecordResponse toGetRecordResponse() {
        GetRecordResponseImpl response = new GetRecordResponseImpl();
        response.setRecordId(record.getId());
        response.setCaseNum(record.getCaseNum());
        response.setUserInfo_FullName(record.getUserInfo().getFullName());
        response.setUserInfo_Organization(record.getUserInfo().getOrganization());
        response.setUserInfo_Email(record.getUserInfo().getEmail());
        response.setUserInfo_ReceiveEmails(record.getUserInfo().getReceiveEmails());
        response.setUserInfo_Country(record.getUserInfo().getCountry());
        response.setUserInfo_ApprovalStatus(record.getUserInfo().getApprovalStatus());
        response.setUserInfo_ProductTitle(record.getUserInfo().getProductTitle());
        response.setUserInfo_Subject(record.getUserInfo().getSubject());
        response.setUserInfo_Description(record.getUserInfo().getDescription());
        return response;
    }

}

