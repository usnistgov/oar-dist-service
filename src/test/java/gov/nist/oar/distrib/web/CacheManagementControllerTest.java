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

import gov.nist.oar.distrib.cachemgr.pdr.HeadBagCacheManager;

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
import org.springframework.util.FileSystemUtils;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.skyscreamer.jsonassert.JSONAssert;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONTokener;
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
    "logging.path=${basedir}/target/surefire-reports",
    "distrib.baseurl=http://localhost/od/ds",
    "distrib.cachemgr.admindir=${java.io.tmpdir}/testcmgr",
    "distrib.cachemgr.headbagCacheSize=40000000",
    "distrib.cachemgr.volumes[0].location=file://vols/king",
    "distrib.cachemgr.volumes[0].name=king",
    "distrib.cachemgr.volumes[0].capacity=30000000",
    "distrib.cachemgr.volumes[1].location=file://vols/pratt",
    "distrib.cachemgr.volumes[1].name=pratt",
    "distrib.cachemgr.volumes[1].capacity=36000000",
    "distrib.cachemgr.volumes[0].roles[0]=small",
    "distrib.cachemgr.volumes[0].roles[1]=fast",
    "distrib.cachemgr.volumes[1].roles[0]=large",
    "distrib.cachemgr.volumes[1].roles[1]=general"
})
public class CacheManagementControllerTest {

    Logger logger = LoggerFactory.getLogger(CacheManagementControllerTest.class);

    @LocalServerPort
    int port;

    TestRestTemplate websvc = new TestRestTemplate();
    HttpHeaders headers = new HttpHeaders();

    static File testdir = null;

    @Autowired
    CacheManagerProvider provider;

    public void cleanTestDir(File testdir) throws IOException, ConfigurationException {
        /*
        */
        if (testdir.exists()) 
            FileSystemUtils.deleteRecursively(testdir);
        testdir.mkdirs();
        HeadBagCacheManager hbcm = provider.createHeadBagManager();
        provider.createPDRCacheManager(hbcm);
    }

    @BeforeClass
    public static void setUpClass() throws IOException {
        String tmpdir = System.getProperty("java.io.tmpdir");
        if (tmpdir == null)
            throw new RuntimeException("java.io.tmpdir property not set");
        File tmp = new File(tmpdir);
        if (! tmp.exists())
            tmp.mkdir();
        testdir = new File(tmp, "testcmgr");
        testdir.mkdirs();
        // cleanTestDir(testdir);
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        testdir.delete();
    }

    @After
    public void tearDown() throws IOException, ConfigurationException {
        cleanTestDir(testdir);
    }

    @Test
    public void testConfig() {
        assertTrue(provider.canCreateManager());
    }

    @Test
    public void testGetStatus() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/", 
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/cache/\"," +
                                 "status:200,message:\"Cache Manager in Use\",method:GET}",
                                resp.getBody(), true);
    }

    @Test
    public void testSummarizeVolumes() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/volumes/", 
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JSONArray summary = new JSONArray(new JSONTokener(resp.getBody()));
        assertEquals(2, summary.length());
    }

    @Test
    public void testSummarizeVolume() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/volumes/goober", 
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());

        resp = websvc.exchange(getBaseURL() + "/cache/volumes/king", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JSONObject summary = new JSONObject(new JSONTokener(resp.getBody()));
        assertEquals(0, summary.getInt("filecount"));
        assertEquals(0, summary.getInt("totalsize"));
        assertEquals(30000000, summary.getLong("capacity"));

        resp = websvc.exchange(getBaseURL() + "/cache/volumes/pratt", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        summary = new JSONObject(new JSONTokener(resp.getBody()));
        assertEquals(0, summary.getInt("filecount"));
        assertEquals(0, summary.getInt("totalsize"));
        assertEquals(36000000, summary.getLong("capacity"));
    }

    @Test
    public void testSummarizeContents() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/objects/", 
                                                      HttpMethod.GET, req, String.class);
        JSONArray summary = new JSONArray(new JSONTokener(resp.getBody()));
        assertEquals(0, summary.length());
    }

    @Test
    public void testSummarizeDataset() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/objects/goober", 
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        // JSONArray summary = new JSONArray(new JSONTokener(resp.getBody()));
        // assertEquals(0, summary.length());
    }

    @Test
    public void testListObjectsFor2() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/objects/goober/:checked", 
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        // JSONArray summary = new JSONArray(new JSONTokener(resp.getBody()));
        // assertEquals(0, summary.length());
    }

    @Test
    public void testListObjectsFor3() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/objects/goober/gurn/1", 
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        // JSONArray summary = new JSONArray(new JSONTokener(resp.getBody()));
        // assertEquals(0, summary.length());
    }

    private String getBaseURL() {
        return "http://localhost:" + port + "/od";
    }
}
