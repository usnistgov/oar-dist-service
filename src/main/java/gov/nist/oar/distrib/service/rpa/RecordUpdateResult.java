package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.service.rpa.model.RecordStatus;

/**
 * Encapsulates the result of a record update operation.
 * Contains the data needed for async post-processing after the synchronous
 * update completes.
 */
public class RecordUpdateResult {
    private final RecordStatus recordStatus;
    private final Record record;
    private final String datasetId;

    public RecordUpdateResult(RecordStatus recordStatus, Record record, String datasetId) {
        this.recordStatus = recordStatus;
        this.record = record;
        this.datasetId = datasetId;
    }

    public RecordStatus getRecordStatus() {
        return recordStatus;
    }

    public Record getRecord() {
        return record;
    }

    public String getDatasetId() {
        return datasetId;
    }
}
