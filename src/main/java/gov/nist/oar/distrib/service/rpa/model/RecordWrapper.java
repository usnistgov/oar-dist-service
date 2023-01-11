package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

@Getter
public class RecordWrapper {
    @JsonProperty("record")
    Record record;

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