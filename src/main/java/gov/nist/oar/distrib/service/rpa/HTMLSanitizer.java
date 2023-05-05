package gov.nist.oar.distrib.service.rpa;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * The {@code HTMLSanitizer} class provides a method for sanitizing HTML input strings
 * to remove any unsafe tags or attributes that could be used in a cross-site scripting (XSS) attack.
 *
 * This class uses the jsoup library to perform HTML sanitization, and can be used to safely
 * sanitize user-generated HTML input before displaying it on a web page.
 *
 * This version of the class uses the java.lang.reflect package for reflection utilities.
 */
public class HTMLSanitizer {

    // Logger
    private static final Logger LOGGER = LoggerFactory.getLogger(HTMLSanitizer.class);

    /**
     * Variable containing a list of allowed HTML tags and attributes used to sanitize input strings.
     * Change this according to needs.
     * This will remove the `a`, and `script` tags from the list of allowed tags.
     */
    private static final Safelist BASIC_SAFELIST = Safelist.basic().removeTags("a", "script");

    // Define a constant to limit the recursion depth
    private static final int MAX_DEPTH = 7;

    /**
     * This function sanitizes an input object, recursively, and returns the sanitized object.
     * It takes an input object of generic type T and returns an object of the same type.
     * To avoid infinite recursion, the function uses a recursion depth limit of MAX_DEPTH.
     *
     * If the input object is a StringBuilder, the function sanitizes its contents using the sanitizeHtml() method
     * and replaces the original contents with the sanitized value.
     *
     * If the input object is a string, the function sanitizes it using the sanitizeHtml() method and returns
     * the sanitized string.
     *
     * If the input object is a map, the function recursively sanitizes its values and returns a new map with
     * the sanitized values.
     *
     * If the input object is an array, the function recursively sanitizes its elements
     * and returns a new array with the sanitized elements.
     *
     * If the input object is not a string, StringBuilder, map, or array, the method recursively sanitizes
     * its fields using reflection.
     *
     * Note: implement checks for more types as needed. For the RPA use case, this should be enough.
     *
     * @param object the input object to sanitize
     * @throws RuntimeException if any exceptions occur during the sanitization process
     * @return the sanitized object
     */
    public static <T> T sanitize(T object) {
        return sanitizeRecursive(object, MAX_DEPTH);
    }

    private static <T> T sanitizeRecursive(T object, int depth) {
        if (object == null || depth == 0) {
            // If the input object is null or the recursion depth is 0, return the object as-is
            return object;
        }

        if (object instanceof StringBuilder) {
            // If the input object is a StringBuilder, sanitize its contents
            String sanitizedValue = sanitizeHtml(object.toString());
            StringBuilder sb = (StringBuilder) object;
            sb.replace(0, sb.length(), sanitizedValue);
            return (T) sb;
        } else if (object instanceof String) {
            // If the input object is a string, sanitize it
            String sanitizedValue = sanitizeHtml((String) object);
            return (T) sanitizedValue;
        } else if (object instanceof Map<?, ?>) {
            // If the input object is a map, recursively sanitize its values
            Map<Object, Object> map = (Map<Object, Object>) object;
            Map<Object, Object> sanitizedMap = new HashMap<>(map.size());
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                Object sanitizedValue = sanitizeRecursive(value, depth - 1);
                sanitizedMap.put(key, sanitizedValue);
            }
            return (T) sanitizedMap;
        } else if (object instanceof Object[]) {
            // If the input object is an array, recursively sanitize its elements
            Object[] array = (Object[]) object;
            for (int i = 0; i < array.length; i++) {
                Object value = array[i];
                Object sanitizedValue = sanitizeRecursive(value, depth - 1);
                array[i] = sanitizedValue;
            }
            return (T) array;
        } else {
            // If the input object is not a string, StringBuilder, or map, recursively sanitize its fields
            Field[] fields = object.getClass().getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(object);
                    Object sanitizedValue = sanitizeRecursive(value, depth - 1);
                    field.set(object, sanitizedValue);
                } catch (Exception e) {
                    LOGGER.warn("Unable to sanitize HTML inputs: " + e.getMessage());
                    throw new RuntimeException("Unable to sanitize HTML inputs: " + e.getMessage());
                }
            }
            return object;
        }
    }

    /**
     * Sanitizes the HTML input by scrubbing out any tags and attributes that are not explicitly allowed.
     * This will help prevent XSS attacks by sanitizing any user-provided HTML input and ensuring that
     * only safe HTML tags and attributes are allowed in the output.
     *
     * The safe HTML tags are provided by the {@link Safelist}.
     *
     * @param input the HTML input to sanitize
     * @return the sanitized input
     */
    private static String sanitizeHtml(String input) {

        // Jsoup removes the newline character (\n) by default from the HTML text
        // To prevent that, disable pretty-print
        Document.OutputSettings outputSettings = new Document.OutputSettings();
        outputSettings.prettyPrint(false);

        // Use the Jsoup.clean method to sanitize the input string
        return Jsoup.clean(input, "", BASIC_SAFELIST, outputSettings);
    }

}
