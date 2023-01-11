package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class EmailInfoWrapper {
    @JsonProperty("timestamp")
    String timestamp;

    @JsonProperty("email")
    EmailInfo emailInfo;
}
