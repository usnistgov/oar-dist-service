package gov.nist.oar.distrib.service.rpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import gov.nist.oar.distrib.service.rpa.utils.Utils;

/**
 * Tests for the {@link Utils} class.
 */
public class UtilsTest {

    /**
     * Tests the buildUrl method with a non-null query parameter map.
     *
     * @throws URISyntaxException if the URI is not properly formatted
     */
    @Test
    public void testBuildUrl() throws URISyntaxException {
        String baseUrl = "https://example.com";
        String path = "/test";
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("param1", "value1");
        queryParams.put("param2", "value2");

        String expectedUrl = "https://example.com/test?param1=value1&param2=value2";
        String actualUrl = Utils.buildUrl(baseUrl, path, queryParams);

        assertEquals(expectedUrl, actualUrl);
    }

    /**
     * Tests the buildUrl method with a null query parameter map.
     *
     * @throws URISyntaxException if the URI is not properly formatted
     */
    @Test
    public void testBuildUrlWithoutQueryParams() throws URISyntaxException {
        String baseUrl = "https://example.com";
        String path = "/test";

        String expectedUrl = "https://example.com/test";
        String actualUrl = Utils.buildUrl(baseUrl, path, null);

        assertEquals(expectedUrl, actualUrl);
    }

    /**
     * Tests the buildUrl method with an invalid instance URL.
     *
     * @throws URISyntaxException if the URI is not properly formatted
     */
    @Test
    public void testBuildUrlWithInvalidUrl() {
        String baseUrl = "invalid url";
        String path = "/test";

        try {
            Utils.buildUrl(baseUrl, path, null);
            fail("Expected URISyntaxException to be thrown");
        } catch (URISyntaxException e) {
            // baseUrl has space in it, throws illegal character
            assertTrue(e.getMessage().contains("Illegal character in path"));
        }
    }

    /**
     * Test readErrorStream method with null input.
     * Expected result is null.
     * @throws Exception if an error occurs during the test.
     */
    @Test
    public void testReadErrorStream_withNullInput() throws Exception {
        assertNull(Utils.readErrorStream(null));
    }

    /**
     * Test readErrorStream method with empty input.
     * Expected result is an empty string.
     * @throws Exception if an error occurs during the test.
     */
    @Test
    public void testReadErrorStream_withEmptyInput() throws Exception {
        InputStream inputErrorStream = new ByteArrayInputStream(new byte[0]);
        assertEquals("", Utils.readErrorStream(inputErrorStream));
    }

    /**
     * Test readErrorStream method with non-empty input.
     * Expected result is the input error message as a string.
     * @throws Exception if an error occurs during the test.
     */
    @Test
    public void testReadErrorStream_withNonEmptyInput() throws Exception {
        String expected = "Test error message";
        InputStream inputErrorStream = new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, Utils.readErrorStream(inputErrorStream));
    }
}

