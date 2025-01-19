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

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
	   "server.servlet.context-path=/od",
        "distrib.bagstore.mode=local",
        "distrib.bagstore.location=${basedir}/src/test/resources",
        "distrib.baseurl=http://localhost/od",
        "cloud.aws.region=us-east-1",
        "logging.path=${basedir}/target/surefire-reports",
})
public class VersionControllerTest {

    Logger logger = LoggerFactory.getLogger(VersionControllerTest.class);

    @LocalServerPort
    int port;

    TestRestTemplate websvc = new TestRestTemplate();
    HttpHeaders headers = new HttpHeaders();

    private String getBaseURL() {
        return "http://localhost:" + port + "/od";
    }

    @Test
    public void testGetServiceVersion() throws JSONException {
        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/",
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        String expect = "{ serviceName: oar-dist-service }";
        String got = resp.getBody();
        logger.info("### version response: " + got);
        JSONAssert.assertEquals(expect, got, false);
        assertTrue(got.contains("\"version\":"));
        assertTrue(!got.contains("\"version\":\"unknown\""));
        assertTrue(!got.contains("\"version\":\"missing\""));
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
    }

    @Test
    public void testDeleteNotAllowed() throws JSONException {
        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/",
                                                      HttpMethod.DELETE, req, String.class);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode());

        req = new HttpEntity<>(null, headers);
        resp = websvc.exchange(getBaseURL() + "/ds/", HttpMethod.POST, req, String.class);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode());

        req = new HttpEntity<>(null, headers);
        resp = websvc.exchange(getBaseURL() + "/ds/", HttpMethod.PATCH, req, String.class);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode());
    }

    @Test
    public void testHEAD() throws JSONException {
        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/",
                                                      HttpMethod.HEAD, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    public void testRedirectToServiceVersion() throws JSONException {
        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds",
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.FOUND, resp.getStatusCode());

        assertTrue(resp.getHeaders().getFirst("Location").endsWith("/od/ds/"));
    }

}
