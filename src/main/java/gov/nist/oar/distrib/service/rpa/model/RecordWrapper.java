package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import gov.nist.oar.distrib.service.rpa.utils.JsonSerializable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * A wrapper around the record.
 * This acts as an envelope for the record.
 * This is used for parsing the record to a JSON with "record" as the key.
 */
@AllArgsConstructor
@NoArgsConstructor
public class RecordWrapper extends JsonSerializable {

    /**
     * Contains the record.
     */
    @JsonProperty("record")
    Record record;

    public Record getRecord() {
        return record;
    }

    public void setRecord(Record record) {
        this.record = record;
    }

}