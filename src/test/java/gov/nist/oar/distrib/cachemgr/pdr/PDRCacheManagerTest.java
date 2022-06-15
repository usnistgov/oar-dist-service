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

import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.cachemgr.ConfigurableCache;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.VolumeConfig;
import gov.nist.oar.distrib.cachemgr.Reservation;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.cachemgr.simple.SimpleCache;
import gov.nist.oar.distrib.cachemgr.BasicCacheManager;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.VolumeNotFoundException;
import gov.nist.oar.distrib.cachemgr.RestorationException;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.inventory.ChecksumCheck;
import gov.nist.oar.distrib.cachemgr.restore.FileCopyRestorer;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.stream.Collectors;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.File;

public class PDRCacheManagerTest {

    @Rule
    public final TemporaryFolder tempf = new TemporaryFolder();

    final String ltsdir = System.getProperty("project.test.resourceDirectory");
    PDRDatasetRestorer rstr = null;
    ConfigurableCache cache = null;
    PDRCacheManager mgr = null;

    HeadBagCacheManager createHBCache(BagStorage ltstore) throws IOException, CacheManagementException {
        File tf = tempf.newFolder("headbags");
        File dbf = new File(tf, "inventory.sqlite");
        HeadBagDB.initializeSQLiteDB(dbf.getAbsolutePath());
        HeadBagDB sidb = HeadBagDB.createHeadBagDB(dbf.getAbsolutePath());
        sidb.registerAlgorithm("sha256");
        ConfigurableCache cache = new ConfigurableCache("headbags", sidb, 2, null);

        File cvd = new File(tf, "cv0");  cvd.mkdir();
        cache.addCacheVolume(new FilesystemCacheVolume(cvd, "cv0"), 2000000, null, true);
        cvd = new File(tf, "cv1");  cvd.mkdir();
        cache.addCacheVolume(new FilesystemCacheVolume(cvd, "cv1"), 2000000, null, true);

        return new HeadBagCacheManager(cache, sidb, ltstore, new FileCopyRestorer(ltstore), "88434");
    }

    ConfigurableCache createDataCache(File croot) throws CacheManagementException, IOException {
        File dbf = new File(croot, "inventory.sqlite");
        PDRStorageInventoryDB.initializeSQLiteDB(dbf.getAbsolutePath());
        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
        sidb.registerAlgorithm("sha256");
        ConfigurableCache cache = new ConfigurableCache("headbags", sidb, 2, null);

        File cvdir = new File(croot, "foobar");  cvdir.mkdir();
        VolumeConfig vc = new VolumeConfig();
        CacheVolume cv = new FilesystemCacheVolume(cvdir, "foobar");
        vc.setRoles(PDRCacheRoles.ROLE_SMALL_OBJECTS|PDRCacheRoles.ROLE_FAST_ACCESS);
        cache.addCacheVolume(cv, 2000000, null, vc, true);

        cvdir = new File(croot, "cranky");  cvdir.mkdir();
        cv = new FilesystemCacheVolume(cvdir, "cranky");
        vc.setRoles(PDRCacheRoles.ROLE_GENERAL_PURPOSE|PDRCacheRoles.ROLE_LARGE_OBJECTS);
        cache.addCacheVolume(cv, 2000000, null, vc, true);

        cvdir = new File(croot, "old");  cvdir.mkdir();
        cv = new FilesystemCacheVolume(cvdir, "old");
        vc.setRoles(PDRCacheRoles.ROLE_OLD_VERSIONS);
        cache.addCacheVolume(cv, 2000000, null, vc, true);

        return cache;
    }

    @Before
    public void setUp() throws IOException, CacheManagementException {
        File croot = tempf.newFolder("data");
        BagStorage ltstore = new FilesystemLongTermStorage(ltsdir);
        HeadBagCacheManager hbcm = createHBCache(ltstore);

        cache = createDataCache(croot);
        rstr = new PDRDatasetRestorer(ltstore, hbcm, 500);

        List<CacheObjectCheck> checks = new ArrayList<CacheObjectCheck>();
        checks.add(new ChecksumCheck());
        mgr = new PDRCacheManager(cache, rstr, checks, 5000, -1, -1, croot, null);
    }

    @Test
    public void testCtor() throws CacheManagementException {
        assertFalse(mgr.isCached("mds1491/trial1.json"));
        assertNull(mgr.findObject("mds1491/trial1.json"));
        mgr.uncache("mds1491/trial1.json");
    }

    @Test
    public void testCacheDataset() 
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        assertTrue(! mgr.isCached("mds1491/trial1.json"));
        assertTrue(! mgr.isCached("mds1491/trial2.json"));
        assertTrue(! mgr.isCached("mds1491/trial3/trial3a.json"));
        mgr.cacheDataset("mds1491", null, true, 0, null);
        assertTrue(mgr.isCached("mds1491/trial1.json"));
        assertTrue(mgr.isCached("mds1491/trial2.json"));
        assertTrue(mgr.isCached("mds1491/trial3/trial3a.json"));

        // test recache=false
        CacheObject co = mgr.findObject("mds1491/trial2.json");
        assertNotNull(co);
        long since = co.getMetadatumLong("since", 0L);
        assertTrue("Missing since metadatum", since > 0L);
        mgr.uncache("mds1491/trial1.json");
        assertTrue(! mgr.isCached("mds1491/trial1.json"));
        assertTrue(mgr.isCached("mds1491/trial2.json"));
        mgr.cacheDataset("mds1491", null, false, 0, null);
        assertTrue(mgr.isCached("mds1491/trial1.json"));
        assertTrue(mgr.isCached("mds1491/trial2.json"));
        co = mgr.findObject("mds1491/trial2.json");
        assertNotNull(co);
        assertEquals("File appears to have been recached:", since, co.getMetadatumLong("since", 0L));
    }

    public void cacheAllTestData()
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        mgr.cacheDataset("mds1491", null, true, 0, null);
        mgr.cacheDataset("mds1491", "1", true, 0, null);
        mgr.cacheDataset("mds1491", "1.1.0", true, 0, null);
        mgr.cacheDataset("67C783D4BA814C8EE05324570681708A1899", null, true, 0, null);
    }

    @Test
    public void testSummarizeContents() 
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        cacheAllTestData();
        mgr.check("mds1491", false);
        
        JSONArray data = mgr.summarizeContents(null);
        assertEquals(2, data.length());
        assertEquals("67C783D4BA814C8EE05324570681708A1899", ((JSONObject) data.get(0)).optString("aipid",""));
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", ((JSONObject) data.get(1)).optString("aipid",""));
        assertTrue("Unexpected Checked dates",
                   ((JSONObject) data.get(0)).optLong("checked",-1L) <
                   ((JSONObject) data.get(1)).optLong("checked",-1L)   );
        assertEquals("(never)", ((JSONObject) data.get(0)).optString("checkedDate",""));
        assertNotEquals("",        ((JSONObject) data.get(1)).optString("checkedDate",""));
        assertNotEquals("(never)", ((JSONObject) data.get(1)).optString("checkedDate",""));

        data = mgr.summarizeContents("old");
        assertEquals(1, data.length());
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", data.getJSONObject(0).get("aipid"));
    }

    @Test
    public void testMonitorUntilDone()
        throws StorageVolumeException, ResourceNotFoundException,
               CacheManagementException, InterruptedException
    {
        PDRCacheManager.MonitorThread thrd = mgr.getMonitorThread();
        List<CacheObject> prob = new ArrayList<CacheObject>();
        int count = thrd.monitorUntilDone(prob, 100, 100);
        assertEquals(0, count);

        cacheAllTestData();
        count = thrd.monitorUntilDone(prob, 100, 100);
        assertEquals(14, count);
        assertEquals(0, prob.size());
    }

    @Test
    public void testMonitorRunOnce()
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        cacheAllTestData();

        PDRCacheManager.MonitorThread thrd = mgr.getMonitorThread();
        thrd.setCycling(20000, -1L, -1L);
        thrd.setContinuous(false);
        thrd.run();
    }

    @Test
    public void testMonitorRun()
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        cacheAllTestData();

        PDRCacheManager.MonitorThread thrd = mgr.getMonitorThread();
        thrd.setCycling(500, -1L, -1L);
        thrd.start();
        try { Thread.currentThread().sleep(3000); }
        catch (InterruptedException ex) { }
        thrd.interruptAndWait();
    }

    @Test
    public void testGetRolesFor() {
        assertEquals(0, mgr.getRolesFor("goober"));
        assertEquals(16, mgr.getRolesFor("old"));
        assertEquals(9, mgr.getRolesFor("cranky"));
        assertEquals(6, mgr.getRolesFor("foobar"));
    }

    @Test
    public void testSummarizeVolumes()
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        try {
            mgr.summarizeVolume("goob");
            fail("Failed to throw VolumeNotFoundException on unknown volume");
        }
        catch (VolumeNotFoundException ex) { /* successful */ }

        JSONObject info = mgr.summarizeVolume("old");
        assertEquals("old", info.opt("name"));
        assertEquals(16, info.optInt("roles"));
        assertEquals(2000000, info.optLong("capacity"));
        assertEquals(0, info.optLong("filecount"));
        assertEquals(0, info.optLong("totalsize"));
        assertEquals(0, info.optLong("since"));
        assertEquals(0, info.optLong("checked"));
        assertEquals("(never)", info.opt("checkedDate"));

        JSONArray vols = mgr.summarizeVolumes();
        assertEquals(3, vols.length());
        info = new JSONObject((Map<String,Object>)
                              vols.toList().stream()
                                  .filter(v -> "old".equals(((Map<String,Object>)v).get("name")))
                                  .collect(Collectors.toList()).get(0));
        assertEquals("old", info.opt("name"));
        assertEquals(16, info.optInt("roles"));
        assertEquals(2000000, info.optLong("capacity"));
        assertEquals(0, info.optLong("filecount"));
        assertEquals(0, info.optLong("totalsize"));
        assertEquals(0, info.optLong("since"));
        assertEquals(0, info.optLong("checked"));
        assertEquals("(never)", info.opt("checkedDate"));

        info = new JSONObject((Map<String,Object>)
                              vols.toList().stream()
                                  .filter(v -> "cranky".equals(((Map)v).get("name")))
                                  .collect(Collectors.toList()).get(0));
        assertEquals("cranky", info.opt("name"));
        assertEquals(9, info.optInt("roles"));
        assertEquals(2000000, info.optLong("capacity"));
        assertEquals(0, info.optLong("filecount"));
        assertEquals(0, info.optLong("totalsize"));
        assertEquals(0, info.optLong("since"));
        assertEquals(0, info.optLong("checked"));
        assertEquals("(never)", info.opt("checkedDate"));

        info = new JSONObject((Map<String,Object>)
                              vols.toList().stream()
                                  .filter(v -> "foobar".equals(((Map)v).get("name")))
                                  .collect(Collectors.toList()).get(0));
        assertEquals("foobar", info.opt("name"));
        assertEquals(6, info.optInt("roles"));
        assertEquals(2000000, info.optLong("capacity"));
        assertEquals(0, info.optLong("filecount"));
        assertEquals(0, info.optLong("totalsize"));
        assertEquals(0, info.optLong("since"));
        assertEquals(0, info.optLong("checked"));
        assertEquals("(never)", info.opt("checkedDate"));

        cacheAllTestData();

        info = mgr.summarizeVolume("old");
        assertEquals("old", info.opt("name"));
        assertEquals(16, info.optInt("roles"));
        assertEquals(2000000, info.optLong("capacity"));
        assertEquals(6, info.optLong("filecount"));
        assertTrue(0 < info.optLong("totalsize"));
        assertTrue(0 < info.optLong("since"));
        assertEquals(0, info.optLong("checked"));
        assertEquals("(never)", info.opt("checkedDate"));

        vols = mgr.summarizeVolumes();        
        info = new JSONObject((Map<String,Object>)
                              vols.toList().stream()
                                  .filter(v -> "foobar".equals(((Map)v).get("name")))
                                  .collect(Collectors.toList()).get(0));
        assertEquals("foobar", info.opt("name"));
        assertEquals(6, info.optInt("roles"));
        assertEquals(2000000, info.optLong("capacity"));
        assertEquals(2, info.optLong("filecount"));
        assertTrue(0 < info.optLong("totalsize"));
        assertTrue(0 < info.optLong("since"));
        assertEquals(0, info.optLong("checked"));
        assertEquals("(never)", info.opt("checkedDate"));

        vols = mgr.summarizeVolumes();        
        info = new JSONObject((Map<String,Object>)
                              vols.toList().stream()
                                  .filter(v -> "cranky".equals(((Map)v).get("name")))
                                  .collect(Collectors.toList()).get(0));
        assertEquals("cranky", info.opt("name"));
        assertEquals(9, info.optInt("roles"));
        assertEquals(2000000, info.optLong("capacity"));
        assertEquals(3, info.optLong("filecount"));
        assertTrue(0 < info.optLong("totalsize"));
        assertTrue(0 < info.optLong("since"));
        assertEquals(0, info.optLong("checked"));
        assertEquals("(never)", info.opt("checkedDate"));
    }

    @Test
    public void testDescribeObject()
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        cacheAllTestData();

        CacheObject co = mgr.describeObject("mds1491", "trial1.json", mgr.VOL_FOR_GET);
        assertNotNull(co);
        assertEquals("mds1491/trial1.json", co.id);
        assertEquals("foobar", co.volname);
        assertTrue(co.cached);

        mgr.uncache("mds1491/trial1.json");
        co = mgr.describeObject("mds1491", "trial1.json", mgr.VOL_FOR_GET);
        assertNull(co);

        co = mgr.describeObject("mds1491", "trial1.json", mgr.VOL_FOR_INFO);
        assertNotNull(co);
        assertEquals("mds1491/trial1.json", co.id);
        assertEquals("foobar", co.volname);
        assertFalse(co.cached);
    }

    @Test
    public void testSelectDatasetObjects()
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        cacheAllTestData();

        List<CacheObject> cos = mgr.selectDatasetObjects("mds1491", mgr.VOL_FOR_GET);
        assertEquals(9, cos.size()); // three for each version cached
        assertEquals(1, cos.stream().filter(c -> "mds1491/trial1.json".equals(c.id))
                           .collect(Collectors.toList()).size());
        assertEquals(1, cos.stream().filter(c -> "mds1491/trial2.json".equals(c.id))
                           .collect(Collectors.toList()).size());
        assertEquals(1, cos.stream().filter(c -> "mds1491/trial3/trial3a.json".equals(c.id))
                           .collect(Collectors.toList()).size());

        mgr.uncache("mds1491/trial1.json");
        cos = mgr.selectDatasetObjects("mds1491", mgr.VOL_FOR_GET);
        assertEquals(8, cos.size());
        cos = mgr.selectDatasetObjects("mds1491", mgr.VOL_FOR_INFO);
        assertEquals(9, cos.size());
    }

    @Test
    public void testSummarizeDataset()
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        cacheAllTestData();

        JSONObject info = mgr.summarizeDataset("mds1491");
        assertNotNull(info);
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", info.get("aipid")); // quirk of this sample data
        assertEquals(3, info.getInt("filecount"));
        assertEquals(691, info.getInt("totalsize"));
        assertTrue(0 < info.optLong("since"));
        assertEquals(0, info.optLong("checked"));

        mgr.check("mds1491", false);
        info = mgr.summarizeDataset("mds1491");
        assertNotNull(info);
        assertTrue(0 < info.optLong("checked"));

        mgr.uncache("mds1491/trial3/trial3a.json");
        info = mgr.summarizeDataset("mds1491");
        assertNotNull(info);
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", info.get("aipid"));
        assertEquals(2, info.getInt("filecount"));
        assertEquals(138, info.getInt("totalsize"));
        assertTrue(0 < info.optLong("since"));
        assertTrue(0 < info.optLong("checked"));
    }

    @Test
    public void testCacheQueue() throws CacheManagementException, IOException {
        assertNotNull(mgr.cath);
        assertFalse(mgr.cath.hasPending());
        Queue<String> q = mgr.cath.loadQueue();
        assertEquals(0, q.size());
        assertTrue(! mgr.cath.isQueued("mds2-1111"));
        q.add("mds2-1111\t0");
        mgr.cath.saveQueue(q);
        assertTrue(mgr.cath.hasPending());
        assertTrue(mgr.cath.isQueued("mds2-1111"));
        mgr.cath.queue("mds2-2222", false);
        mgr.cath.queue("mds2-3333", true);
        assertTrue(mgr.cath.hasPending());
        assertTrue(mgr.cath.isQueued("mds2-1111"));
        assertTrue(mgr.cath.isQueued("mds2-2222"));
        assertTrue(mgr.cath.isQueued("mds2-3333"));
        q = mgr.cath.loadQueue();
        assertEquals(3, q.size());
        assertEquals("mds2-1111\t0", mgr.cath.popQueue());
        assertEquals("mds2-2222\t0", mgr.cath.popQueue());
        assertEquals("mds2-3333\t1", mgr.cath.popQueue());
        assertTrue(! mgr.cath.hasPending());
        assertTrue(!mgr.cath.isQueued("mds2-1111"));
        assertTrue(!mgr.cath.isQueued("mds2-2222"));
        assertTrue(!mgr.cath.isQueued("mds2-3333"));
    }

    @Test
    public void testCachingStatus() throws CacheManagementException {
        assertFalse("Unexpectedly says cacher is running", mgr.isCaching());
        assertNull("Unexepectedly found caching item in progress", mgr.getCachingItemName());
        JSONObject status = mgr.getCachingQueueStatus();
        assertEquals("not running", status.getString("status"));
        assertEquals(JSONObject.NULL, status.get("current"));
        JSONArray waiting = status.getJSONArray("waiting");
        assertEquals(0, waiting.length());
    }
}
