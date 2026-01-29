/**
 * This software was developed at the National Institute of Standards and Technology by employees of
 * the Federal Government in the course of their official duties. Pursuant to title 17 Section 105
 * of the United States Code this software is not subject to copyright protection and is in the
 * public domain. This is an experimental system. NIST assumes no responsibility whatsoever for its
 * use by other parties, and makes no guarantees, expressed or implied, about its quality,
 * reliability, or any other characteristic. We would appreciate acknowledgement if the software is
 * used. This software can be redistributed and/or modified freely provided that any derivative
 * works bear some notice that they are derived from it, and any modified versions bear some notice
 * that they have been modified.
 */
package gov.nist.oar.distrib.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Integration tests for malformed JSON request handling.
 * <p>
 * These tests verify that the application returns HTTP 400 Bad Request (not 500 Internal Server Error)
 * when clients send malformed JSON requests. This is important for security scanners which flag
 * unhandled errors (500 responses) as potential vulnerabilities.
 * <p>
 * Tests send raw JSON strings directly to endpoints to simulate malformed input that cannot be
 * represented as valid Java objects.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = NISTDistribServiceConfig.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "server.servlet.context-path=/od",
    "distrib.bagstore.mode=local",
    "distrib.bagstore.location=${basedir}/src/test/resources",
    "distrib.baseurl=http://localhost/od/ds",
    "logging.path=${basedir}/target/surefire-reports",
    "distrib.packaging.maxpackagesize = 2000000",
    "distrib.packaging.maxfilecount = 2",
    "distrib.packaging.allowedurls = nist.gov|s3.amazonaws.com/nist-midas|httpstat.us",
    "cloud.aws.region=us-east-1"
})
public class MalformedJsonRequestTest {

    private static final Logger logger = LoggerFactory.getLogger(MalformedJsonRequestTest.class);

    @LocalServerPort
    int port;

    TestRestTemplate restTemplate = new TestRestTemplate();

    private String getBaseURL() {
        return "http://localhost:" + port + "/od";
    }

    /**
     * Test that numeric overflow in fileSize field returns 400 Bad Request.
     * <p>
     * This is the specific case reported by security scanners: sending a number larger than
     * Long.MAX_VALUE (9223372036854775807) causes Jackson to throw an InvalidFormatException
     * which should be handled as a client error, not a server error.
     */
    @Test
    public void testNumericOverflow_bundlePlan_returns400() throws URISyntaxException {
        String malformedJson = """
            {
                "requestId": "test-overflow",
                "bundleName": "testdownload",
                "includeFiles": [{
                    "filePath": "/test/file.pdf",
                    "downloadUrl": "https://example.com/file.pdf",
                    "fileSize": 999999999999999999999
                }],
                "bundleSize": 0,
                "filesInBundle": 1
            }
            """;

        ResponseEntity<String> response = sendRawJson("/ds/_bundle_plan", malformedJson);

        logger.info("Numeric overflow test response: {} - {}", response.getStatusCode(), response.getBody());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
            "Numeric overflow should return 400 Bad Request, not 500");
        assertTrue(response.getBody().contains("Malformed JSON input"),
            "Response should indicate malformed JSON");
        assertTrue(response.getBody().contains("out of range") || response.getBody().contains("Numeric value"),
            "Response should contain specific error detail");
    }

    /**
     * Test that numeric overflow in bundleSize field returns 400 Bad Request.
     */
    @Test
    public void testNumericOverflow_bundleSize_returns400() throws URISyntaxException {
        String malformedJson = """
            {
                "requestId": "test-overflow-bundlesize",
                "bundleName": "testdownload",
                "includeFiles": [{
                    "filePath": "/test/file.pdf",
                    "downloadUrl": "https://example.com/file.pdf",
                    "fileSize": 100
                }],
                "bundleSize": 999999999999999999999,
                "filesInBundle": 1
            }
            """;

        ResponseEntity<String> response = sendRawJson("/ds/_bundle_plan", malformedJson);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
            "Numeric overflow in bundleSize should return 400 Bad Request");
    }

    /**
     * Test that negative overflow returns 400 Bad Request.
     * <p>
     * This matches the exact error from the security scan log:
     * "Numeric value (-99999999999999999999) out of range of long"
     */
    @Test
    public void testNegativeNumericOverflow_returns400() throws URISyntaxException {
        String malformedJson = """
            {
                "requestId": "test-negative-overflow",
                "bundleName": "testdownload",
                "includeFiles": [{
                    "filePath": "/test/file.pdf",
                    "downloadUrl": "https://example.com/file.pdf",
                    "fileSize": -999999999999999999999
                }],
                "bundleSize": 0,
                "filesInBundle": 1
            }
            """;

        ResponseEntity<String> response = sendRawJson("/ds/_bundle_plan", malformedJson);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
            "Negative numeric overflow should return 400 Bad Request");
    }

    /**
     * Test that syntactically invalid JSON returns 400 Bad Request.
     */
    @Test
    public void testInvalidJsonSyntax_returns400() throws URISyntaxException {
        String invalidJson = "{ this is not valid json }";

        ResponseEntity<String> response = sendRawJson("/ds/_bundle_plan", invalidJson);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
            "Invalid JSON syntax should return 400 Bad Request");
    }

    /**
     * Test that wrong field type (string where number expected) returns 400 Bad Request.
     */
    @Test
    public void testWrongFieldType_returns400() throws URISyntaxException {
        String malformedJson = """
            {
                "requestId": "test-wrong-type",
                "bundleName": "testdownload",
                "includeFiles": [{
                    "filePath": "/test/file.pdf",
                    "downloadUrl": "https://example.com/file.pdf",
                    "fileSize": "not-a-number"
                }],
                "bundleSize": 0,
                "filesInBundle": 1
            }
            """;

        ResponseEntity<String> response = sendRawJson("/ds/_bundle_plan", malformedJson);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
            "Wrong field type should return 400 Bad Request");
    }

    /**
     * Test that incomplete JSON (missing closing brace) returns 400 Bad Request.
     */
    @Test
    public void testIncompleteJson_returns400() throws URISyntaxException {
        String incompleteJson = """
            {
                "requestId": "test-incomplete",
                "bundleName": "testdownload"
            """;

        ResponseEntity<String> response = sendRawJson("/ds/_bundle_plan", incompleteJson);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
            "Incomplete JSON should return 400 Bad Request");
    }

    /**
     * Test numeric overflow on the /ds/_bundle endpoint.
     */
    @Test
    public void testNumericOverflow_bundle_returns400() throws URISyntaxException {
        String malformedJson = """
            {
                "requestId": "test-overflow-bundle",
                "bundleName": "testdownload",
                "includeFiles": [{
                    "filePath": "/test/file.pdf",
                    "downloadUrl": "https://example.com/file.pdf",
                    "fileSize": 999999999999999999999
                }],
                "bundleSize": 0,
                "filesInBundle": 1
            }
            """;

        ResponseEntity<String> response = sendRawJson("/ds/_bundle", malformedJson);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
            "Numeric overflow on _bundle endpoint should return 400 Bad Request");
    }

    /**
     * Test that the error response contains useful context for debugging.
     */
    @Test
    public void testErrorResponseContainsContext() throws URISyntaxException {
        String malformedJson = """
            {
                "bundleName": "test",
                "includeFiles": [{"filePath": "/x", "downloadUrl": "http://x", "fileSize": 999999999999999999999}]
            }
            """;

        ResponseEntity<String> response = sendRawJson("/ds/_bundle_plan", malformedJson);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        String body = response.getBody();
        logger.info("Error response body: {}", body);

        // Verify response contains useful debugging information
        assertTrue(body.contains("400"), "Response should contain status code 400");
        assertTrue(body.contains("_bundle_plan") || body.contains("requestURL"),
            "Response should contain the request URL");
        assertTrue(body.contains("Malformed JSON input"),
            "Response should contain error message prefix");
        assertTrue(body.contains("out of range") || body.contains("Numeric value"),
            "Response should contain specific error detail from Jackson");
    }

    /**
     * Test that error response does NOT expose internal class names (security).
     * Jackson's getOriginalMessage() returns parsing details without class names.
     */
    @Test
    public void testErrorResponseDoesNotExposeInternalClasses() throws URISyntaxException {
        String malformedJson = """
            {
                "bundleName": "test",
                "includeFiles": [{"filePath": "/x", "downloadUrl": "http://x", "fileSize": 999999999999999999999}]
            }
            """;

        ResponseEntity<String> response = sendRawJson("/ds/_bundle_plan", malformedJson);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        String body = response.getBody();
        logger.info("Security test - Error response body: {}", body);

        // Verify response does NOT contain internal class names
        assertFalse(body.contains("gov.nist.oar"),
            "Response should NOT expose internal package names");
        assertFalse(body.contains("BundleRequest"),
            "Response should NOT expose internal class names");
        assertFalse(body.contains("FileRequest"),
            "Response should NOT expose internal class names");

        // But should still contain helpful error details
        assertTrue(body.contains("out of range") || body.contains("Numeric value"),
            "Response should contain helpful error detail");
    }

    /**
     * Helper method to send raw JSON string to an endpoint.
     */
    private ResponseEntity<String> sendRawJson(String endpoint, String json) throws URISyntaxException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        RequestEntity<String> request = RequestEntity
                .post(new URI(getBaseURL() + endpoint))
                .headers(headers)
                .body(json);

        return restTemplate.exchange(request, String.class);
    }
}
