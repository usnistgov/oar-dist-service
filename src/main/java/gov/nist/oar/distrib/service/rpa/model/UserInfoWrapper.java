package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import gov.nist.oar.distrib.service.rpa.utils.JsonSerializable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * A wrapper around the user info.
 */

@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // ignore any unknown properties during deserialization.
public class UserInfoWrapper extends JsonSerializable {

    /**
     * Contains the user info.
     */
    @JsonProperty("userInfo")
    UserInfo userInfo;

    @JsonProperty("recaptcha")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String recaptcha;

    /**
     * Used to store unknown properties encountered during JSON deserialization.
     */
    @JsonIgnore
    private Map<String, Object> unknownProperties = new HashMap<>();

    public UserInfoWrapper(UserInfo userInfo, String recaptcha) {
        this.userInfo = userInfo;
        this.recaptcha = recaptcha;
        this.unknownProperties =  new HashMap<>();
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfo userInfo) {
        this.userInfo = userInfo;
    }

    public String getRecaptcha() {
        return recaptcha;
    }

    public void setRecaptcha(String recaptcha) {
        this.recaptcha = recaptcha;
    }


    /**
     * Sets an unknown property encountered during JSON deserialization.
     *
     * The @JsonAnySetter annotation will ensure that any JSON fields not matching known properties will be stored
     * in the unknownProperties map.
     *
     * @param name  The name of the unknown property.
     * @param value The value of the unknown property.
     */
    @JsonAnySetter
    public void setUnknownProperty(String name, Object value) {
        unknownProperties.put(name, value);
    }

    /**
     * Retrieves the map of unknown properties encountered during JSON deserialization.
     *
     * @return The map of unknown properties.
     */
    public Map<String, Object> getUnknownProperties() {
        return unknownProperties;
    }

}