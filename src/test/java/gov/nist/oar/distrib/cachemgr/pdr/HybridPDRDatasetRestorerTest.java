package gov.nist.oar.distrib.cachemgr.pdr;

import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.ConfigurableCache;
import gov.nist.oar.distrib.cachemgr.Reservation;
import gov.nist.oar.distrib.cachemgr.VolumeConfig;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.restore.FileCopyRestorer;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HybridPDRDatasetRestorerTest {

    @Rule
    public final TemporaryFolder tempf = new TemporaryFolder();

    final String ltsdir = System.getProperty("project.test.resourceDirectory");

    private BagStorage publicLtstore;
    private BagStorage restrictedLtstore;
    private HeadBagCacheManager hbcm;
    private HybridPDRDatasetRestorer rstr;

    ConfigurableCache cache = null;

    // Helper method to create HeadBagCacheManager. Adjust based on your actual implementation.
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

        publicLtstore = new FilesystemLongTermStorage(ltsdir);
        restrictedLtstore = new FilesystemLongTermStorage(ltsdir + "/restricted");

        hbcm = createHBCache(restrictedLtstore);
        cache = createDataCache();
        rstr = new HybridPDRDatasetRestorer(publicLtstore, restrictedLtstore, hbcm, 500);

    }

    @Test
    public void testGetSmallSizeLimit() {
        // Assuming the small size limit is set to 100000000L in the setUp
        assertEquals("The small size limit should be correctly set", 500, rstr.getSmallSizeLimit());
    }

    @Test
    public void testParseId() {
        String[] parts = rstr.parseId("mds2-8888");
        assertEquals(parts[0], "mds2-8888");
        assertEquals(parts[1], "");
        assertNull(parts[2]);

        parts = rstr.parseId("mds2-8888/");
        assertEquals(parts[0], "mds2-8888");
        assertEquals(parts[1], "");
        assertNull(parts[2]);

        parts = rstr.parseId("mds2-8888#");
        assertEquals(parts[0], "mds2-8888");
        assertEquals(parts[1], "");
        assertEquals(parts[2], "");

        parts = rstr.parseId("mds2-8888/#");
        assertEquals(parts[0], "mds2-8888");
        assertEquals(parts[1], "");
        assertEquals(parts[2], "");

        parts = rstr.parseId("mds2-8888/goob");
        assertEquals(parts[0], "mds2-8888");
        assertEquals(parts[1], "goob");
        assertNull(parts[2]);

        parts = rstr.parseId("mds2-8888/goob/gurn.json");
        assertEquals(parts[0], "mds2-8888");
        assertEquals(parts[1], "goob/gurn.json");
        assertNull(parts[2]);

        parts = rstr.parseId("mds2-8888/goob/gurn.json#");
        assertEquals(parts[0], "mds2-8888");
        assertEquals(parts[1], "goob/gurn.json");
        assertEquals(parts[2], "");

        parts = rstr.parseId("mds2-8888/goob/gurn.json#2");
        assertEquals(parts[0], "mds2-8888");
        assertEquals(parts[1], "goob/gurn.json");
        assertEquals(parts[2], "2");

        parts = rstr.parseId("mds2-8888/goob/gurn.json#1.45.21-rc1");
        assertEquals(parts[0], "mds2-8888");
        assertEquals(parts[1], "goob/gurn.json");
        assertEquals(parts[2], "1.45.21-rc1");
    }

    @Test
    public void testDoesNotExist() throws StorageVolumeException, CacheManagementException {
        assertTrue(rstr.doesNotExist("goober"));

        assertFalse(rstr.doesNotExist("mds1491/trial1.json"));
        assertFalse(rstr.doesNotExist("mds1491/gurn.json"));
        assertFalse(rstr.doesNotExist("mds1491"));
        assertFalse(rstr.doesNotExist("mds1491/"));

        assertFalse(rstr.doesNotExist("mds1491-rpa/trial1.json"));
        assertFalse(rstr.doesNotExist("mds1491-rpa/gurn.json"));
        assertFalse(rstr.doesNotExist("mds1491-rpa"));
        assertFalse(rstr.doesNotExist("mds1491-rpa/"));
    }

    @Test
    public void testGetSize() throws StorageVolumeException, CacheManagementException {
        assertEquals(69, rstr.getSizeOf("mds1491/trial1.json"));
        assertEquals(553, rstr.getSizeOf("mds1491/trial3/trial3a.json"));
        try {
            rstr.getSizeOf("goober/file.json");
            fail("found non-existent file");
        }
        catch (ObjectNotFoundException ex) { /* Success! */ }
    }

    @Test
    public void testGetChecksum() throws StorageVolumeException, CacheManagementException {
        Checksum cksm = rstr.getChecksum("mds1491/trial1.json");
        assertEquals("d155d99281ace123351a311084cd8e34edda6a9afcddd76eb039bad479595ec9", cksm.hash);
        assertEquals("sha256", cksm.algorithm);

        cksm = rstr.getChecksum("mds1491/trial3/trial3a.json");
        assertEquals("ccf6d9df969ab2ef6873f1d3125eac2fbdc082784b446be4c44d8bab148f5396", cksm.hash);
        assertEquals("sha256", cksm.algorithm);

        try {
            rstr.getChecksum("goober/file.json");
            fail("found non-existent file");
        }
        catch (ObjectNotFoundException ex) { /* Success! */ }
    }
}

