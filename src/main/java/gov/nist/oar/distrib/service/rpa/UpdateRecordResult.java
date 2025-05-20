package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.service.rpa.model.RecordStatus;

public class UpdateRecordResult {

    private final RecordStatus recordStatus;
    private final Record record;
    private final String randomId;

    public UpdateRecordResult(RecordStatus recordStatus, Record record, String randomId) {
        this.recordStatus = recordStatus;
        this.record = record;
        this.randomId = randomId;
    }

    public RecordStatus getRecordStatus() {
        return recordStatus;
    }

    public Record getRecord() {
        return record;
    }

    public String getRandomId() {
        return randomId;
    }

}
