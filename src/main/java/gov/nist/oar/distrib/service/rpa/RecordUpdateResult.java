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
    private final String previousApprovalStatus;

    public RecordUpdateResult(RecordStatus recordStatus, Record record, String datasetId) {
        this(recordStatus, record, datasetId, null);
    }

    public RecordUpdateResult(RecordStatus recordStatus, Record record, String datasetId,
            String previousApprovalStatus) {
        this.recordStatus = recordStatus;
        this.record = record;
        this.datasetId = datasetId;
        this.previousApprovalStatus = previousApprovalStatus;
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

    public String getPreviousApprovalStatus() {
        return previousApprovalStatus;
    }
}
