package gov.nist.oar.distrib.service.rpa.external.salesforce;

import com.fasterxml.jackson.annotation.JsonProperty;
import gov.nist.oar.distrib.service.rpa.client.CreateRecordResponse;
import gov.nist.oar.distrib.service.rpa.client.impl.CreateRecordResponseImpl;
import gov.nist.oar.distrib.service.rpa.external.ExternalCreateRecordResponse;
import gov.nist.oar.distrib.service.rpa.utils.JsonSerializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SalesforceCreateRecordResponse extends JsonSerializable implements ExternalCreateRecordResponse {
    @JsonProperty("record")
    private Record record = new Record();

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
    public CreateRecordResponse toCreateRecordResponse() {
        CreateRecordResponse response = new CreateRecordResponseImpl();
        response.setRecordId(this.record.getId());
        response.setCaseNum(this.record.getCaseNum());
        response.setUserInfo_FullName(this.record.getUserInfo().getFullName());
        response.setUserInfo_Organization(this.record.getUserInfo().getOrganization());
        response.setUserInfo_Email(this.record.getUserInfo().getEmail());
        response.setUserInfo_ReceiveEmails(this.record.getUserInfo().getReceiveEmails());
        response.setUserInfo_Country(this.record.getUserInfo().getCountry());
        response.setUserInfo_ApprovalStatus(this.record.getUserInfo().getApprovalStatus());
        response.setUserInfo_ProductTitle(this.record.getUserInfo().getProductTitle());
        response.setUserInfo_Subject(this.record.getUserInfo().getSubject());
        response.setUserInfo_Description(this.record.getUserInfo().getDescription());
        return response;
    }

}
