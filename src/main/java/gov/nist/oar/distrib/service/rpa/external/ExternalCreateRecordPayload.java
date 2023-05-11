package gov.nist.oar.distrib.service.rpa.external;

import gov.nist.oar.distrib.service.rpa.external.salesforce.SalesforceCreateRecordPayload;

public interface ExternalCreateRecordPayload {
    SalesforceCreateRecordPayload.UserInfo getUserInfo();
}
