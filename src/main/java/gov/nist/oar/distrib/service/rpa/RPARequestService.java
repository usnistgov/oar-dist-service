package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.client.CreateRecordPayload;
import gov.nist.oar.distrib.service.rpa.client.UpdateRecordPayload;
import gov.nist.oar.distrib.service.rpa.external.ExternalApiClient;
import gov.nist.oar.distrib.service.rpa.external.ExternalApiException;
import gov.nist.oar.distrib.service.rpa.external.ExternalCreateRecordPayload;
import gov.nist.oar.distrib.service.rpa.external.ExternalCreateRecordResponse;
import gov.nist.oar.distrib.service.rpa.external.ExternalGetRecordResponse;
import gov.nist.oar.distrib.service.rpa.external.ExternalUpdateRecordResponse;
import gov.nist.oar.distrib.service.rpa.external.salesforce.SalesforceApiClient;
import gov.nist.oar.distrib.service.rpa.external.salesforce.SalesforceCreateRecordPayload;
import gov.nist.oar.distrib.service.rpa.external.salesforce.SalesforceUpdateRecordPayload;
import gov.nist.oar.distrib.web.RPAConfiguration;

public class RPARequestService {
    private final ExternalApiClient externalApiClient;

    public RPARequestService(RPAConfiguration rpaConfiguration) {
        this.externalApiClient = new SalesforceApiClient(rpaConfiguration);
    }

    public ExternalGetRecordResponse getRecord(String id) throws ExternalApiException {
        return externalApiClient.getRecordById(id);
    }

    public ExternalCreateRecordResponse createRecord(CreateRecordPayload requestPayload) throws ExternalApiException {
        // TODO: sanitize user input
        // TODO: verify recaptcha
        // Map the CreateRecordRequest to ExternalCreateRecordPayload
        ExternalCreateRecordPayload payload = requestPayload.toExternalPayload();
        return externalApiClient.createRecord(payload);
    }

    public ExternalUpdateRecordResponse updateRecord(String id, UpdateRecordPayload payload) throws ExternalApiException {
        SalesforceUpdateRecordPayload requestPayload = new SalesforceUpdateRecordPayload(payload);
        return externalApiClient.updateRecordById(id, requestPayload);
    }

}
