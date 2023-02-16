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

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfo userInfo) {
        this.userInfo = userInfo;
    }
}