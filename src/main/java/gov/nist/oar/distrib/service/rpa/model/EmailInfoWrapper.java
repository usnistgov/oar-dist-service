package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import gov.nist.oar.distrib.service.rpa.utils.JsonSerializable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * A wrapper around the email info.
 * Adds a timestamp to the email info.
 */
@AllArgsConstructor
@NoArgsConstructor
public class EmailInfoWrapper extends JsonSerializable {
    /**
     * Represents the timestamp the email was sent.
     */
    @JsonProperty("timestamp")
    String timestamp;

    /**
     * Contains the email info.
     */
    @JsonProperty("email")
    EmailInfo emailInfo;

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public EmailInfo getEmailInfo() {
        return emailInfo;
    }

    public void setEmailInfo(EmailInfo emailInfo) {
        this.emailInfo = emailInfo;
    }
}