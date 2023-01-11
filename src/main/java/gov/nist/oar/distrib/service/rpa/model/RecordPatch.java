package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Setter
@Getter
public class RecordPatch {
    @JsonProperty("Approval_Status__c")
    String approvalStatus;

    @Override
    public String toString() {
        return super.toString();
    }
}
