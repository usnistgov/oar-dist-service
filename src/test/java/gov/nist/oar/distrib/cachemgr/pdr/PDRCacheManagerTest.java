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
import gov.nist.oar.distrib.cachemgr.RestorationException;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.inventory.ChecksumCheck;
import gov.nist.oar.distrib.cachemgr.restore.FileCopyRestorer;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
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

    ConfigurableCache createDataCache() throws CacheManagementException, IOException {
        File croot = tempf.newFolder("data");
        File dbf = new File(croot, "inventory.sqlite");
        SQLiteStorageInventoryDB.initializeDB(dbf.getAbsolutePath());
        SQLiteStorageInventoryDB sidb = new SQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("sha256");
        ConfigurableCache cache = new ConfigurableCache("headbags", sidb, 2, null);

        File cvdir = new File(croot, "foobar");  cvdir.mkdir();
        VolumeConfig vc = new VolumeConfig();
        CacheVolume cv = new FilesystemCacheVolume(cvdir, "foobar");
        vc.setRoles(PDRCacheRoles.ROLE_SMALL_OBJECTS|PDRCacheRoles.ROLE_FAST_ACCESS);
        cache.addCacheVolume(cv, 200000, null, vc, true);

        cvdir = new File(croot, "cranky");  cvdir.mkdir();
        cv = new FilesystemCacheVolume(cvdir, "cranky");
        vc.setRoles(PDRCacheRoles.ROLE_GENERAL_PURPOSE|PDRCacheRoles.ROLE_LARGE_OBJECTS);
        cache.addCacheVolume(cv, 200000, null, vc, true);

        cvdir = new File(croot, "old");  cvdir.mkdir();
        cv = new FilesystemCacheVolume(cvdir, "old");
        vc.setRoles(PDRCacheRoles.ROLE_OLD_VERSIONS);
        cache.addCacheVolume(cv, 200000, null, vc, true);

        return cache;
    }

    @Before
    public void setUp() throws IOException, CacheManagementException {
        BagStorage ltstore = new FilesystemLongTermStorage(ltsdir);
        HeadBagCacheManager hbcm = createHBCache(ltstore);

        cache = createDataCache();
        rstr = new PDRDatasetRestorer(ltstore, hbcm, 500);

        List<CacheObjectCheck> checks = new ArrayList<CacheObjectCheck>();
        checks.add(new ChecksumCheck());
        mgr = new PDRCacheManager(cache, rstr, checks, 5000, -1, -1, null);
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
        mgr.cacheDataset("mds1491", null);
        assertTrue(mgr.isCached("mds1491/trial1.json"));
        assertTrue(mgr.isCached("mds1491/trial2.json"));
        assertTrue(mgr.isCached("mds1491/trial3/trial3a.json"));
    }

    public void cacheAllTestData()
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        
    }
}
