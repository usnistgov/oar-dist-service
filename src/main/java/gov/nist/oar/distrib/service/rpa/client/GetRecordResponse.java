package gov.nist.oar.distrib.service.rpa.client;

import gov.nist.oar.distrib.service.rpa.external.salesforce.SalesforceGetRecordResponse;

public interface GetRecordResponse {
    void setRecordId(String recordId);
    void setCaseNum(String caseNum);
    void setUserInfo_FullName(String fullName);
    void setUserInfo_Organization(String organization);
    void setUserInfo_Email(String email);
    void setUserInfo_ReceiveEmails(String receiveEmails);
    void setUserInfo_Country(String country);
    void setUserInfo_ApprovalStatus(String status);
    void setUserInfo_ProductTitle(String productTitle);
    void setUserInfo_Subject(String subject);
    void setUserInfo_Description(String description);

    String getRecordId();

    String getCaseNum();

    String getUserInfo_FullName();

    String getUserInfo_Organization();

    String getUserInfo_Email();

    String getUserInfo_ReceiveEmails();

    String getUserInfo_Country();

    String getUserInfo_ApprovalStatus();

    String getUserInfo_ProductTitle();

    String getUserInfo_Subject();

    String getUserInfo_Description();

}
