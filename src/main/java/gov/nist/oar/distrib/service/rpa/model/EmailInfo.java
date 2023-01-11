package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EmailInfo {
    @JsonProperty("recordId")
    String recordId;
    @JsonProperty("recipient")
    String recipient;
    @JsonProperty("subject")
    String subject;
    @JsonProperty("content")
    String content;
}