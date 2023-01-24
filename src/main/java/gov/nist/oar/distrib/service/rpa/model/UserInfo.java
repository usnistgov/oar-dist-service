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
public class UserInfo {
    @JsonProperty("fullName")
    private String fullName;
    @JsonProperty("organization")
    private String organization;
    @JsonProperty("email")
    private String email;
    @JsonProperty("receiveEmails")
    private String receiveEmails;
    @JsonProperty("country")
    private String country;
    @JsonProperty("approvalStatus")
    private String approvalStatus;
    @JsonProperty("productTitle")
    private String productTitle;
    @JsonProperty("subject")
    private String subject;
    @JsonProperty("description")
    private String description;
}
