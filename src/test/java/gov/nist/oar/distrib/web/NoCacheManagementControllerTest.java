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

import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
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

/**
 * This class tests the CachemanagementController endpoints when no cache manager is in use.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = NISTDistribServiceConfig.class,
                webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "distrib.bagstore.mode=local",
    "distrib.bagstore.location=${basedir}/src/test/resources",
    "distrib.baseurl=http://localhost/oar-distrb-service",
    "distrib.cachemgr.restapi.accesstoken=SECRET",
    "logging.path=${basedir}/target/surefire-reports",
    "cloud.aws.region=us-east-1"
})
public class NoCacheManagementControllerTest {

    Logger logger = LoggerFactory.getLogger(NoCacheManagementControllerTest.class);

    @LocalServerPort
    int port;

    TestRestTemplate websvc = new TestRestTemplate();
    HttpHeaders headers = new HttpHeaders();

    @Autowired
    CacheManagerProvider provider;

    public NoCacheManagementControllerTest() {
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer SECRET");
    }

    @Test
    public void testProvider() {
        assertFalse(provider.canCreateManager());
    }

    @Test
    public void testGetStatus() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/", 
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/cache/\"," +
                                 "status:404,message:\"Cache Management is not in operation\",method:GET}",
                                resp.getBody(), true);
    }

    @Test
    public void testSummarizeVolumes() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/volumes/", 
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/cache/volumes/\"," +
                                 "status:404,message:\"Cache Management is not in operation\",method:GET}",
                                resp.getBody(), true);
    }

    @Test
    public void testSummarizeVolume() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/volumes/goober", 
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/cache/volumes/goober\"," +
                                 "status:404,message:\"Cache Management is not in operation\",method:GET}",
                                resp.getBody(), true);
    }

    @Test
    public void testSummarizeContentss() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/objects/", 
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/cache/objects/\"," +
                                 "status:404,message:\"Cache Management is not in operation\",method:GET}",
                                resp.getBody(), true);
    }

    @Test
    public void testListObjectsFor() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/objects/goober", 
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/cache/objects/goober\"," +
                                 "status:404,message:\"Cache Management is not in operation\",method:GET}",
                                resp.getBody(), true);
    }

    @Test
    public void testListObjectsFor2() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/objects/goober/:checked", 
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/cache/objects/goober/:checked\"," +
                                 "status:404,message:\"Cache Management is not in operation\",method:GET}",
                                resp.getBody(), true);
    }

    @Test
    public void testListObjectsFor3() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/objects/goober/gurn/1", 
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/cache/objects/goober/gurn/1\"," +
                                 "status:404,message:\"Cache Management is not in operation\",method:GET}",
                                resp.getBody(), true);
    }

    private String getBaseURL() {
        return "http://localhost:" + port + "/od";
    }
}
