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

import org.json.JSONException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.ConfigurableCache;
import gov.nist.oar.distrib.cachemgr.Reservation;
import gov.nist.oar.distrib.cachemgr.VolumeConfig;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

public class PDRDatasetRestorerTest {

    @TempDir
    public File tempDir;

    final String ltsdir = System.getProperty("project.test.resourceDirectory");
    PDRDatasetRestorer rstr = null;
    ConfigurableCache cache = null;

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

    ConfigurableCache createDataCache() throws CacheManagementException, IOException {
        File croot = new File(tempDir, "data");
        croot.mkdir();
        File dbf = new File(croot, "inventory.sqlite");
        SQLiteStorageInventoryDB.initializeDB(dbf.getAbsolutePath());
        SQLiteStorageInventoryDB sidb = new SQLiteStorageInventoryDB(dbf.getPath());
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
        BagStorage ltstore = new FilesystemLongTermStorage(ltsdir);
        HeadBagCacheManager hbcm = createHBCache(ltstore);

        cache = createDataCache();
        rstr = new PDRDatasetRestorer(ltstore, hbcm, 500);
    }

    @Test
    public void testParseId() {
        String[] parts = rstr.parseId("mds2-8888");
        Assertions.assertEquals("mds2-8888", parts[0]);
        Assertions.assertEquals("", parts[1]);
        Assertions.assertNull(parts[2]);

        parts = rstr.parseId("mds2-8888/");
        Assertions.assertEquals("mds2-8888", parts[0]);
        Assertions.assertEquals("", parts[1]);
        Assertions.assertNull(parts[2]);

        parts = rstr.parseId("mds2-8888#");
        Assertions.assertEquals("mds2-8888", parts[0]);
        Assertions.assertEquals("", parts[1]);
        Assertions.assertEquals("", parts[2]);

        parts = rstr.parseId("mds2-8888/#");
        Assertions.assertEquals("mds2-8888", parts[0]);
        Assertions.assertEquals("", parts[1]);
        Assertions.assertEquals("", parts[2]);

        parts = rstr.parseId("mds2-8888/goob");
        Assertions.assertEquals("mds2-8888", parts[0]);
        Assertions.assertEquals("goob", parts[1]);
        Assertions.assertNull(parts[2]);

        parts = rstr.parseId("mds2-8888/goob/gurn.json");
        Assertions.assertEquals("mds2-8888", parts[0]);
        Assertions.assertEquals("goob/gurn.json", parts[1]);
        Assertions.assertNull(parts[2]);

        parts = rstr.parseId("mds2-8888/goob/gurn.json#");
        Assertions.assertEquals("mds2-8888", parts[0]);
        Assertions.assertEquals("goob/gurn.json", parts[1]);
        Assertions.assertEquals("", parts[2]);

        parts = rstr.parseId("mds2-8888/goob/gurn.json#2");
        Assertions.assertEquals("mds2-8888", parts[0]);
        Assertions.assertEquals("goob/gurn.json", parts[1]);
        Assertions.assertEquals("2", parts[2]);

        parts = rstr.parseId("mds2-8888/goob/gurn.json#1.45.21-rc1");
        Assertions.assertEquals("mds2-8888", parts[0]);
        Assertions.assertEquals("goob/gurn.json", parts[1]);
        Assertions.assertEquals("1.45.21-rc1", parts[2]);
    }

    @Test
    public void testDoesNotExist() throws StorageVolumeException, CacheManagementException {
        Assertions.assertTrue(rstr.doesNotExist("goober"));
        Assertions.assertFalse(rstr.doesNotExist("mds1491/trial1.json"));
        Assertions.assertFalse(rstr.doesNotExist("mds1491/gurn.json"));
        Assertions.assertFalse(rstr.doesNotExist("mds1491"));
        Assertions.assertFalse(rstr.doesNotExist("mds1491/"));
    }

    @Test
    public void testGetSize() throws StorageVolumeException, CacheManagementException {
        Assertions.assertEquals(69, rstr.getSizeOf("mds1491/trial1.json"));
        Assertions.assertEquals(553, rstr.getSizeOf("mds1491/trial3/trial3a.json"));
        Assertions.assertThrows(ObjectNotFoundException.class, () -> {
            rstr.getSizeOf("goober/file.json");
        });
    }

    @Test
    public void testGetChecksum() throws StorageVolumeException, CacheManagementException {
        Checksum cksm = rstr.getChecksum("mds1491/trial1.json");
        Assertions.assertEquals("d155d99281ace123351a311084cd8e34edda6a9afcddd76eb039bad479595ec9", cksm.hash);
        Assertions.assertEquals("sha256", cksm.algorithm);

        cksm = rstr.getChecksum("mds1491/trial3/trial3a.json");
        Assertions.assertEquals("ccf6d9df969ab2ef6873f1d3125eac2fbdc082784b446be4c44d8bab148f5396", cksm.hash);
        Assertions.assertEquals("sha256", cksm.algorithm);

        Assertions.assertThrows(ObjectNotFoundException.class, () -> {
            rstr.getChecksum("goober/file.json");
        });
    }

    @Test
    public void testRestoreObject() throws CacheManagementException, StorageVolumeException, JSONException {
        Assertions.assertFalse(cache.isCached("mds1491/trial1.json"));
        Reservation resv = cache.reserveSpace(70, PDRCacheRoles.ROLE_SMALL_OBJECTS);
        rstr.restoreObject("mds1491/trial1.json", resv, "mds1491/trial1.json", null);
        Assertions.assertTrue(cache.isCached("mds1491/trial1.json"));
        Assertions.assertEquals("foobar", resv.getVolumeName());
        Assertions.assertTrue((new File(tempDir, "data/" + resv.getVolumeName() + "/mds1491/trial1.json")).exists());
    }
}
