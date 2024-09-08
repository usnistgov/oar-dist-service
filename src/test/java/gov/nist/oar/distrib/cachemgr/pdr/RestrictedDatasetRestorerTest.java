package gov.nist.oar.distrib.cachemgr.pdr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;

import org.json.JSONException;
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

public class RestrictedDatasetRestorerTest {

    @TempDir
    File tempDir;

    final String ltsdir = System.getProperty("project.test.resourceDirectory");

    private BagStorage publicLtstore;
    private BagStorage restrictedLtstore;
    private HeadBagCacheManager hbcm;
    private RestrictedDatasetRestorer rstr;

    ConfigurableCache cache = null;

    // Helper method to create HeadBagCacheManager. Adjust based on your actual implementation.
    HeadBagCacheManager createHBCache(BagStorage reststore, BagStorage pubstore)
        throws IOException, CacheManagementException
    {
        File tf = new File(tempDir, "headbags");
        File dbf = new File(tf, "inventory.sqlite");
        HeadBagDB.initializeSQLiteDB(dbf.getAbsolutePath());
        HeadBagDB sidb = HeadBagDB.createHeadBagDB(dbf.getAbsolutePath());
        sidb.registerAlgorithm("sha256");
        ConfigurableCache cache = new ConfigurableCache("headbags", sidb, 2, null);

        File cvd = new File(tf, "cv0"); cvd.mkdir();
        cache.addCacheVolume(new FilesystemCacheVolume(cvd, "cv0"), 2000000, null, true);
        cvd = new File(tf, "cv1"); cvd.mkdir();
        cache.addCacheVolume(new FilesystemCacheVolume(cvd, "cv1"), 2000000, null, true);

        return new HeadBagCacheManager(cache, sidb, new HeadBagRestorer(reststore, pubstore), "88434");
    }

    ConfigurableCache createDataCache() throws CacheManagementException, IOException {
        File croot = new File(tempDir, "data");
        File dbf = new File(croot, "inventory.sqlite");
        SQLiteStorageInventoryDB.initializeDB(dbf.getAbsolutePath());
        SQLiteStorageInventoryDB sidb = new SQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("sha256");
        ConfigurableCache cache = new ConfigurableCache("headbags", sidb, 2, null);

        File cvdir = new File(croot, "foobar"); cvdir.mkdir();
        VolumeConfig vc = new VolumeConfig();
        CacheVolume cv = new FilesystemCacheVolume(cvdir, "foobar");
        vc.setRoles(PDRCacheRoles.ROLE_GENERAL_PURPOSE);
        cache.addCacheVolume(cv, 2000000, null, vc, true);

        cvdir = new File(croot, "old"); cvdir.mkdir();
        cv = new FilesystemCacheVolume(cvdir, "old");
        vc.setRoles(PDRCacheRoles.ROLE_OLD_VERSIONS);
        cache.addCacheVolume(cv, 2000000, null, vc, true);

        cvdir = new File(croot, "rpa"); cvdir.mkdir();
        cv = new FilesystemCacheVolume(cvdir, "rpa");
        vc.setRoles(PDRCacheRoles.ROLE_RESTRICTED_DATA);
        cache.addCacheVolume(cv, 2000000, null, vc, true);

        return cache;
    }

    @BeforeEach
    public void setUp() throws IOException, CacheManagementException {
        publicLtstore = new FilesystemLongTermStorage(ltsdir);
        restrictedLtstore = new FilesystemLongTermStorage(ltsdir + "/restricted");

        hbcm = createHBCache(restrictedLtstore, publicLtstore);
        cache = createDataCache();
        rstr = new RestrictedDatasetRestorer(publicLtstore, restrictedLtstore, hbcm, 500);
    }

    @Test
    public void testGetSmallSizeLimit() {
        assertEquals(500, rstr.getSmallSizeLimit(), "The small size limit should be correctly set");
    }

    @Test
    public void testParseId() {
        String[] parts = rstr.parseId("mds2-8888");
        assertEquals("mds2-8888", parts[0]);
        assertEquals("", parts[1]);
        assertNull(parts[2]);

        parts = rstr.parseId("mds2-8888/");
        assertEquals("mds2-8888", parts[0]);
        assertEquals("", parts[1]);
        assertNull(parts[2]);

        parts = rstr.parseId("mds2-8888#");
        assertEquals("mds2-8888", parts[0]);
        assertEquals("", parts[1]);
        assertEquals("", parts[2]);

        parts = rstr.parseId("mds2-8888/goob");
        assertEquals("mds2-8888", parts[0]);
        assertEquals("goob", parts[1]);
        assertNull(parts[2]);

        parts = rstr.parseId("mds2-8888/goob/gurn.json");
        assertEquals("mds2-8888", parts[0]);
        assertEquals("goob/gurn.json", parts[1]);
        assertNull(parts[2]);

        parts = rstr.parseId("mds2-8888/goob/gurn.json#2");
        assertEquals("mds2-8888", parts[0]);
        assertEquals("goob/gurn.json", parts[1]);
        assertEquals("2", parts[2]);
    }

    @Test
    public void testDoesNotExist() throws StorageVolumeException, CacheManagementException {
        assertTrue(rstr.doesNotExist("goober"));
        assertFalse(rstr.doesNotExist("mds1491/trial1.json"));
        assertFalse(rstr.doesNotExist("mds1491/gurn.json"));
    }

    @Test
    public void testGetSize() throws StorageVolumeException, CacheManagementException {
        assertEquals(69, rstr.getSizeOf("mds1491/trial1.json"));
        assertEquals(15, rstr.getSizeOf("mds1491/README.txt"));
        assertEquals(553, rstr.getSizeOf("mds1491/trial3/trial3a.json"));
        try {
            rstr.getSizeOf("goober/file.json");
            fail("found non-existent file");
        } catch (ObjectNotFoundException ex) { /* Success! */ }
    }

    @Test
    public void testGetChecksum() throws StorageVolumeException, CacheManagementException {
        Checksum cksm = rstr.getChecksum("mds1491/trial1.json");
        assertEquals("d155d99281ace123351a311084cd8e34edda6a9afcddd76eb039bad479595ec9", cksm.hash);
        assertEquals("sha256", cksm.algorithm);

        cksm = rstr.getChecksum("mds1491/README.txt");
        assertEquals("b6433cb0e033f32ba411463e2cc304bd6aa3a9e0efa8e2539c452622f02a99cd", cksm.hash);
        assertEquals("sha256", cksm.algorithm);

        try {
            rstr.getChecksum("goober/file.json");
            fail("found non-existent file");
        } catch (ObjectNotFoundException ex) { /* Success! */ }
    }

    @Test
    public void testRestoreObject()
        throws CacheManagementException, StorageVolumeException, JSONException, IOException
    {
        assertFalse(cache.isCached("mds1491/trial1.json"));
        Reservation resv = cache.reserveSpace(70, PDRCacheRoles.ROLE_RESTRICTED_DATA);
        rstr.restoreObject("mds1491/trial1.json", resv, "mds1491/trial1.json", null);
        assertTrue(cache.isCached("mds1491/trial1.json"));
        assertEquals("rpa", resv.getVolumeName());
        assertTrue(new File(tempDir, "data/" + resv.getVolumeName() + "/mds1491/trial1.json").exists());
    }
}
