package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/** A wrapper around the user info.
 */
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoWrapper {

    /** Contains the user info.
     */
    @JsonProperty("userInfo")
    UserInfo userInfo;

    @JsonProperty("recaptcha")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String recaptcha;

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

    @Override
    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        String jsonStr;
        try {
            jsonStr = mapper.writeValueAsString(this);
        } catch (
                JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return jsonStr;
    }
}