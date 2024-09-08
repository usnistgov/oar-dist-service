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
 */
package gov.nist.oar.distrib.cachemgr.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheManager;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.DeletionPlanner;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.inventory.DefaultDeletionPlanner;
import gov.nist.oar.distrib.cachemgr.inventory.OldSelectionStrategy;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.restore.FileCopyRestorer;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.storage.WebLongTermStorage;

public class SimpleCacheManagerZipTest {

    @TempDir
    File tempDir;  // JUnit 5's TempDir for temporary folder

    String aipbase = "http://archive.apache.org/dist/commons/lang/";
    Logger log = LoggerFactory.getLogger(getClass());

    File dbf = null;
    File repodir = null;
    StorageInventoryDB sidb = null;
    List<CacheVolume> cvlist = null;
    SimpleCache cache = null;

    String createDB() throws IOException, InventoryException {
        File tf = new File(tempDir, "testdb.sqlite");
        String out = tf.getAbsolutePath();
        SQLiteStorageInventoryDB.initializeDB(out);
        return out;
    }

    @AfterEach
    public void tearDown() {
        if (dbf != null) {
            dbf.delete();
        }
    }

    @BeforeEach
    public void setUp() throws IOException, InventoryException {
        dbf = new File(createDB());
        assertTrue(dbf.exists());

        sidb = new SQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");

        cvlist = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            String name = "Cache" + i;
            File cd = new File(tempDir, name);
            CacheVolume cv = new FilesystemCacheVolume(cd.toString(), name);
            sidb.registerVolume(name, 1200000, null);
            cvlist.add(cv);
        }

        OldSelectionStrategy ss = new OldSelectionStrategy(1200000, 1200000, 0, 0);
        DeletionPlanner dp = new DefaultDeletionPlanner(sidb, cvlist, ss);
        cache = new SimpleCache("Zippy", sidb, cvlist, dp);
    }

    @Test
    public void testCtor() throws IOException, CacheManagementException {
        LongTermStorage lts = new WebLongTermStorage(aipbase, "md5");
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));

        for (Long space : sidb.getAvailableSpace().values()) {
            assertEquals(1200000L, space.longValue());
        }
        assertFalse(scm.isCached("RELEASE-NOTES.txt"));
        scm.uncache("RELEASE-NOTES.txt");
    }

    @Test
    public void testCache() throws IOException, CacheManagementException {
        LongTermStorage lts = new WebLongTermStorage(aipbase, "md5");
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));

        assertFalse(scm.isCached("source/commons-lang3-3.7-src.tar.gz"));

        assertTrue(scm.cache("source/commons-lang3-3.7-src.tar.gz"));
        assertTrue(scm.isCached("source/commons-lang3-3.7-src.tar.gz"));
        assertFalse(scm.cache("source/commons-lang3-3.7-src.tar.gz"));
        assertTrue(scm.isCached("source/commons-lang3-3.7-src.tar.gz"));
        assertFalse(scm.cache("source/commons-lang3-3.7-src.tar.gz", false));
        assertTrue(scm.isCached("source/commons-lang3-3.7-src.tar.gz"));
        assertTrue(scm.cache("source/commons-lang3-3.7-src.tar.gz", true));
        assertTrue(scm.isCached("source/commons-lang3-3.7-src.tar.gz"));

        scm.uncache("source/commons-lang3-3.7-src.tar.gz");
        assertFalse(scm.isCached("source/commons-lang3-3.7-src.tar.gz"));
    }

    @Test
    public void testFindObject() throws IOException, CacheManagementException {
        LongTermStorage lts = new WebLongTermStorage(aipbase, "md5");
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));

        assertFalse(scm.isCached("source/commons-lang3-3.7-src.tar.gz"));

        assertTrue(scm.cache("source/commons-lang3-3.7-src.tar.gz"));
        assertTrue(scm.isCached("source/commons-lang3-3.7-src.tar.gz"));

        CacheObject co = scm.findObject("source/commons-lang3-3.7-src.tar.gz");
        assertTrue(co.volname.startsWith("Cache"), "Unexpected cache name: " + co.volname);
        assertEquals("source/commons-lang3-3.7-src.tar.gz", co.name);
        assertEquals(862725, co.getSize());
        assertEquals("ad3206706dddc694b310630449f6a2f2", co.getMetadatumString("checksum", "not-set"));

        File cf = new File(new File(tempDir, co.volname), "source/commons-lang3-3.7-src.tar.gz");
        assertTrue(cf.exists(), "Can't find cache file");
    }

    @Test
    public void testFill2() throws IOException, CacheManagementException {
        LongTermStorage lts = new WebLongTermStorage(aipbase, "md5");
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));

        String[] zips = {
            "source/commons-lang3-3.7-src.tar.gz",
            "source/commons-lang3-3.6-src.tar.gz",
            "source/commons-lang3-3.5-src.tar.gz",
            "source/commons-lang3-3.4-src.tar.gz",
            "source/commons-lang3-3.3-src.tar.gz",
        };
        testFill(scm, zips);
    }

    public void testFill(CacheManager scm, String[] zips) throws IOException, CacheManagementException {
        for (String file : zips) {
            CacheObject co = scm.getObject(file);
            assertTrue(scm.isCached(co.id));
        }

        /*
         * we're over filling, so some files will be knocked out
         */
        int ncached = 0;
        for (String file : zips) {
            if (scm.isCached(file)) {
                ncached++;
                CacheObject co = scm.findObject(file);
                File cf = new File(new File(tempDir, co.volname), co.name);
                assertTrue(cf.exists(), "Can't find cache file");
            }
        }
        assertEquals(3, ncached);

        for (Long space : sidb.getAvailableSpace().values()) {
            assertNotEquals(120000L, space.longValue());
        }
    }
}
