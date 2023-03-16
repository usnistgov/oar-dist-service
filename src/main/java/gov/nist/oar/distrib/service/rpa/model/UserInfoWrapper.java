package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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
}