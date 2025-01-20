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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.FileSystemUtils;

import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagCacheManager;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheManager;

/**
 * This class tests the CacheManagementController endpoints when no cache manager is in use.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = NISTDistribServiceConfig.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
	"server.servlet.context-path=/od",
    "distrib.bagstore.mode=local",
    "distrib.bagstore.location=${basedir}/src/test/resources",
    "distrib.baseurl=http://localhost/oar-distrb-service",
    "logging.path=${basedir}/target/surefire-reports",
    "distrib.baseurl=http://localhost/od/ds",
    "distrib.cachemgr.admindir=${java.io.tmpdir}/testcmgr",
    "distrib.cachemgr.headbagCacheSize=40000000",
    "distrib.cachemgr.checkDutyCycle=3",
    "distrib.cachemgr.dbrootdir=${java.io.tmpdir}/testcmgr/db",
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
    "distrib.cachemgr.restapi.accesstoken=SECRET",
    "cloud.aws.region=us-east-1"
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

    public CacheManagementControllerTest() {
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer SECRET");
    }

    public void cleanTestDir(File testdir) throws IOException, ConfigurationException {
        if (testdir.exists()) 
            FileSystemUtils.deleteRecursively(testdir);
        testdir.mkdirs();
        (new File(testdir,"db")).mkdirs();
        provider.cfg.theCache = null;
        HeadBagCacheManager hbcm = provider.createHeadBagManager();
        provider.createPDRCacheManager(hbcm);
    }

    @BeforeAll
    public static void setUpClass() throws IOException {
        String tmpdir = System.getProperty("java.io.tmpdir");
        if (tmpdir == null)
            throw new RuntimeException("java.io.tmpdir property not set");
        File tmp = new File(tmpdir);
        if (! tmp.exists())
            tmp.mkdir();
        testdir = new File(tmp, "testcmgr");
        testdir.mkdirs();
        (new File(testdir,"db")).mkdirs();
    }

    @AfterAll
    public static void tearDownClass() throws IOException {
        if (testdir.exists()) 
            FileSystemUtils.deleteRecursively(testdir);
    }

    @AfterEach
    public void tearDown() throws IOException, ConfigurationException {
        cleanTestDir(testdir);
    }

    @BeforeEach
    public void setUp() throws CacheManagementException, StorageVolumeException, ResourceNotFoundException, ConfigurationException {
        provider.getPDRCacheManager().cacheDataset("mds1491", null, true, 0 , null);
    }

    @Test
    public void testConfig() {
        assertTrue(provider.canCreateManager());
        File db = new File(testdir, "db/data.sqlite");
        assertTrue(db.isFile());
    }

    @Test
    public void testGetStatus() {
        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/cache/\"," +
                                 "status:200,message:\"Cache Manager in Use\",method:GET}",
                                resp.getBody(), true);
    }

    @Test
    public void testSummarizeVolumes() {
        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/volumes/", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JSONArray summary = new JSONArray(new JSONTokener(resp.getBody()));
        assertEquals(2, summary.length());
    }

    @Test
    public void testSummarizeVolume() {
        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/volumes/goober", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());

        resp = websvc.exchange(getBaseURL() + "/cache/volumes/king", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JSONObject summary = new JSONObject(new JSONTokener(resp.getBody()));
        assertEquals(3, summary.getInt("filecount"));
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
        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/objects/", HttpMethod.GET, req, String.class);
        JSONArray summary = new JSONArray(new JSONTokener(resp.getBody()));
        assertEquals(1, summary.length());
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", summary.getJSONObject(0).optString("aipid", null));
        assertEquals(3, summary.getJSONObject(0).optInt("filecount", 0));
    }

    @Test
    public void testSummarizeDataset() {
        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/objects/goober", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());

        resp = websvc.exchange(getBaseURL() + "/cache/objects/mds1491", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JSONObject summary = new JSONObject(new JSONTokener(resp.getBody()));
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", summary.optString("aipid", null));
        assertEquals(3, summary.getJSONArray("files").length());
    }

    @Test
    public void testListObjectsFor2() {
        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/objects/goober/:checked", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());

        resp = websvc.exchange(getBaseURL() + "/cache/objects/mds1491/:files", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JSONArray summary = new JSONArray(new JSONTokener(resp.getBody()));
        assertEquals(3, summary.length());
    }

    @Test
    public void testListObjectsFor3() {
        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/objects/goober/gurn/1", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());

        resp = websvc.exchange(getBaseURL() + "/cache/objects/mds1491/trial1.json/:files", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JSONArray summary = new JSONArray(new JSONTokener(resp.getBody()));
        assertEquals(1, summary.length());
        assertEquals("mds1491/trial1.json", summary.getJSONObject(0).getString("name"));

        resp = websvc.exchange(getBaseURL() + "/cache/objects/mds1491/trial1.json", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JSONObject file = new JSONObject(new JSONTokener(resp.getBody()));
        assertEquals("mds1491/trial1.json", file.getString("name"));
    }

    @Test
    public void testCacheDataset() {
        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/objects/goober/gurn/:cached", 
                                                      HttpMethod.PUT, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());

        resp = websvc.exchange(getBaseURL() + "/cache/objects/67C783D4BA814C8EE05324570681708A1899/:cached", 
                               HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());

        // cache this dataset
        resp = websvc.exchange(getBaseURL() + "/cache/objects/67C783D4BA814C8EE05324570681708A1899/:cached", 
                               HttpMethod.PUT, req, String.class);
        assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());

        for(int i=0; i < 10; i++) {
            try { Thread.sleep(200); } catch (InterruptedException ex) { }
            resp = websvc.exchange(getBaseURL() +
                                "/cache/objects/67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf", 
                                   HttpMethod.GET, req, String.class);
            if (! HttpStatus.NOT_FOUND.equals(resp.getStatusCode())) break;
        }
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        
        JSONObject file = new JSONObject(new JSONTokener(resp.getBody()));
        assertEquals("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf", file.getString("name"));

        resp = websvc.exchange(getBaseURL() + "/cache/objects/mds1491/trial1.json", 
                               HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        file = new JSONObject(new JSONTokener(resp.getBody()));
        assertEquals("mds1491/trial1.json", file.getString("name"));
        long since = file.optLong("since", 0L);
        assertTrue(since > 0L);

        resp = websvc.exchange(getBaseURL() + "/cache/objects/mds1491/:cached?recache=false", 
                               HttpMethod.PUT, req, String.class);
        assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());
        try { Thread.sleep(200); } catch (InterruptedException ex) { }

        resp = websvc.exchange(getBaseURL() + "/cache/objects/mds1491/trial1.json", 
                               HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        file = new JSONObject(new JSONTokener(resp.getBody()));
        assertEquals("mds1491/trial1.json", file.getString("name"));
        assertEquals(since, file.optLong("since", 0L));

        resp = websvc.exchange(getBaseURL() + "/cache/objects/mds1491/:cached?recache=true", 
                               HttpMethod.PUT, req, String.class);
        assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());

        for(int i=0; i < 10; i++) {
            try { Thread.sleep(200); } catch (InterruptedException ex) { }
            resp = websvc.exchange(getBaseURL() + "/cache/objects/mds1491/trial1.json", 
                                   HttpMethod.GET, req, String.class);
            if (! HttpStatus.OK.equals(resp.getStatusCode())) break;
            file = new JSONObject(new JSONTokener(resp.getBody()));
            if (since < file.optLong("since", 0L)) break;
        }
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        file = new JSONObject(new JSONTokener(resp.getBody()));
        assertEquals("mds1491/trial1.json", file.getString("name"));
        assertTrue( since < file.optLong("since", 0L), "Cache object's since date is too old: " + 
                    file.optLong("since", 0L) + " <= " + since);
    }

    @Test
    public void testRunMonitor() throws ConfigurationException {
        JSONObject status = null;
        ResponseEntity<String> resp = null;
        HttpEntity<String> req = new HttpEntity<>(null, headers);

        resp = websvc.exchange(getBaseURL() + "/cache/monitor/", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        status = new JSONObject(new JSONTokener(resp.getBody()));
        assertEquals(0L, status.getLong("lastRan"));
        assertEquals("(never)", status.getString("lastRanDate"));
        assertFalse(status.getBoolean("running"), "Monitor started on its own");

        resp = websvc.exchange(getBaseURL() + "/cache/monitor/running", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());

        PDRCacheManager mgr = provider.getPDRCacheManager();
        PDRCacheManager.MonitorThread month = mgr.getMonitorThread();
        resp = websvc.exchange(getBaseURL() + "/cache/monitor/running", HttpMethod.PUT, req, String.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertFalse(month.isContinuous());
        try { month.join(5000); } catch (InterruptedException ex) {  }
        
        resp = websvc.exchange(getBaseURL() + "/cache/monitor/running", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());

        resp = websvc.exchange(getBaseURL() + "/cache/monitor/", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        status = new JSONObject(new JSONTokener(resp.getBody()));
        assertTrue(0L < status.getLong("lastRan"));
        assertNotEquals("(never)", status.getString("lastRanDate"));
        assertFalse(status.getBoolean("running"), "Monitor failed to finish?");
    }

    @Test
    public void testStartMonitor() throws ConfigurationException {
        JSONObject status = null;
        ResponseEntity<String> resp = null;
        HttpEntity<String> req = new HttpEntity<String>(null, headers);

        resp = websvc.exchange(getBaseURL() + "/cache/monitor/", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        status = new JSONObject(new JSONTokener(resp.getBody()));
        assertEquals(0L, status.getLong("lastRan"));
        assertEquals("(never)", status.getString("lastRanDate"));
        assertFalse(status.getBoolean("running"), "Monitor started on its own");

        resp = websvc.exchange(getBaseURL() + "/cache/monitor/running", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());

        PDRCacheManager mgr = provider.getPDRCacheManager();
        PDRCacheManager.MonitorThread month = mgr.getMonitorThread();
        resp = websvc.exchange(getBaseURL() + "/cache/monitor/running?repeat=1", HttpMethod.PUT, req, String.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertTrue(month.isContinuous());
        assertTrue(month.isAlive());
        
        resp = websvc.exchange(getBaseURL() + "/cache/monitor/running", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        month = mgr.getMonitorThread();
        resp = websvc.exchange(getBaseURL() + "/cache/monitor/running?repeat=0", HttpMethod.PUT, req, String.class);
        assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());
        assertFalse(month.isContinuous());
        try { month.join(5000); } catch (InterruptedException ex) {  }
        assertFalse(month.isAlive());

        resp = websvc.exchange(getBaseURL() + "/cache/monitor/running", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());

        resp = websvc.exchange(getBaseURL() + "/cache/monitor/", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        status = new JSONObject(new JSONTokener(resp.getBody()));
        assertTrue(0L < status.getLong("lastRan"));
        assertNotEquals("(never)", status.getString("lastRanDate"));
        assertFalse(status.getBoolean("running"), "Monitor failed to finish?");
    }


    @Test
    public void testRemoveFromCache() {
        HttpEntity<String> req = new HttpEntity<>(null, headers);

        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/cache/objects/mds1491/:cached",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        ResponseEntity<String> removeResp = websvc.exchange(getBaseURL() +
                        "/cache/objects/mds1491/trial2.json/:cached",
                HttpMethod.DELETE, req, String.class);
        assertEquals(HttpStatus.OK, removeResp.getStatusCode());

        resp = websvc.exchange(getBaseURL() + "/cache/objects/mds1491/trial2.json/:cached",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());

        removeResp = websvc.exchange(getBaseURL() +
                        "/cache/objects/mds1491/:cached",
                HttpMethod.DELETE, req, String.class);
        assertEquals(HttpStatus.OK, removeResp.getStatusCode());

        resp = websvc.exchange(getBaseURL() + "/cache/objects/mds1491/:cached",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }


    private String getBaseURL() {
        return "http://localhost:" + port + "/od";
    }
}