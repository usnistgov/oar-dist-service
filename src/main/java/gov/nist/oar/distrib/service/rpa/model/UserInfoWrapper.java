package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class UserInfoWrapper {
    @JsonProperty("userInfo")
    UserInfo userInfo;
}