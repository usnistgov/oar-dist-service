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
 * 
 * @author: Raymond Plante
 */
package gov.nist.oar.distrib.cachemgr.pdr;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import gov.nist.oar.clients.OARServiceException;
import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.Cache;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.cachemgr.simple.SimpleCache;
import gov.nist.oar.distrib.cachemgr.BasicCacheManager;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.restore.FileCopyRestorer;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.io.File;

public class HeadBagCacheManagerTest {

    @Rule
    public final TemporaryFolder tempf = new TemporaryFolder();

    HeadBagDB sidb = null;
    HeadBagCacheManager hbcmgr = null;
    final String ltsdir = System.getProperty("project.test.resourceDirectory");

    String createDB() throws IOException, InventoryException {
        File tf = tempf.newFile("testdb.sqlite");
        String out = tf.getAbsolutePath();
        HeadBagDB.initializeSQLiteDB(out);
        return out;
    }

    @Before
    public void setUp() throws IOException, InventoryException {
        BagStorage ltstore = new FilesystemLongTermStorage(ltsdir);

        File dbf = new File(createDB());
        sidb = HeadBagDB.createHeadBagDB(dbf.getPath());
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("cv0", 200000, null);
        sidb.registerVolume("cv1", 200000, null);

        File cdir = tempf.newFolder("cache");
        List<CacheVolume> cvs = new ArrayList<CacheVolume>();
        File cvd = new File(cdir, "cv0");  cvd.mkdir();
        cvs.add(new FilesystemCacheVolume(cvd, "cv0"));
        cvd = new File(cdir, "cv1");  cvd.mkdir();
        cvs.add(new FilesystemCacheVolume(cvd, "cv1"));

        SimpleCache cache = new SimpleCache("simple", sidb, cvs);
        hbcmgr = new HeadBagCacheManager(cache, sidb, ltstore, new FileCopyRestorer(ltstore), "88434");
    }

    @Test
    public void testCtor() throws IOException, CacheManagementException {
        Map<String, Long> avail = sidb.getAvailableSpace();
        assertEquals(2, avail.size());
        for(Long space : avail.values()) {
            assertEquals(200000L, space.longValue());
        }
        assertFalse(hbcmgr.isCached("mds1491.mbag0_2-0.zip"));
        hbcmgr.uncache("mds1491.mbag0_2-0.zip");
    }

    @Test
    public void testGetObject() throws CacheManagementException, StorageVolumeException {
        CacheObject co = hbcmgr.getObject("mds1491.mbag0_2-0.zip");
        assertNotNull(co);
        assertNotNull(co.volume);
        assertTrue(co.volume.exists("mds1491.mbag0_2-0.zip"));
        assertTrue(hbcmgr.isCached("mds1491.mbag0_2-0.zip"));
    }

    @Test
    public void testResolveAIPID() throws OARServiceException, CacheManagementException {
        assertTrue(! hbcmgr.isCached("mds1491.1_1_0.mbag0_4-1.zip"));
        assertTrue(! hbcmgr.isCached("mds1491.mbag0_2-0.zip"));

        JSONObject resmd = hbcmgr.resolveAIPID("mds1491", null);
        assertNotNull(resmd);
        assertEquals("ark:/88434/edi00hw91c", resmd.optString("@id"));
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", resmd.optString("ediid"));
        assertEquals(8, resmd.optJSONArray("components").length());
        assertTrue(hbcmgr.isCached("mds1491.1_1_0.mbag0_4-1.zip"));
        assertTrue(! hbcmgr.isCached("mds1491.mbag0_2-0.zip"));

        resmd = hbcmgr.resolveAIPID("mds1491", "0");
        assertNotNull(resmd);
        assertEquals("ark:/88434/edi00hw91c", resmd.optString("@id"));
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", resmd.optString("ediid"));
        assertEquals(8, resmd.optJSONArray("components").length());        
        assertTrue(hbcmgr.isCached("mds1491.1_1_0.mbag0_4-1.zip"));
        assertTrue(hbcmgr.isCached("mds1491.mbag0_2-0.zip"));

        assertTrue(! hbcmgr.isCached("67C783D4BA814C8EE05324570681708A1899.mbag0_3-1.zip"));
        resmd = hbcmgr.resolveAIPID("67C783D4BA814C8EE05324570681708A1899", null);
        assertNotNull(resmd);
        assertTrue(hbcmgr.isCached("67C783D4BA814C8EE05324570681708A1899.mbag0_3-1.zip"));

        resmd = hbcmgr.resolveAIPID("mds1492", null);
        assertNull(resmd);
    }

    /*
    @Test
    public void testResolveResourceID() throws OARServiceException {
        JSONObject resmd = hbcmgr.resolveResourceID("ark:/88434/mds1491");
        assertNotNull(resmd);
        assertEquals("ark:/88434/edi00hw91c", resmd.optString("@id"));
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", resmd.optString("ediid"));
        assertEquals(8, resmd.optJSONArray("components").length());
    }

    @Test
    public void testResolveEDIID() throws OARServiceException {
        JSONObject resmd = hbcmgr.resolveEDIID("67C783D4BA814C8EE05324570681708A1899/goob");
        assertNotNull(resmd);
        assertEquals("ark:/88434/mds107pcw08s", resmd.optString("@id"));
        assertEquals("67C783D4BA814C8EE05324570681708A1899", resmd.optString("ediid"));
        assertEquals(5, resmd.optJSONArray("components").length());
    }

    @Test
    public void testResolveComponentID() throws OARServiceException {
        JSONObject cmpmd = hbcmgr.resolveComponentID("ark:/88434/mds1491/cmps/trial1.json");
        assertNotNull(cmpmd);
        assertEquals("cmps/trial1.json", cmpmd.optString("@id"));
        assertEquals("trial1.json", cmpmd.optString("filepath"));
    }
    */

    @Test
    public void testFindComponentByFilepath() throws OARServiceException {
        JSONObject resmd = hbcmgr.resolveAIPID("mds1491", null);
        assertNotNull(resmd);
        JSONObject md = HeadBagCacheManager.findComponentByFilepath(resmd, "trial1.json");
        assertEquals("cmps/trial1.json", md.optString("@id"));
        assertEquals("trial1.json", md.optString("filepath"));
        md = HeadBagCacheManager.findComponentByFilepath(resmd, "trial3");
        assertEquals("cmps/trial3", md.optString("@id"));
        assertEquals("trial3", md.optString("filepath"));
        md = HeadBagCacheManager.findComponentByFilepath(resmd, "trial3/trial3a.json");
        assertEquals("cmps/trial3/trial3a.json", md.optString("@id"));
        assertEquals("trial3/trial3a.json", md.optString("filepath"));
    }

    @Test
    public void testResolveDistID() throws OARServiceException, CacheManagementException {
        assertTrue(! hbcmgr.isCached("mds1491.1_1_0.mbag0_4-1.zip"));
        assertTrue(! hbcmgr.isCached("mds1491.mbag0_2-0.zip"));

        JSONObject cmpmd = hbcmgr.resolveDistID("ark:/88434/mds1491/trial1.json", null);
        assertNotNull(cmpmd);
        assertEquals("cmps/trial1.json", cmpmd.optString("@id"));
        assertEquals("trial1.json", cmpmd.optString("filepath"));
        assertTrue(hbcmgr.isCached("mds1491.1_1_0.mbag0_4-1.zip"));
        assertTrue(! hbcmgr.isCached("mds1491.mbag0_2-0.zip"));

        cmpmd = hbcmgr.resolveDistID("mds1491/trial1.json", "0");
        assertNotNull(cmpmd);
        assertEquals("cmps/trial1.json", cmpmd.optString("@id"));
        assertEquals("trial1.json", cmpmd.optString("filepath"));
    }
}
