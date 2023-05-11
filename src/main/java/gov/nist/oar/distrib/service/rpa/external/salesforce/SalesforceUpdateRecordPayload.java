package gov.nist.oar.distrib.service.rpa.external.salesforce;

import com.fasterxml.jackson.annotation.JsonProperty;
import gov.nist.oar.distrib.service.rpa.client.UpdateRecordPayload;
import gov.nist.oar.distrib.service.rpa.external.ExternalUpdateRecordPayload;

public class SalesforceUpdateRecordPayload implements ExternalUpdateRecordPayload {

    @JsonProperty("Approval_Status__c")
    private final String approvalStatus;

    public SalesforceUpdateRecordPayload(UpdateRecordPayload payload) {
        this.approvalStatus = payload.getApprovalStatus();
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }


}
