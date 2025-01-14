package gov.nist.oar.distrib.service.rpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import gov.nist.oar.distrib.service.rpa.utils.JsonSerializable;
import org.junit.jupiter.api.Test;

/**
 * This class provides unit tests for the {@link JsonSerializable} class.
 */
public class JsonSerializableTest {

    /**
     * Tests that an object can be serialized to a JSON string using the {@code toString} method.
     * The method creates a new object, serializes it to a JSON string, and then verifies that the JSON string is correct.
     */
    @Test
    public void testToString() {
        // Create a new test object
        TestObject obj = new TestObject("someValue");

        // Serialize the object to a JSON string using the toString method
        String jsonString = obj.toString();

        // Verify that the JSON string is correct
        assertEquals("{\"value\":\"someValue\"}", jsonString);
    }

    /**
     * Tests that an exception is thrown when attempting to serialize an object that is missing a getter.
     * The method creates a new object that is missing a getter, attempts to serialize it to a JSON string using the
     * {@code toString} method, and then verifies that the expected exception is thrown.
     */
    @Test
    public void testMissingGetter() {
        // Create a mock object that extends JsonSerializable
        TestObjectMissingGetter obj = new TestObjectMissingGetter("someValue");

        // Verify that an exception is thrown when serializing the object
        try {
            obj.toString();
            fail("Expected exception was not thrown");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("No serializer found for class"));
        }
    }

    // A simple test class that extends JsonSerializable
    public static class TestObject extends JsonSerializable {
        private String value;

        public TestObject(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    // A simple test class that extends JsonSerializable, but missing a getter
    public static class TestObjectMissingGetter extends JsonSerializable {
        @SuppressWarnings("unused") // Suppress the warning about unused value
        private String value;

        public TestObjectMissingGetter(String value) {
            this.value = value;
        }

        // Getter commented out
        // public String getValue() {
        //    return value;
        // }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
