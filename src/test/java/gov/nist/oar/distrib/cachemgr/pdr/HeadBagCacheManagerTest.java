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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.simple.SimpleCache;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

public class HeadBagCacheManagerTest {

    @TempDir
    public File tempDir;

    HeadBagDB sidb = null;
    HeadBagCacheManager hbcmgr = null;
    final String ltsdir = System.getProperty("project.test.resourceDirectory");

    String createDB() throws IOException, InventoryException {
        File tf = new File(tempDir, "testdb.sqlite");
        String out = tf.getAbsolutePath();
        HeadBagDB.initializeSQLiteDB(out);
        return out;
    }

    @BeforeEach
    public void setUp() throws IOException, InventoryException {
        BagStorage ltstore = new FilesystemLongTermStorage(ltsdir);

        File dbf = new File(createDB());
        sidb = HeadBagDB.createHeadBagDB(dbf.getPath());
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("cv0", 200000, null);
        sidb.registerVolume("cv1", 200000, null);

        File cdir = new File(tempDir, "cache");
        cdir.mkdir();
        List<CacheVolume> cvs = new ArrayList<CacheVolume>();
        File cvd = new File(cdir, "cv0");
        cvd.mkdir();
        cvs.add(new FilesystemCacheVolume(cvd, "cv0"));
        cvd = new File(cdir, "cv1");
        cvd.mkdir();
        cvs.add(new FilesystemCacheVolume(cvd, "cv1"));

        SimpleCache cache = new SimpleCache("simple", sidb, cvs);
        hbcmgr = new HeadBagCacheManager(cache, sidb, new HeadBagRestorer(ltstore), "88434");
    }

    @Test
    public void testCtor() throws IOException, CacheManagementException {
        Map<String, Long> avail = sidb.getAvailableSpace();
        assertEquals(2, avail.size());
        for (Long space : avail.values()) {
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
    public void testResolveAIPID() throws ResourceNotFoundException, CacheManagementException {
        assertFalse(hbcmgr.isCached("mds1491.1_1_0.mbag0_4-1.zip"));
        assertFalse(hbcmgr.isCached("mds1491.mbag0_2-0.zip"));

        JSONObject resmd = hbcmgr.resolveAIPID("mds1491", null);
        assertNotNull(resmd);
        assertEquals("ark:/88434/edi00hw91c", resmd.optString("@id"));
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", resmd.optString("ediid"));
        assertEquals(8, resmd.optJSONArray("components").length());
        assertTrue(hbcmgr.isCached("mds1491.1_1_0.mbag0_4-1.zip"));
        assertFalse(hbcmgr.isCached("mds1491.mbag0_2-0.zip"));

        resmd = hbcmgr.resolveAIPID("mds1491", "0");
        assertNotNull(resmd);
        assertEquals("ark:/88434/edi00hw91c", resmd.optString("@id"));
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", resmd.optString("ediid"));
        assertEquals(5, resmd.optJSONArray("components").length());
        assertTrue(hbcmgr.isCached("mds1491.1_1_0.mbag0_4-1.zip"));
        assertTrue(hbcmgr.isCached("mds1491.mbag0_2-0.zip"));

        assertFalse(hbcmgr.isCached("67C783D4BA814C8EE05324570681708A1899.mbag0_3-1.zip"));
        resmd = hbcmgr.resolveAIPID("67C783D4BA814C8EE05324570681708A1899", null);
        assertNotNull(resmd);
        assertTrue(hbcmgr.isCached("67C783D4BA814C8EE05324570681708A1899.mbag0_3-1.zip"));

        try {
            hbcmgr.resolveAIPID("mds1492", null);
            fail("Found non-existent AIP");
        } catch (ResourceNotFoundException ex) { /* success! */ }
    }

    @Test
    public void testFindComponentByFilepath() throws ResourceNotFoundException, CacheManagementException {
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
    public void testResolveDistID() throws ResourceNotFoundException, CacheManagementException, FileNotFoundException {
        assertFalse(hbcmgr.isCached("mds1491.1_1_0.mbag0_4-1.zip"));
        assertFalse(hbcmgr.isCached("mds1491.mbag0_2-0.zip"));

        JSONObject cmpmd = hbcmgr.resolveDistID("ark:/88434/mds1491/trial1.json", null);
        assertNotNull(cmpmd);
        assertEquals("cmps/trial1.json", cmpmd.optString("@id"));
        assertEquals("trial1.json", cmpmd.optString("filepath"));
        assertTrue(hbcmgr.isCached("mds1491.1_1_0.mbag0_4-1.zip"));
        assertFalse(hbcmgr.isCached("mds1491.mbag0_2-0.zip"));

        cmpmd = hbcmgr.resolveDistID("mds1491/trial1.json", "0");
        assertNotNull(cmpmd);
        assertEquals("cmps/trial1.json", cmpmd.optString("@id"));
        assertEquals("trial1.json", cmpmd.optString("filepath"));

        try {
            hbcmgr.resolveDistID("mds1491/goober", "0");
            fail("Found non-existent filepath");
        } catch (FileNotFoundException ex) { /* success! */ }
    }
}
