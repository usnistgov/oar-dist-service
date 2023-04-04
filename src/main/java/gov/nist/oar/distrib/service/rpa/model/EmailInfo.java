package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import gov.nist.oar.distrib.service.rpa.utils.JsonSerializable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Contains information about the email to send.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class EmailInfo extends JsonSerializable {
    /**
     * Represents record ID this email will be part of.
     */
    @JsonProperty("recordId")
    String recordId;
    /**
     * Represents the recipient of the email.
     */
    @JsonProperty("recipient")
    String recipient;
    /**
     * Represents the subject of the email.
     */
    @JsonProperty("subject")
    String subject;
    /**
     * Represents the content of the email (in HTML).
     */
    @JsonProperty("content")
    String content;

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

}