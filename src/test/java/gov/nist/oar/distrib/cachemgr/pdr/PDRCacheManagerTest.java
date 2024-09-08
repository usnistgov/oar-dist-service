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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.ConfigurableCache;
import gov.nist.oar.distrib.cachemgr.VolumeConfig;
import gov.nist.oar.distrib.cachemgr.VolumeNotFoundException;
import gov.nist.oar.distrib.cachemgr.VolumeStatus;
import gov.nist.oar.distrib.cachemgr.inventory.ChecksumCheck;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

public class PDRCacheManagerTest {

    @TempDir
    public File tempDir;

    final String ltsdir = System.getProperty("project.test.resourceDirectory");
    PDRDatasetRestorer rstr = null;
    ConfigurableCache cache = null;
    PDRCacheManager mgr = null;

    HeadBagCacheManager createHBCache(BagStorage ltstore) throws IOException, CacheManagementException {
        File tf = new File(tempDir, "headbags");
        tf.mkdir();
        File dbf = new File(tf, "inventory.sqlite");
        HeadBagDB.initializeSQLiteDB(dbf.getAbsolutePath());
        HeadBagDB sidb = HeadBagDB.createHeadBagDB(dbf.getAbsolutePath());
        sidb.registerAlgorithm("sha256");
        ConfigurableCache cache = new ConfigurableCache("headbags", sidb, 2, null);

        File cvd = new File(tf, "cv0");
        cvd.mkdir();
        cache.addCacheVolume(new FilesystemCacheVolume(cvd, "cv0"), 2000000, null, true);
        cvd = new File(tf, "cv1");
        cvd.mkdir();
        cache.addCacheVolume(new FilesystemCacheVolume(cvd, "cv1"), 2000000, null, true);

        return new HeadBagCacheManager(cache, sidb, new HeadBagRestorer(ltstore), "88434");
    }

    ConfigurableCache createDataCache(File croot) throws CacheManagementException, IOException {
        File dbf = new File(croot, "inventory.sqlite");
        PDRStorageInventoryDB.initializeSQLiteDB(dbf.getAbsolutePath());
        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
        sidb.registerAlgorithm("sha256");
        ConfigurableCache cache = new ConfigurableCache("headbags", sidb, 2, null);

        File cvdir = new File(croot, "foobar");
        cvdir.mkdir();
        VolumeConfig vc = new VolumeConfig();
        CacheVolume cv = new FilesystemCacheVolume(cvdir, "foobar");
        vc.setRoles(PDRCacheRoles.ROLE_SMALL_OBJECTS | PDRCacheRoles.ROLE_FAST_ACCESS);
        cache.addCacheVolume(cv, 2000000, null, vc, true);

        cvdir = new File(croot, "cranky");
        cvdir.mkdir();
        cv = new FilesystemCacheVolume(cvdir, "cranky");
        vc.setRoles(PDRCacheRoles.ROLE_GENERAL_PURPOSE | PDRCacheRoles.ROLE_LARGE_OBJECTS);
        cache.addCacheVolume(cv, 2000000, null, vc, true);

        cvdir = new File(croot, "old");
        cvdir.mkdir();
        cv = new FilesystemCacheVolume(cvdir, "old");
        vc.setRoles(PDRCacheRoles.ROLE_OLD_VERSIONS);
        cache.addCacheVolume(cv, 2000000, null, vc, true);

        return cache;
    }

    @BeforeEach
    public void setUp() throws IOException, CacheManagementException {
        File croot = new File(tempDir, "data");
        croot.mkdir();
        BagStorage ltstore = new FilesystemLongTermStorage(ltsdir);
        HeadBagCacheManager hbcm = createHBCache(ltstore);

        cache = createDataCache(croot);
        rstr = new PDRDatasetRestorer(ltstore, hbcm, 500);

        List<CacheObjectCheck> checks = new ArrayList<>();
        checks.add(new ChecksumCheck());
        mgr = new PDRCacheManager(cache, rstr, checks, 5000, -1, -1, croot, null);
    }

    @Test
    public void testCtor() throws CacheManagementException {
        Assertions.assertFalse(mgr.isCached("mds1491/trial1.json"));
        Assertions.assertNull(mgr.findObject("mds1491/trial1.json"));
        mgr.uncache("mds1491/trial1.json");
    }

    @Test
    public void testCacheDataset() 
            throws StorageVolumeException, ResourceNotFoundException, CacheManagementException {
        Assertions.assertFalse(mgr.isCached("mds1491/trial1.json"));
        Assertions.assertFalse(mgr.isCached("mds1491/trial2.json"));
        Assertions.assertFalse(mgr.isCached("mds1491/trial3/trial3a.json"));
        mgr.cacheDataset("mds1491", null, true, 0, null);
        Assertions.assertTrue(mgr.isCached("mds1491/trial1.json"));
        Assertions.assertTrue(mgr.isCached("mds1491/trial2.json"));
        Assertions.assertTrue(mgr.isCached("mds1491/trial3/trial3a.json"));

        // test recache=false
        CacheObject co = mgr.findObject("mds1491/trial2.json");
        Assertions.assertNotNull(co);
        long since = co.getMetadatumLong("since", 0L);
        Assertions.assertTrue(since > 0L, "Missing since metadatum");
        mgr.uncache("mds1491/trial1.json");
        Assertions.assertFalse(mgr.isCached("mds1491/trial1.json"));
        Assertions.assertTrue(mgr.isCached("mds1491/trial2.json"));
        mgr.cacheDataset("mds1491", null, false, 0, null);
        Assertions.assertTrue(mgr.isCached("mds1491/trial1.json"));
        Assertions.assertTrue(mgr.isCached("mds1491/trial2.json"));
        co = mgr.findObject("mds1491/trial2.json");
        Assertions.assertNotNull(co);
        Assertions.assertEquals(since, co.getMetadatumLong("since", 0L), "File appears to have been recached:");
    }

    public void cacheAllTestData()
            throws StorageVolumeException, ResourceNotFoundException, CacheManagementException {
        mgr.cacheDataset("mds1491", null, true, 0, null);
        mgr.cacheDataset("mds1491", "1", true, 0, null);
        mgr.cacheDataset("mds1491", "1.1.0", true, 0, null);
        mgr.cacheDataset("67C783D4BA814C8EE05324570681708A1899", null, true, 0, null);
    }

    @Test
    public void testSummarizeContents() 
            throws StorageVolumeException, ResourceNotFoundException, CacheManagementException {
        cacheAllTestData();
        mgr.check("mds1491", false);
        
        JSONArray data = mgr.summarizeContents(null);
        Assertions.assertEquals(2, data.length());
        Assertions.assertEquals("67C783D4BA814C8EE05324570681708A1899", data.getJSONObject(0).getString("aipid"));
        Assertions.assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", data.getJSONObject(1).getString("aipid"));
        Assertions.assertTrue(data.getJSONObject(0).optLong("checked", -1L) <
                   data.getJSONObject(1).optLong("checked", -1L));
        Assertions.assertEquals("(never)", data.getJSONObject(0).getString("checkedDate"));
        Assertions.assertNotEquals("", data.getJSONObject(1).getString("checkedDate"));
        Assertions.assertNotEquals("(never)", data.getJSONObject(1).getString("checkedDate"));

        data = mgr.summarizeContents("old");
        Assertions.assertEquals(1, data.length());
        Assertions.assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", data.getJSONObject(0).get("aipid"));
    }

    @Test
    public void testMonitorUntilDone()
            throws StorageVolumeException, ResourceNotFoundException,
                   CacheManagementException, InterruptedException {
        PDRCacheManager.MonitorThread thrd = mgr.getMonitorThread();
        List<CacheObject> prob = new ArrayList<>();
        int count = thrd.monitorUntilDone(prob, 100, 100);
        Assertions.assertEquals(0, count);

        cacheAllTestData();
        count = thrd.monitorUntilDone(prob, 100, 100);
        Assertions.assertEquals(14, count);
        Assertions.assertEquals(0, prob.size());
    }

    @Test
    public void testMonitorRunOnce()
            throws StorageVolumeException, ResourceNotFoundException, CacheManagementException {
        cacheAllTestData();

        PDRCacheManager.MonitorThread thrd = mgr.getMonitorThread();
        thrd.setCycling(20000, -1L, -1L);
        thrd.setContinuous(false);
        thrd.run();
    }

    @Test
    public void testMonitorRun()
            throws StorageVolumeException, ResourceNotFoundException, CacheManagementException {
        cacheAllTestData();

        PDRCacheManager.MonitorThread thrd = mgr.getMonitorThread();
        thrd.setCycling(500, -1L, -1L);
        thrd.start();
        try { Thread.sleep(3000); }
        catch (InterruptedException ex) { }
        thrd.interruptAndWait();
    }

    @Test
    public void testGetRolesFor() {
        Assertions.assertEquals(0, mgr.getRolesFor("goober"));
        Assertions.assertEquals(16, mgr.getRolesFor("old"));
        Assertions.assertEquals(9, mgr.getRolesFor("cranky"));
        Assertions.assertEquals(6, mgr.getRolesFor("foobar"));
    }

    @Test
    public void testSummarizeVolumes()
            throws StorageVolumeException, ResourceNotFoundException, CacheManagementException {
        try {
            mgr.summarizeVolume("goob");
            Assertions.fail("Failed to throw VolumeNotFoundException on unknown volume");
        } catch (VolumeNotFoundException ex) {
            // success
        }

        JSONObject info = mgr.summarizeVolume("old");
        Assertions.assertEquals("old", info.getString("name"));
        Assertions.assertEquals(16, info.getInt("roles"));
        Assertions.assertEquals(2000000, info.getLong("capacity"));
        Assertions.assertEquals(0, info.getLong("filecount"));
        Assertions.assertEquals(0, info.getLong("totalsize"));
        Assertions.assertEquals(0, info.getLong("since"));
        Assertions.assertEquals(0, info.getLong("checked"));
        Assertions.assertEquals("(never)", info.getString("checkedDate"));

        JSONArray vols = mgr.summarizeVolumes();
        Assertions.assertEquals(3, vols.length());

        info = vols.toList().stream()
                .map(v -> new JSONObject((Map<String, Object>) v))
                .filter(v -> "old".equals(v.getString("name")))
                .findFirst()
                .orElse(null);
        Assertions.assertNotNull(info);
        Assertions.assertEquals("old", info.getString("name"));
        Assertions.assertEquals(16, info.getInt("roles"));

        info = vols.toList().stream()
                .map(v -> new JSONObject((Map<String, Object>) v))
                .filter(v -> "cranky".equals(v.getString("name")))
                .findFirst()
                .orElse(null);
        Assertions.assertNotNull(info);
        Assertions.assertEquals("cranky", info.getString("name"));

        info = vols.toList().stream()
                .map(v -> new JSONObject((Map<String, Object>) v))
                .filter(v -> "foobar".equals(v.getString("name")))
                .findFirst()
                .orElse(null);
        Assertions.assertNotNull(info);
        Assertions.assertEquals("foobar", info.getString("name"));
    }

    @Test
    public void testDescribeObject()
            throws StorageVolumeException, ResourceNotFoundException, CacheManagementException {
        cacheAllTestData();

        CacheObject co = mgr.describeObject("mds1491", "trial1.json", VolumeStatus.VOL_FOR_GET);
        Assertions.assertNotNull(co);
        Assertions.assertEquals("mds1491/trial1.json", co.id);
        Assertions.assertEquals("foobar", co.volname);
        Assertions.assertTrue(co.cached);

        mgr.uncache("mds1491/trial1.json");
        co = mgr.describeObject("mds1491", "trial1.json", VolumeStatus.VOL_FOR_GET);
        Assertions.assertNull(co);

        co = mgr.describeObject("mds1491", "trial1.json", VolumeStatus.VOL_FOR_INFO);
        Assertions.assertNotNull(co);
        Assertions.assertEquals("mds1491/trial1.json", co.id);
        Assertions.assertEquals("foobar", co.volname);
        Assertions.assertFalse(co.cached);
    }

    @Test
    public void testSelectDatasetObjects()
            throws StorageVolumeException, ResourceNotFoundException, CacheManagementException {
        cacheAllTestData();

        List<CacheObject> cos = mgr.selectDatasetObjects("mds1491", VolumeStatus.VOL_FOR_GET);
        Assertions.assertEquals(9, cos.size());

        mgr.uncache("mds1491/trial1.json");
        cos = mgr.selectDatasetObjects("mds1491", VolumeStatus.VOL_FOR_GET);
        Assertions.assertEquals(8, cos.size());
        cos = mgr.selectDatasetObjects("mds1491", VolumeStatus.VOL_FOR_INFO);
        Assertions.assertEquals(9, cos.size());
    }

    @Test
    public void testSummarizeDataset()
            throws StorageVolumeException, ResourceNotFoundException, CacheManagementException {
        cacheAllTestData();

        JSONObject info = mgr.summarizeDataset("mds1491");
        Assertions.assertNotNull(info);
        Assertions.assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", info.get("aipid"));
        Assertions.assertEquals(3, info.getInt("filecount"));
        Assertions.assertEquals(691, info.getInt("totalsize"));
        Assertions.assertTrue(0 < info.optLong("since"));
        Assertions.assertEquals(0, info.optLong("checked"));

        mgr.check("mds1491", false);
        info = mgr.summarizeDataset("mds1491");
        Assertions.assertNotNull(info);
        Assertions.assertTrue(0 < info.optLong("checked"));
    }

    @Test
    public void testCacheQueue() throws CacheManagementException, IOException {
        Assertions.assertNotNull(mgr.cath);
        Assertions.assertFalse(mgr.cath.hasPending());
        Queue<String> q = mgr.cath.loadQueue();
        Assertions.assertEquals(0, q.size());
        Assertions.assertFalse(mgr.cath.isQueued("mds2-1111"));
        q.add("mds2-1111\t0");
        mgr.cath.saveQueue(q);
        Assertions.assertTrue(mgr.cath.hasPending());
        Assertions.assertTrue(mgr.cath.isQueued("mds2-1111"));
        mgr.cath.queue("mds2-2222", false);
        mgr.cath.queue("mds2-3333", true);
        Assertions.assertTrue(mgr.cath.hasPending());
        Assertions.assertTrue(mgr.cath.isQueued("mds2-1111"));
        Assertions.assertTrue(mgr.cath.isQueued("mds2-2222"));
        Assertions.assertTrue(mgr.cath.isQueued("mds2-3333"));
        q = mgr.cath.loadQueue();
        Assertions.assertEquals(3, q.size());
        Assertions.assertEquals("mds2-1111\t0", mgr.cath.popQueue());
        Assertions.assertEquals("mds2-2222\t0", mgr.cath.popQueue());
        Assertions.assertEquals("mds2-3333\t1", mgr.cath.popQueue());
        Assertions.assertFalse(mgr.cath.hasPending());
    }

    @Test
    public void testCachingStatus() throws CacheManagementException {
        Assertions.assertFalse(mgr.isCaching());
        Assertions.assertNull(mgr.getCachingItemName());
        JSONObject status = mgr.getCachingQueueStatus();
        Assertions.assertEquals("not running", status.getString("status"));
        Assertions.assertEquals(JSONObject.NULL, status.get("current"));
        JSONArray waiting = status.getJSONArray("waiting");
        Assertions.assertEquals(0, waiting.length());
    }
}

