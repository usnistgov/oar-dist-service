package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import gov.nist.oar.distrib.service.rpa.utils.JsonSerializable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Represents the status of a record.
 */
@AllArgsConstructor
@NoArgsConstructor
public class RecordStatus extends JsonSerializable {

    /**
     * Represents the record ID.
     */
    @JsonProperty("recordId")
    String recordId;

    /**
     * Represents the approval status the record.
     */
    @JsonProperty("approvalStatus")
    String approvalStatus;

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

}