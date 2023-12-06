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
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.VolumeConfig;
import gov.nist.oar.distrib.cachemgr.VolumeStatus;
import gov.nist.oar.distrib.cachemgr.Reservation;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.cachemgr.simple.SimpleCache;
import gov.nist.oar.distrib.cachemgr.BasicCacheManager;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.RestorationException;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;
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

public class PDRDatasetRestorerTest {

    @Rule
    public final TemporaryFolder tempf = new TemporaryFolder();

    final String ltsdir = System.getProperty("project.test.resourceDirectory");
    PDRDatasetRestorer rstr = null;
    ConfigurableCache cache = null;

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
        BagStorage ltstore = new FilesystemLongTermStorage(ltsdir);
        HeadBagCacheManager hbcm = createHBCache(ltstore);

        cache = createDataCache();
        rstr = new PDRDatasetRestorer(ltstore, hbcm, 500);
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

    @Test
    public void testRestoreObject() throws CacheManagementException, StorageVolumeException, JSONException {
        assertTrue(! cache.isCached("mds1491/trial1.json"));
        Reservation resv = cache.reserveSpace(70, PDRCacheRoles.ROLE_SMALL_OBJECTS);
        rstr.restoreObject("mds1491/trial1.json", resv, "mds1491/trial1.json", null);
        assertTrue(cache.isCached("mds1491/trial1.json"));
        assertEquals("foobar", resv.getVolumeName());
        assertTrue((new File(tempf.getRoot(),
                             "data/"+resv.getVolumeName()+"/mds1491/trial1.json")).exists());

        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1"));
        resv = cache.reserveSpace(70, PDRCacheRoles.ROLE_OLD_VERSIONS);
        rstr.restoreObject("mds1491/trial3/trial3a.json#1", resv, "mds1491/trial3/trial3a-v1.json", null);
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json#1"));
        assertEquals("old", resv.getVolumeName());
        assertTrue((new File(tempf.getRoot(),
                             "data/"+resv.getVolumeName()+"/mds1491/trial3/trial3a-v1.json")).exists());

        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));
        resv = cache.reserveSpace(553, PDRCacheRoles.ROLE_GENERAL_PURPOSE);
        rstr.restoreObject("mds1491/trial3/trial3a.json#1.1.0", resv, "mds1491/trial3/trial3a-X.json", null);
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));
        assertEquals("cranky", resv.getVolumeName());
        assertTrue((new File(tempf.getRoot(),
                             "data/"+resv.getVolumeName()+"/mds1491/trial3/trial3a-X.json")).exists());

        assertTrue(! cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf"));
        assertTrue(! cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf.sha256"));
        resv = cache.reserveSpace(64);
        rstr.restoreObject("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf.sha256",
                           resv, "NMRRVocab20171102.rdf.sha256", null);
        assertTrue(cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf.sha256"));
        assertTrue(! cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf"));
        assertTrue((new File(tempf.getRoot(),
                             "data/"+resv.getVolumeName()+"/NMRRVocab20171102.rdf.sha256")).exists());
    }

    @Test
    public void testCacheDataset() 
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        assertTrue(! cache.isCached("mds1491/trial1.json"));
        assertTrue(! cache.isCached("mds1491/trial2.json"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json"));
        assertTrue(! cache.isCached("mds1491/trial1.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial1.json#1"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1"));

        Set<String> cached = rstr.cacheDataset("mds1491", null, cache, true, 0 , null);
        assertTrue(cached.contains("trial1.json"));
        assertTrue(cached.contains("trial2.json"));
        assertTrue(cached.contains("trial3/trial3a.json"));
        assertEquals(3, cached.size());

        assertTrue(cache.isCached("mds1491/trial1.json"));
        assertTrue(cache.isCached("mds1491/trial2.json"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json"));
        assertTrue(! cache.isCached("mds1491/trial1.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial1.json#1"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1"));

        cached = rstr.cacheDataset("mds1491", "1", cache, true, 0 , null);
        assertTrue(cached.contains("trial1.json"));
        assertTrue(cached.contains("trial2.json"));
        assertTrue(cached.contains("trial3/trial3a.json"));
        assertEquals(3, cached.size());

        assertTrue(cache.isCached("mds1491/trial1.json"));
        assertTrue(cache.isCached("mds1491/trial2.json"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json"));
        assertTrue(! cache.isCached("mds1491/trial1.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));
        assertTrue(cache.isCached("mds1491/trial1.json#1"));
        assertTrue(cache.isCached("mds1491/trial2.json#1"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json#1"));

        cached = rstr.cacheDataset("mds1491", "1.1.0", cache, true, 0 , null);
        assertTrue(cached.contains("trial1.json"));
        assertTrue(cached.contains("trial2.json"));
        assertTrue(cached.contains("trial3/trial3a.json"));
        assertEquals(3, cached.size());

        assertTrue(cache.isCached("mds1491/trial1.json"));
        assertTrue(cache.isCached("mds1491/trial2.json"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json"));
        assertTrue(cache.isCached("mds1491/trial1.json#1.1.0"));
        assertTrue(cache.isCached("mds1491/trial2.json#1.1.0"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));
        assertTrue(cache.isCached("mds1491/trial1.json#1"));
        assertTrue(cache.isCached("mds1491/trial2.json#1"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json#1"));

        assertTrue(! cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf"));
        assertTrue(! cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf.sha256"));
        cached = rstr.cacheDataset("67C783D4BA814C8EE05324570681708A1899", null, cache, true, 0 , null);
        assertEquals(2, cached.size());
        assertTrue(cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf"));
        assertTrue(! cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf.sha256"));
    }

    @Test
    public void testCacheFromBagSelect() 
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException,
               FileNotFoundException
    {
        assertTrue(! cache.isCached("mds1491/trial1.json"));
        assertTrue(! cache.isCached("mds1491/trial2.json"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json"));
        assertTrue(! cache.isCached("mds1491/trial1.json#8"));
        assertTrue(! cache.isCached("mds1491/trial2.json#8"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#8"));

        ArrayList<String> need = new ArrayList<String>();
        need.add("trial1.json");
        need.add("trial3/trial3a.json");

        Set<String> cached = rstr.cacheFromBag("mds1491.mbag0_2-0.zip", need, null, cache, true);
        assertTrue(cached.contains("trial1.json"));
        assertTrue(! cached.contains("trial2.json"));
        assertTrue(cached.contains("trial3/trial3a.json"));
        assertEquals(2, cached.size());

        assertTrue(cache.isCached("mds1491/trial1.json"));
        assertTrue(! cache.isCached("mds1491/trial2.json"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json"));
        assertTrue(! cache.isCached("mds1491/trial1.json#8"));
        assertTrue(! cache.isCached("mds1491/trial2.json#8"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#8"));

        need.add("trial1.json");
        need.add("trial3/trial3a.json");
        cached = rstr.cacheFromBag("mds1491.mbag0_2-0.zip", need, "8", cache, true);
        assertTrue(cached.contains("trial1.json"));
        assertTrue(! cached.contains("trial2.json"));
        assertTrue(cached.contains("trial3/trial3a.json"));
        assertEquals(2, cached.size());

        assertTrue(cache.isCached("mds1491/trial1.json#8"));
        assertTrue(! cache.isCached("mds1491/trial2.json#8"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json#8"));
        assertTrue(cache.isCached("mds1491/trial1.json"));
        assertTrue(! cache.isCached("mds1491/trial2.json"));
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
        assertTrue("Missing since metadatum", since > 0L);
        cached = rstr.cacheFromBag("67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip", null, null, cache, true);
        assertTrue(cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf"));
        assertTrue(! cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf.sha256"));
        found =
            cache.getInventoryDB().findObject("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf",
                                              VolumeStatus.VOL_FOR_INFO);
        assertEquals(1, found.size());
        assertTrue("File appears not to have been recached", since < found.get(0).getMetadatumLong("since", 0L));

        // test when file might get cached to different volume
        File croot = new File(tempf.getRoot(),"data");
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
        assertEquals("crunchy", found.get(0).volname);

        // test optional recache
        since = found.get(0).getMetadatumLong("since", 0L);
        assertTrue("Missing since metadatum", since > 0L);
        cache.uncache("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf");
        assertFalse(cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf"));
        cached = rstr.cacheFromBag("67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip", null, null, cache, false);
        assertTrue(cache.isCached("67C783D4BA814C8EE05324570681708A1899/NMRRVocab20171102.rdf"));
        found =
            cache.getInventoryDB()
                 .findObject("67C783D4BA814C8EE05324570681708A1899/Materials_Registry_vocab_20180418.xlsx",
                             VolumeStatus.VOL_FOR_GET);
        assertEquals("File appears to have been recached:", since, found.get(0).getMetadatumLong("since", 0L));

    }

    @Test
    public void testGetPreferencesFor() {
        assertEquals(rstr.ROLE_OLD_VERSIONS, rstr.getPreferencesFor("mds1214/readme.txt#1.0", 200L, -1));
        assertEquals(rstr.ROLE_OLD_VERSIONS, rstr.getPreferencesFor("mds1214/readme.txt#1.0",
                                                                    rstr.getSmallSizeLimit()+10, -1));
        assertEquals(rstr.ROLE_GENERAL_PURPOSE, rstr.getPreferencesFor("mds1214/readme.txt",
                                                                       rstr.getSmallSizeLimit()+10, -1));
        assertEquals(rstr.ROLE_SMALL_OBJECTS, rstr.getPreferencesFor("mds1214/readme.txt", 200L, -1));
        assertEquals(rstr.ROLE_GENERAL_PURPOSE, rstr.getPreferencesFor("mds1214/readme.txt", 0, -1));
        assertEquals(rstr.ROLE_GENERAL_PURPOSE, rstr.getPreferencesFor("mds1214/readme.txt", -1, -1));
        assertEquals(rstr.ROLE_GENERAL_PURPOSE, rstr.getPreferencesFor("mds1214/readme.txt",
                                                                       rstr.getSmallSizeLimit()+10, 3));
        assertEquals(rstr.ROLE_GENERAL_PURPOSE, rstr.getPreferencesFor("mds1214/readme.txt",
                                                                       rstr.getSmallSizeLimit()+10, 10));
        assertEquals(rstr.ROLE_SMALL_OBJECTS, rstr.getPreferencesFor("mds1214/readme.txt", 200L, 2));
    }
}


