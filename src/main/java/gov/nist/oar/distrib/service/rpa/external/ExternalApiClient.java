package gov.nist.oar.distrib.service.rpa.external;

import gov.nist.oar.distrib.web.RPAConfiguration;

import java.io.IOException;

public interface ExternalApiClient {
    ExternalGetRecordResponse getRecordById(String id) throws ExternalApiException;
    ExternalUpdateRecordResponse updateRecordById(String id, ExternalUpdateRecordPayload requestPayload) throws ExternalApiException;
    ExternalCreateRecordResponse createRecord(ExternalCreateRecordPayload requestPayload) throws ExternalApiException;
}
