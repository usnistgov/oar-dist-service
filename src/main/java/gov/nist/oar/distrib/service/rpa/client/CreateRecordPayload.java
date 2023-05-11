package gov.nist.oar.distrib.service.rpa.client;

import gov.nist.oar.distrib.service.rpa.external.salesforce.SalesforceCreateRecordPayload;

public interface CreateRecordPayload {
    SalesforceCreateRecordPayload.UserInfo getUserInfo();
}
