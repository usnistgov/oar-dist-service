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

import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.Cache;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.ConfigurableCache;
import gov.nist.oar.distrib.cachemgr.VolumeConfig;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONObject;
import org.json.JSONException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BagCacherTest {

    @TempDir
    public File tempf;

    MetadataCache mdcache = null;
    public BagCacher cacher = null;
    public StorageInventoryDB sidb = null;
    public ConfigurableCache cache = null;
    BagStorage ltstore = null;

    StorageInventoryDB createDB() throws IOException, InventoryException {
        File tf = new File(tempf, "testdb.sqlite");
        SQLiteStorageInventoryDB.initializeDB(tf.getAbsolutePath());

        StorageInventoryDB out = new SQLiteStorageInventoryDB(tf.getPath());
        out.registerAlgorithm("md5");
        out.registerAlgorithm("sha256");
        return out;
    }

    ConfigurableCache createCache(StorageInventoryDB db)
        throws InventoryException, StorageVolumeException, IOException, CacheManagementException
    {
        cache = null;
        File cacheroot = new File(tempf, "cache");
        File voldir = new File(cacheroot, "foobar");
        voldir.mkdir();
        ConfigurableCache out = new ConfigurableCache("test", db);

        VolumeConfig vc = new VolumeConfig();
        CacheVolume cv = new FilesystemCacheVolume(voldir, "foobar");
        int[] sizes = { 229, 9321, 9001, 980, 2230, 100 };
        addDataToVolume(sizes, db, cv);
        vc.setRoles(BagCacher.ROLE_SMALL_OBJECTS | BagCacher.ROLE_FAST_ACCESS);
        out.addCacheVolume(cv, 22000, null, vc, true);

        voldir = new File(cacheroot, "cranky");
        voldir.mkdir();
        cv = new FilesystemCacheVolume(voldir, "cranky");
        int[] sizesc = { 229, 6321, 953, 10031, 2230, 100 };
        addDataToVolume(sizesc, db, cv);
        vc.setRoles(BagCacher.ROLE_GENERAL_PURPOSE | BagCacher.ROLE_LARGE_OBJECTS);
        out.addCacheVolume(cv, 20000, null, vc, true);

        voldir = new File(cacheroot, "old");
        voldir.mkdir();
        cv = new FilesystemCacheVolume(voldir, "old");
        vc.setRoles(BagCacher.ROLE_OLD_VERSIONS);
        out.addCacheVolume(cv, 20000, null, vc, true);

        return out;
    }

    public long addDataToVolume(int[] sizes, StorageInventoryDB db, CacheVolume cv)
        throws InventoryException, IOException, StorageVolumeException
    {
        String volname = cv.getName();
        db.registerVolume(volname, 50000, null);

        long now = System.currentTimeMillis();

        String nm = null;
        byte[] buf = null;
        JSONObject md = null;
        long total = 0L;
        for (int i = 0; i < sizes.length; i++) {
            nm = Integer.toString(i) + ".dat";
            md = new JSONObject();
            md.put("size", sizes[i]);
            sidb.addObject(volname + ":" + nm, volname, nm, md);
            total += sizes[i];

            buf = new byte[sizes[i]];
            Arrays.fill(buf, Byte.parseByte("4"));
            cv.saveAs(new ByteArrayInputStream(buf), nm, md);

            md.put("since", now - (sizes[i] * 60000));
            sidb.updateMetadata(volname, nm, md);
        }

        return total;
    }

    @BeforeEach
    public void setUp() throws StorageVolumeException, InventoryException, IOException, CacheManagementException {
        String ltsdir = System.getProperty("project.test.resourceDirectory");
        assertNotNull(ltsdir);
        ltstore = new FilesystemLongTermStorage(ltsdir);

        mdcache = new MetadataCache(tempf.toPath().resolve("mdcache"));
        sidb = createDB();
        cache = createCache(sidb);
        cacher = new BagCacher(cache, ltstore, mdcache, 500L, LoggerFactory.getLogger("testCacher"));
    }

    @AfterEach
    public void tearDown() {
        mdcache = null;
        sidb = null;
        cache = null;
        ltstore = null;
        cacher = null;
    }

    @Test
    public void testCtor() {
        assertEquals(cache, cacher.cache);
        assertEquals(ltstore, cacher.bagstore);
        assertEquals(mdcache, cacher.mdcache);
        assertEquals(500L, cacher.smszlim);
    }

    @Test
    public void testCacheAllFromBag() throws StorageVolumeException, IOException, CacheManagementException {
        assertTrue(!cache.isCached("mds1491/trial1.json#1.1.0"));
        assertTrue(!cache.isCached("mds1491/trial2.json#1.1.0"));
        assertTrue(!cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));

        Set<String> cached = cacher.cacheFromBag("mds1491.1_1_0.mbag0_4-1.zip", null);
        assertTrue(cache.isCached("mds1491/trial1.json#1.1.0"));
        assertTrue(cache.isCached("mds1491/trial2.json#1.1.0"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));
        assertFalse(cache.isCached("mds1491/trial1.json.sha256#1.1.0"));
        assertFalse(cache.isCached("mds1491/trial2.json.sha256#1.1.0"));
        assertFalse(cache.isCached("mds1491/trial3/trial3a.json.sha256#1.1.0"));

        assertTrue(cached.contains("trial1.json"));
        assertTrue(cached.contains("trial2.json"));
        assertTrue(cached.contains("trial3/trial3a.json"));

        Path croot = tempf.toPath().resolve("cache");
        assertTrue(Files.isDirectory(croot.resolve("cranky")));
        assertTrue(Files.isRegularFile(croot.resolve("foobar/mds1491/trial1.json")));
        assertTrue(Files.isRegularFile(croot.resolve("foobar/mds1491/trial2.json")));
        assertTrue(Files.isRegularFile(croot.resolve("cranky/mds1491/trial3/trial3a.json")));

        assertEquals("foobar", cache.findObject("mds1491/trial1.json#1.1.0").volname);
        assertEquals("foobar", cache.findObject("mds1491/trial2.json#1.1.0").volname);
        assertEquals("cranky", cache.findObject("mds1491/trial3/trial3a.json#1.1.0").volname);

        croot = tempf.toPath().resolve("mdcache");
        assertFalse(Files.exists(croot.resolve("mds1491/1.1.0")));
    }

    @Test
    public void testCacheSomeFromBag()
        throws StorageVolumeException, IOException, CacheManagementException
    {
        assertTrue(! cache.isCached("mds1491/trial1.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));

        HashSet<String> select = new HashSet<String>(2);
        select.add("trial1.json");
        select.add("trial3/trial3a.json");

        Set<String> cached = cacher.cacheFromBag("mds1491.1_1_0.mbag0_4-1.zip", select);
        assertTrue(cache.isCached("mds1491/trial1.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1.1.0"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));
        assertFalse(cache.isCached("mds1491/trial1.json.sha256#1.1.0"));
        assertFalse(cache.isCached("mds1491/trial2.json.sha256#1.1.0"));
        assertFalse(cache.isCached("mds1491/trial3/trial3a.json.sha256#1.1.0"));

        assertTrue(cached.contains("trial1.json"));
        assertTrue(! cached.contains("trial2.json"));
        assertTrue(cached.contains("trial3/trial3a.json"));
        assertTrue(! select.contains("trial1.json"));
        assertTrue(! select.contains("trial2.json"));
        assertTrue(! select.contains("trial3/trial3a.json"));
        assertEquals(0, select.size());

        Path croot = tempf.toPath().resolve("cache");
        assertTrue(Files.isDirectory(croot.resolve("cranky")));
        assertTrue(Files.isRegularFile(croot.resolve("foobar/mds1491/trial1.json")));
        assertTrue(! Files.exists(croot.resolve("foobar/mds1491/trial2.json")));
        assertTrue(Files.isRegularFile(croot.resolve("cranky/mds1491/trial3/trial3a.json")));

        assertEquals("foobar", cache.findObject("mds1491/trial1.json#1.1.0").volname);
        assertNull(cache.findObject("mds1491/trial2.json#1.1.0"));
        assertEquals("cranky", cache.findObject("mds1491/trial3/trial3a.json#1.1.0").volname);

        croot = tempf.toPath().resolve("mdcache");
        assertFalse(Files.exists(croot.resolve("mds1491/1.1.0")));
    }

    @Test
    public void testCacheMetadataFromBag() 
        throws StorageVolumeException, IOException, CacheManagementException
    {
        Path croot = tempf.toPath().resolve("mdcache");
        assertFalse(Files.exists(croot.resolve("mds1491/1.1.0")));
        assertTrue(! cache.isCached("mds1491/trial1.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));

        String headbag = "mds1491.1_1_0.mbag0_4-1.zip";
        InputStream fs = ltstore.openFile(headbag);
        cacher.cacheFromBag("mds1491", "1.1.0", fs, "zip", 0, null, null);

        assertTrue(! cache.isCached("mds1491/trial1.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));
        assertTrue(Files.isDirectory(croot.resolve("mds1491/1.1.0")));
        assertTrue(Files.isRegularFile(croot.resolve("mds1491/1.1.0/trial1.json.json")));
        assertTrue(Files.isRegularFile(croot.resolve("mds1491/1.1.0/trial2.json.json")));
        assertTrue(Files.isRegularFile(croot.resolve("mds1491/1.1.0/trial3:trial3a.json.json")));
    }

    @Test
    public void testCacheDataset()
        throws CacheManagementException, StorageVolumeException, ResourceNotFoundException
    {
        assertTrue(! cache.isCached("mds1491/trial1.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1.1.0"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));

        Set<String> cached = cacher.cacheDataset("mds1491");

        assertTrue(cached.contains("trial1.json"));
        assertTrue(cached.contains("trial2.json"));
        assertTrue(cached.contains("trial3/trial3a.json"));

        assertTrue(cache.isCached("mds1491/trial1.json#1.1.0"));
        assertTrue(cache.isCached("mds1491/trial2.json#1.1.0"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json#1.1.0"));
        assertFalse(cache.isCached("mds1491/trial1.json.sha256#1.1.0"));
        assertFalse(cache.isCached("mds1491/trial2.json.sha256#1.1.0"));
        assertFalse(cache.isCached("mds1491/trial3/trial3a.json.sha256#1.1.0"));

        Path croot = tempf.toPath().resolve("cache");
        assertTrue(Files.isDirectory(croot.resolve("cranky")));
        assertTrue(Files.isRegularFile(croot.resolve("foobar/mds1491/trial1.json")));
        assertTrue(Files.isRegularFile(croot.resolve("foobar/mds1491/trial2.json")));
        assertTrue(Files.isRegularFile(croot.resolve("cranky/mds1491/trial3/trial3a.json")));

        assertEquals("foobar", cache.findObject("mds1491/trial1.json#1.1.0").volname);
        assertEquals("foobar", cache.findObject("mds1491/trial2.json#1.1.0").volname);
        assertEquals("cranky", cache.findObject("mds1491/trial3/trial3a.json#1.1.0").volname);

        List<CacheObject> cos = null;
        // JSONObject md = null;
        cos = sidb.findObject("mds1491/trial1.json#1.1.0");
        assertEquals(1, cos.size());   // there's only one copy cached
        assertTrue(cos.get(0).getMetadatumString("checksum", "").startsWith("d155d9928"));
        assertEquals("sha256", cos.get(0).getMetadatumString("checksumAlgorithm", ""));
        assertEquals("mds1491.1_1_0.mbag0_4-1", cos.get(0).getMetadatumString("bagfile", ""));
        assertEquals("application/json", cos.get(0).getMetadatumString("contentType", ""));
        assertEquals("1.1.0", cos.get(0).getMetadatumString("version", ""));
        assertEquals(69L, cos.get(0).getMetadatumLong("size", -1L));
        
        cos = sidb.findObject("mds1491/trial2.json#1.1.0");
        assertEquals(1, cos.size());   // there's only one copy cached
        assertTrue(cos.get(0).getMetadatumString("checksum", "").startsWith("d5eed5092"));
        assertEquals("sha256", cos.get(0).getMetadatumString("checksumAlgorithm", ""));
        assertEquals("mds1491.mbag0_2-0", cos.get(0).getMetadatumString("bagfile", ""));
        assertEquals("application/json", cos.get(0).getMetadatumString("contentType", ""));
        assertEquals("1.1.0", cos.get(0).getMetadatumString("version", ""));
        assertEquals(69L, cos.get(0).getMetadatumLong("size", -1L));
        
        cos = sidb.findObject("mds1491/trial3/trial3a.json#1.1.0");
        assertEquals(1, cos.size());   // there's only one copy cached
        assertTrue(cos.get(0).getMetadatumString("checksum", "").startsWith("ccf6d9df9"));
        assertEquals("sha256", cos.get(0).getMetadatumString("checksumAlgorithm", ""));
        assertEquals("mds1491.1_1_0.mbag0_4-1", cos.get(0).getMetadatumString("bagfile", ""));
        assertEquals("application/json", cos.get(0).getMetadatumString("contentType", ""));
        assertEquals("1.1.0", cos.get(0).getMetadatumString("version", ""));
        assertEquals(553L, cos.get(0).getMetadatumLong("size", -1L));
    }

    @Test
    public void testCacheOldDataset()
        throws CacheManagementException, StorageVolumeException, ResourceNotFoundException
    {
        assertTrue(! cache.isCached("mds1491/trial1.json#1.0.0"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1.0.0"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1.0.0"));
        assertTrue(! cache.isCached("mds1491/trial1.json#1"));
        assertTrue(! cache.isCached("mds1491/trial2.json#1"));
        assertTrue(! cache.isCached("mds1491/trial3/trial3a.json#1"));

        Set<String> cached = cacher.cacheDataset("mds1491", "1");

        assertTrue(cached.contains("trial1.json"));
        assertTrue(cached.contains("trial2.json"));
        assertTrue(cached.contains("trial3/trial3a.json"));

        assertTrue(cache.isCached("mds1491/trial1.json#1"));
        assertTrue(cache.isCached("mds1491/trial2.json#1"));
        assertTrue(cache.isCached("mds1491/trial3/trial3a.json#1"));
        assertFalse(cache.isCached("mds1491/trial1.json.sha256#1.0.0"));
        assertFalse(cache.isCached("mds1491/trial2.json.sha256#1.0.0"));
        assertFalse(cache.isCached("mds1491/trial3/trial3a.json.sha256#1.0.0"));

        Path croot = tempf.toPath().resolve("cache");
        assertTrue(Files.isDirectory(croot.resolve("cranky")));
        assertTrue(Files.isRegularFile(croot.resolve("old/mds1491/trial1-v1.json")));
        assertTrue(Files.isRegularFile(croot.resolve("old/mds1491/trial2-v1.json")));
        assertTrue(Files.isRegularFile(croot.resolve("old/mds1491/trial3/trial3a-v1.json")));

        assertEquals("old", cache.findObject("mds1491/trial1.json#1").volname);
        assertEquals("old", cache.findObject("mds1491/trial2.json#1").volname);
        assertEquals("old", cache.findObject("mds1491/trial3/trial3a.json#1").volname);

        List<CacheObject> cos = null;
        // JSONObject md = null;
        cos = sidb.findObject("mds1491/trial1.json#1");
        assertEquals(1, cos.size());   // there's only one copy cached
        assertTrue(cos.get(0).getMetadatumString("checksum", "").startsWith("d155d9928"));
        assertEquals("sha256", cos.get(0).getMetadatumString("checksumAlgorithm", ""));
        assertEquals("mds1491.mbag0_2-0", cos.get(0).getMetadatumString("bagfile", ""));
        assertEquals("application/json", cos.get(0).getMetadatumString("contentType", ""));
        assertEquals("1", cos.get(0).getMetadatumString("version", ""));
        assertEquals(69L, cos.get(0).getMetadatumLong("size", -1L));
        
        cos = sidb.findObject("mds1491/trial2.json#1");
        assertEquals(1, cos.size());   // there's only one copy cached
        assertTrue(cos.get(0).getMetadatumString("checksum", "").startsWith("d5eed5092"));
        assertEquals("sha256", cos.get(0).getMetadatumString("checksumAlgorithm", ""));
        assertEquals("mds1491.mbag0_2-0", cos.get(0).getMetadatumString("bagfile", ""));
        assertEquals("application/json", cos.get(0).getMetadatumString("contentType", ""));
        assertEquals("1", cos.get(0).getMetadatumString("version", ""));
        assertEquals(69L, cos.get(0).getMetadatumLong("size", -1L));
        
        cos = sidb.findObject("mds1491/trial3/trial3a.json#1");
        assertEquals(1, cos.size());   // there's only one copy cached
        assertTrue(cos.get(0).getMetadatumString("checksum", "").startsWith("7b58010c"));
        assertEquals("sha256", cos.get(0).getMetadatumString("checksumAlgorithm", ""));
        assertEquals("mds1491.mbag0_2-0", cos.get(0).getMetadatumString("bagfile", ""));
        assertEquals("application/json", cos.get(0).getMetadatumString("contentType", ""));
        assertEquals("1", cos.get(0).getMetadatumString("version", ""));
        assertEquals(70, cos.get(0).getMetadatumLong("size", -1L));
    }

    @Test
    public void testGetIdForCache() {
        assertEquals("pdr0-goober/foo/bar/data.txt#2.0",
                     cacher.getIdForCache("pdr0-goober", "foo/bar/data.txt", "2.0"));
    }

    @Test
    public void testGetNameForCache() {
        assertEquals("pdr0-goober/foo/bar/data.txt",
                     cacher.getNameForCache("pdr0-goober", "foo/bar/data.txt", "2.0", 0));
        assertEquals("pdr0-goober/foo/bar/data-v2.0.txt",
                     cacher.getNameForCache("pdr0-goober", "foo/bar/data.txt", "2.0",
                     PDRCacheRoles.ROLE_OLD_VERSIONS));
        assertEquals("pdr0-goober/foo/bar/data-v2.0.txt.XZ",
                     cacher.getNameForCache("pdr0-goober", "foo/bar/data.txt.XZ", "2.0",
                     PDRCacheRoles.ROLE_OLD_VERSIONS));
    }
}
