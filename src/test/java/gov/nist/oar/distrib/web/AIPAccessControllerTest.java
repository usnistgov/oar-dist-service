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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = NISTDistribServiceConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "distrib.bagstore.mode=local",
        "distrib.bagstore.location=${basedir}/src/test/resources",
        "distrib.baseurl=http://localhost/oar-distrb-service",
        "logging.path=${basedir}/target/surefire-reports",
        "cloud.aws.region=us-east-1",
	"server.servlet.context-path=/od"
})
public class AIPAccessControllerTest {

    Logger logger = LoggerFactory.getLogger(AIPAccessControllerTest.class);

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate websvc;

    private HttpHeaders headers;

    @BeforeEach
    public void setUp() {
        headers = new HttpHeaders();
    }

    @Test
    public void testDescribeAIP() throws JSONException {
        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() +
                        "/ds/_aip/mds1491.mbag0_2-0.zip/_info",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        logger.info("testDescribeAIP(): got:\n  " + resp.getBody());

        String expect = "{ name:mds1491.mbag0_2-0.zip, contentType: \"application/zip\"," +
                "contentLength:9841, aipid:mds1491, serialization:zip}";

        String got = resp.getBody();
        JSONAssert.assertEquals(expect, got, false);
        assertTrue(got.contains("\"downloadURL\":"));
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
    }

    @Test
    public void testDescribeAIPsBadID() throws JSONException {
        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/_aip/goober.zip",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/ds/_aip/goober.zip\"," +
                        "status:404,message:\"AIP file not found\",method:GET}",
                resp.getBody(), true);
    }

    @Test
public void testDownloadAIP() {
    HttpEntity<String> req = new HttpEntity<>(null, headers);
    ResponseEntity<byte[]> resp = websvc.exchange(getBaseURL() +
             "/ds/_aip/mds1491.mbag0_2-0.zip", HttpMethod.GET, req, byte[].class);

    logger.info("Expected file size: 9841");
    logger.info("Actual Content-Length in response: " + resp.getHeaders().getFirst("Content-Length"));
    logger.info("Actual bytes received: " + resp.getBody().length);

    assertEquals(HttpStatus.OK, resp.getStatusCode());
    assertEquals("application/zip", resp.getHeaders().getFirst("Content-Type"));
    assertEquals(9841, resp.getBody().length);  // Compare byte array length
}


    private String getBaseURL() {
        return "http://localhost:" + port + "/od";
    }
}
