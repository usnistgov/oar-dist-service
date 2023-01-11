package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Setter
@Getter
public class Record {
    @JsonProperty("id")
    private String id;
    @JsonProperty("caseNum")
    private String caseNum;
    @JsonProperty("userInfo")
    private UserInfo userInfo;

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
