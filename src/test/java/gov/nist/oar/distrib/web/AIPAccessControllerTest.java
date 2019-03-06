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

import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.skyscreamer.jsonassert.JSONAssert;
import org.json.JSONException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = NISTDistribServiceConfig.class,
                webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "distrib.bagstore.mode=local",
        "distrib.bagstore.location=${basedir}/src/test/resources",
        "distrib.baseurl=http://localhost/oar-distrb-service",
        "logging.path=${basedir}/target/surefire-reports"
})
public class AIPAccessControllerTest {

    Logger logger = LoggerFactory.getLogger(AIPAccessControllerTest.class);

    @LocalServerPort
    int port;

    TestRestTemplate websvc = new TestRestTemplate();
    HttpHeaders headers = new HttpHeaders();

    @Test
    public void testDescribeAIP() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() +
                                                          "/ds/_aip/mds1491.mbag0_2-0.zip/_info",
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        // logger.info("testDescribeAIP(): got:\n  " + resp.getBody());

        String expect = "{ name:mds1491.mbag0_2-0.zip, contentType: \"application/zip\"," +
                          "contentLength:9841, aipid:mds1491, serialization:zip}";

        String got = resp.getBody();
        JSONAssert.assertEquals(expect, got, false);
        assertTrue(got.contains("\"downloadURL\":"));
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
    }

    @Test
    public void testDescribeAIPsBadID() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/_aip/goober.zip",
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/oar-dist-service/ds/_aip/goober.zip\"," +
                                 "status:404,message:\"AIP file not found\",method:GET}",
                                resp.getBody(), true);
    }

    @Test
    public void testDownloadAIP() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() +
                                                          "/ds/_aip/mds1491.mbag0_2-0.zip",
                                                      HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("application/zip", resp.getHeaders().getFirst("Content-Type"));
        assertEquals(9841, resp.getBody().length());
    }

    private String getBaseURL() {
        return "http://localhost:" + port + "/oar-dist-service";
    }
}
