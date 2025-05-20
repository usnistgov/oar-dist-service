package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;

public class RecordCreationResult {
    private final RecordWrapper recordWrapper;
    private final int statusCode;

    public RecordCreationResult(RecordWrapper wrapper, int code) {
        this.recordWrapper = wrapper;
        this.statusCode = code;
    }

    public RecordWrapper getRecordWrapper() { return recordWrapper; }
    public int getStatusCode() { return statusCode; }
}
