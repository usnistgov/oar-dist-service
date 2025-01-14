package gov.nist.oar.distrib.cachemgr.pdr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.ConfigurableCache;
import gov.nist.oar.distrib.cachemgr.Reservation;
import gov.nist.oar.distrib.cachemgr.VolumeConfig;
import gov.nist.oar.distrib.cachemgr.VolumeStatus;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

public class RestrictedDatasetRestorerTest {

    @TempDir
    public File tempDir; 

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

        return new HeadBagCacheManager(cache, sidb, new HeadBagRestorer(reststore, pubstore), "88434");
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
        vc.setRoles(PDRCacheRoles.ROLE_GENERAL_PURPOSE);
        cache.addCacheVolume(cv, 2000000, null, vc, true);

        cvdir = new File(croot, "old");
        cvdir.mkdir();
        cv = new FilesystemCacheVolume(cvdir, "old");
        vc.setRoles(PDRCacheRoles.ROLE_OLD_VERSIONS);
        cache.addCacheVolume(cv, 2000000, null, vc, true);

        cvdir = new File(croot, "rpa");
        cvdir.mkdir();
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
        assertTrue(! cache.isCached("mds1491/trial1.json"));
        Reservation resv = cache.reserveSpace(70, PDRCacheRoles.ROLE_RESTRICTED_DATA);
        rstr.restoreObject("mds1491/trial1.json", resv, "mds1491/trial1.json", null);
        assertTrue(cache.isCached("mds1491/trial1.json"));
        assertEquals("rpa", resv.getVolumeName());
        assertTrue((new File(tempDir,
                "data/"+resv.getVolumeName()+"/mds1491/trial1.json")).exists());

        // this file is only found in the restricted store
        assertTrue(! cache.isCached("mds1491/README.txt"));
        resv = cache.reserveSpace(15, PDRCacheRoles.ROLE_RESTRICTED_DATA);
        rstr.restoreObject("mds1491/README.txt", resv, "mds1491/README.txt", null);
        assertTrue(cache.isCached("mds1491/README.txt"));
        assertEquals("rpa", resv.getVolumeName());
        assertTrue((new File(tempDir,
                "data/"+resv.getVolumeName()+"/mds1491/README.txt")).exists());

        assertTrue(! cache.isCached("mds1491/trial2.json"));
        resv = cache.reserveSpace(68, PDRCacheRoles.ROLE_RESTRICTED_DATA);
        rstr.restoreObject("mds1491/trial2.json", resv, "mds1491/trial2.json", null);
        assertTrue(cache.isCached("mds1491/trial2.json"));
        assertEquals("rpa", resv.getVolumeName());
        File t2file = new File(tempDir, "data/"+resv.getVolumeName()+"/mds1491/trial2.json");
        assertTrue(t2file.exists());

        // confirm that we got the one from the restricted store
        String trial2 = new String(Files.readAllBytes(Paths.get(t2file.toURI())));
        JSONObject data = new JSONObject(trial2);
        assertTrue(data.getBoolean("result"));
        assertEquals("2024-01-06", data.getString("date"));

        assertTrue(! cache.isCached("mds1491/trial2.json#1.1.0"));
        resv = cache.reserveSpace(553, PDRCacheRoles.ROLE_OLD_VERSIONS);
        rstr.restoreObject("mds1491/trial2.json#1.1.0", resv, "mds1491/trial2.json", null);
        assertTrue(cache.isCached("mds1491/trial2.json#1.1.0"));
        assertEquals("old", resv.getVolumeName());
        t2file = new File(tempDir, "data/"+resv.getVolumeName()+"/mds1491/trial2.json");
        assertTrue(t2file.exists());

        // confirm that we got the one from the public store
        trial2 = new String(Files.readAllBytes(Paths.get(t2file.toURI())));
        data = new JSONObject(trial2);
        assertFalse(data.getBoolean("result"));
        assertEquals("2017-02-02", data.getString("date"));
    }

    @Test
    public void testCacheDataset()
            throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        assertTrue(! cache.isCached("mds1491/trial1.json"));
        assertTrue(! cache.isCached("mds1491/trial2.json"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json"));
        assertTrue(! cache.isCached("mds1491/README.txt"));
        assertTrue(! cache.isCached("mds1491/trial1.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial1.json#1"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1"));

        Set<String> cached = rstr.cacheDataset("mds1491", null, cache, true,
                                               PDRCacheRoles.ROLE_RESTRICTED_DATA, null);
        assertTrue(cached.contains("trial1.json"));
        assertTrue(cached.contains("trial2.json"));
        assertTrue(cached.contains("README.txt"));
        assertTrue(cached.contains("trial3/trial3a.json"));
        assertEquals(4, cached.size());

        assertTrue(cache.isCached("mds1491/trial1.json"));
        assertTrue(cache.isCached("mds1491/trial2.json"));
        assertTrue(cache.isCached("mds1491/README.txt"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json"));
        assertTrue(! cache.isCached("mds1491/trial1.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial1.json#1"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1"));

        cached = rstr.cacheDataset("mds1491", "1.1.0", cache, true,
                                   PDRCacheRoles.ROLE_OLD_VERSIONS, null);
        assertTrue(cached.contains("trial1.json"));
        assertTrue(cached.contains("trial2.json"));
        assertTrue(cached.contains("trial3/trial3a.json"));
        assertFalse(cached.contains("README.txt"));
        assertEquals(3, cached.size());
        assertTrue(cache.isCached("mds1491/trial1.json#1.1.0"));
        assertTrue(cache.isCached("mds1491/trial2.json#1.1.0"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/README.txt#1.1.0"));
    }

    @Test
    public void testCacheFromBagSelect()
            throws StorageVolumeException, ResourceNotFoundException, CacheManagementException,
            FileNotFoundException
    {
        assertTrue(! cache.isCached("mds1491/trial1.json"));
        assertTrue(! cache.isCached("mds1491/trial2.json"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json"));
        assertTrue(! cache.isCached("mds1491/README.txt"));

        ArrayList<String> need = new ArrayList<String>();
        need.add("trial1.json");
        need.add("trial3/trial3a.json");

        Set<String> cached = rstr.cacheFromBag("mds1491.mbag0_2-0.zip", need, null, cache, true);
        assertTrue(cached.contains("trial1.json"));
        assertTrue(! cached.contains("trial2.json"));
        assertTrue(! cached.contains("README.txt"));
        assertTrue(cached.contains("trial3/trial3a.json"));
        assertEquals(2, cached.size());

        assertTrue(cache.isCached("mds1491/trial1.json"));
        assertTrue(! cache.isCached("mds1491/trial2.json"));
        assertTrue(! cache.isCached("mds1491/README.txt"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json"));

        need.add("README.txt");
        cached = rstr.cacheFromBag("mds1491.1_2_0.mbag0_4-2.zip", need, null, cache, true);
        assertTrue(cached.contains("README.txt"));
        assertEquals(1, cached.size());

        assertTrue(cache.isCached("mds1491/trial1.json"));
        assertTrue(! cache.isCached("mds1491/trial2.json"));
        assertTrue(cache.isCached("mds1491/README.txt"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json"));
    }

    @Test
    public void testCacheFromBag()
            throws StorageVolumeException, FileNotFoundException, CacheManagementException
    {
        assertTrue(! cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf"));
        assertTrue(! cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf.sha256"));
        Set<String> cached =
                rstr.cacheFromBag("67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip", null, null, cache, true);
        assertEquals(2, cached.size());
        assertTrue(cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf"));
        assertTrue(! cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf.sha256"));

        // test what happens when attempting recache
        List<CacheObject> found =
                cache.getInventoryDB().findObject("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf",
                        VolumeStatus.VOL_FOR_GET);
        assertEquals(1, found.size());
        long since = found.get(0).getMetadatumLong("since", 0L);
        assertTrue(since > 0L, "Missing since metadatum");
        cached = rstr.cacheFromBag("67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip", null, null, cache, true);
        assertTrue(cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf"));
        assertTrue(! cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf.sha256"));
        found =
                cache.getInventoryDB().findObject("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf",
                        VolumeStatus.VOL_FOR_INFO);
        assertEquals(1, found.size());
        assertTrue(since < found.get(0).getMetadatumLong("since", 0L), "File appears not to have been recached");

        // test when file might get cached to different volume
        File croot = new File(tempDir,"data");
        File cvdir = new File(croot, "crunchy");  cvdir.mkdir();
        VolumeConfig vc = new VolumeConfig();
        CacheVolume cv = new FilesystemCacheVolume(cvdir, "crunchy");
        cv = new FilesystemCacheVolume(cvdir, "crunchy");
        vc.setRoles(PDRCacheRoles.ROLE_GENERAL_PURPOSE|PDRCacheRoles.ROLE_LARGE_OBJECTS);
        cache.addCacheVolume(cv, 2000000, null, vc, true);

        assertTrue(
                cache.isCached("67C783D4BA814C8EE05324570681708A1899/Materials_Registry_vocab_20180418.xlsx"));
        cached = rstr.cacheFromBag("67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip", null, null, cache, true);
        assertTrue(
                cache.isCached("67C783D4BA814C8EE05324570681708A1899/Materials_Registry_vocab_20180418.xlsx"));
        found =
                cache.getInventoryDB()
                        .findObject("67C783D4BA814C8EE05324570681708A1899/Materials_Registry_vocab_20180418.xlsx",
                                VolumeStatus.VOL_FOR_GET);
        assertEquals(1, found.size());
        assertEquals("rpa", found.get(0).volname);

        // test optional recache
        since = found.get(0).getMetadatumLong("since", 0L);
        assertTrue(since > 0L, "Missing since metadatum");
        cache.uncache("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf");
        assertFalse(cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf"));
        cached = rstr.cacheFromBag("67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip", null, null, cache, false);
        assertTrue(cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf"));
        found =
                cache.getInventoryDB()
                        .findObject("67C783D4BA814C8EE05324570681708A1899/Materials_Registry_vocab_20180418.xlsx",
                                VolumeStatus.VOL_FOR_GET);
        assertEquals(since, found.get(0).getMetadatumLong("since", 0L), "File appears to have been recached:");

    }

    @Test
    public void testExpires() throws StorageVolumeException, ResourceNotFoundException, CacheManagementException {

        assertTrue(! cache.isCached("mds1491/trial1.json"));
        assertTrue(! cache.isCached("mds1491/trial2.json"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json"));
        assertTrue(! cache.isCached("mds1491/README.txt"));

        Set<String> cached = rstr.cacheDataset("mds1491", null, cache, true,
                PDRCacheRoles.ROLE_RESTRICTED_DATA, null);
        assertTrue(cached.contains("trial1.json"));
        assertTrue(cached.contains("trial2.json"));
        assertTrue(cached.contains("README.txt"));
        assertTrue(cached.contains("trial3/trial3a.json"));
        assertEquals(4, cached.size());

        assertTrue(cache.isCached("mds1491/trial1.json"));
        assertTrue(cache.isCached("mds1491/trial2.json"));
        assertTrue(cache.isCached("mds1491/README.txt"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json"));

        List<CacheObject> found = cache.getInventoryDB().findObject("mds1491/trial1.json", VolumeStatus.VOL_FOR_INFO);
        assertEquals(1, found.size());
        assertTrue(found.get(0).hasMetadatum("expires"), "CacheObject should contain expires metadata");

        // Verify the "expires" value is approximately 2 weeks from the current time
        long TWO_WEEKS_MILLIS = 14L * 24 * 60 * 60 * 1000;
        long expectedExpires = System.currentTimeMillis() + TWO_WEEKS_MILLIS;
        long actualExpires = found.get(0).getMetadatumLong("expires", 0);
        // Check that the absolute difference between the expected and actual expires values is less than 1000ms
        assertTrue(Math.abs(expectedExpires - actualExpires) < 5000, "expires field should be set to 2 weeks from the current time (diff="+
                   Long.toString(Math.abs(expectedExpires - actualExpires)) + ")");

        found = cache.getInventoryDB().findObject("mds1491/trial2.json", VolumeStatus.VOL_FOR_INFO);
        assertEquals(1, found.size());
        assertTrue(found.get(0).hasMetadatum("expires"), "CacheObject should contain expires metadata");

        expectedExpires= System.currentTimeMillis() + TWO_WEEKS_MILLIS;
        actualExpires = found.get(0).getMetadatumLong("expires", 0);
        assertTrue(Math.abs(expectedExpires - actualExpires) < 5000, "expires field should be set to 2 weeks from the current time");

        found = cache.getInventoryDB().findObject("mds1491/README.txt", VolumeStatus.VOL_FOR_INFO);
        assertEquals(1, found.size());
        assertTrue(found.get(0).hasMetadatum("expires"), "CacheObject should contain expires metadata");

        expectedExpires = System.currentTimeMillis() + TWO_WEEKS_MILLIS;
        actualExpires = found.get(0).getMetadatumLong("expires", 0);
        assertTrue(Math.abs(expectedExpires - actualExpires) < 5000, "expires field should be set to 2 weeks from the current time");

    }
}
