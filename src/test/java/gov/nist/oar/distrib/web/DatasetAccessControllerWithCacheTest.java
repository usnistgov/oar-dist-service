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
import gov.nist.oar.distrib.cachemgr.CacheManager;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagCacheManager;

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
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.ArrayList;
import javax.servlet.http.HttpServletResponseWrapper;

import org.junit.Test;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.After;
import org.junit.AfterClass;
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
        "logging.path=${basedir}/target/surefire-reports",
        "distrib.cachemgr.admindir=${java.io.tmpdir}/testcmgr",
        "distrib.cachemgr.smallSizeLimit=500",
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
        "distrib.cachemgr.volumes[1].roles[1]=general",
        "distrib.cachemgr.volumes[1].redirectBase=https://pdr.net/gen/",
        // "logging.level.org.springframework.web=DEBUG"
        "logging.level.gov.nist.oar.distrib=DEBUG",
        "logging.path=${basedir}/target",
        "logging.file=tst.log",
        "cloud.aws.region=us-east-1"
})
public class DatasetAccessControllerWithCacheTest {

    Logger logger = LoggerFactory.getLogger(DatasetAccessControllerTest.class);

    @LocalServerPort
    int port;

    TestRestTemplate websvc = new TestRestTemplate();
    HttpHeaders headers = new HttpHeaders();

    private String getBaseURL() {
        return "http://localhost:" + port + "/od";
    }

    static File testdir = null;

    @Autowired
    CacheManagerProvider provider;

    @Autowired
    DatasetAccessController ctrlr;

    public void cleanTestDir(File testdir) throws IOException, ConfigurationException {
        /*
        */
        if (testdir.exists()) 
            FileSystemUtils.deleteRecursively(testdir);
        testdir.mkdirs();
        provider.cfg.theCache = null;
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
        assertTrue(provider.canProvideManager());
    }

    @Test
    public void testDescribeAIPs() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/_aip",
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        String expect = "[{ name:mds1491.mbag0_2-0.zip, contentLength:9841 }," +
                         "{ name:mds1491.1_1_0.mbag0_4-1.zip, contentLength:14077 }]";

        String got = resp.getBody();
        JSONAssert.assertEquals(expect, got, false);
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
    }

    @Test
    public void testListAIPVersions() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/_aip/_v",
                                                      HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        String expect = "[\"1.1.0\", \"0\"]";
        JSONAssert.assertEquals(expect, resp.getBody(), false);
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
    }
    
    @Test
    public void testDownloadFile() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/trial1.json",
                                                      HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        assertEquals(69, resp.getBody().length());
    }

    @Test
    public void testDownloadFileViaRedirect() throws CacheManagementException, ConfigurationException {
        // this file is a "large" file that will get put into the large volume which has a
        // redirect capability configured in
        CacheManager cm = provider.getPDRCacheManager();
        cm.cache("mds1491/trial3/trial3a.json");

        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/trial3/trial3a.json",
                                                      HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.FOUND, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Location").equals("https://pdr.net/gen/mds1491/trial3/trial3a.json"));
    }

    @Test
    public void testDownloadFileFromCache() throws CacheManagementException, ConfigurationException {
        // this file is a "small" file that will get put into the small volume which has no
        // redirect capability configured in
        CacheManager cm = provider.getPDRCacheManager();
        cm.cache("mds1491/trial1.json");

        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/trial1.json",
                                                      HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        assertEquals(69, resp.getBody().length());
    }

    @Test
    public void testDownloadFileViaARK() throws CacheManagementException, ConfigurationException {
        CacheManager cm = provider.getPDRCacheManager();
        cm.cache("mds1491/trial1.json");

        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/ark:/1212/mds1491/trial1.json",
                                                      HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        assertEquals(69, resp.getBody().length());
    }

    @Test
    public void testDownloadFileFromVersion() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/_v/0/trial1.json",
                                                      HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        assertEquals(69, resp.getBody().length());

        req = new HttpEntity<String>(null, headers);
        resp = websvc.exchange(getBaseURL() + "/ds/mds1491/_v/1.1.0/trial1.json",
                               HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        assertEquals(69, resp.getBody().length());
    }

    @Test
    public void testDownloadFileInfo() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/trial1.json",
                                                      HttpMethod.HEAD, req, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        assertNull(resp.getBody());
    }

}
