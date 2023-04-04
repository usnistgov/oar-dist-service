package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import gov.nist.oar.distrib.service.rpa.utils.JsonSerializable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Represents a salesforce case record.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class Record extends JsonSerializable {

    /**
     * Represents the record ID.
     */
    @JsonProperty("id")
    private String id;

    /**
     * Represents the record case number.
     */
    @JsonProperty("caseNum")
    private String caseNum;

    /**
     * Contains the user information that was submitted through the request form.
     */
    @JsonProperty("userInfo")
    private UserInfo userInfo;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCaseNum() {
        return caseNum;
    }

    public void setCaseNum(String caseNum) {
        this.caseNum = caseNum;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfo userInfo) {
        this.userInfo = userInfo;
    }

}