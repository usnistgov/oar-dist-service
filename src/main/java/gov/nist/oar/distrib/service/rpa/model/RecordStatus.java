package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class RecordStatus {
    @JsonProperty("recordId")
    String recordId;
    @JsonProperty("approvalStatus")
    String approvalStatus;
}