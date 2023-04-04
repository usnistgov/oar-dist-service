package gov.nist.oar.distrib.service.rpa.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@code JsonSerializable} class provides a base class for objects that can be serialized to JSON.
 * This class defines a {@code toString} method that uses the Jackson library to convert an object to a JSON string.
 *
 * <p>When extending this class, it is recommended to follow these requirements to ensure proper
 * serialization of the objects:
 * <ul>
 * <li>The class should have a no-argument constructor.</li>
 * <li>The class should have getter and setter methods for all properties.</li>
 * <li>The class should be annotated with {@code @JsonInclude(JsonInclude.Include.NON_NULL)}
 * to exclude null properties from serialization.</li>
 * </ul>
 */
public abstract class JsonSerializable {
    /**
     * Serializes the object to a JSON string.
     *
     * @return the JSON string representation of the object
     * @throws RuntimeException if an error occurs while serializing the object to JSON
     */
    @Override
    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        String jsonStr;
        try {
            jsonStr = mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return jsonStr;
    }
}
