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

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.ClassRule;
import org.junit.rules.TestRule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import gov.nist.oar.distrib.storage.WebLongTermStorage;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.DeletionPlan;
import gov.nist.oar.distrib.cachemgr.DeletionPlanner;
import gov.nist.oar.distrib.cachemgr.inventory.DefaultDeletionPlanner;
import gov.nist.oar.distrib.cachemgr.inventory.OldSelectionStrategy;
import gov.nist.oar.distrib.cachemgr.SelectionStrategy;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.Reservation;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.CacheManager;
import gov.nist.oar.distrib.cachemgr.Restorer;
import gov.nist.oar.distrib.cachemgr.restore.FileCopyRestorer;
import gov.nist.oar.distrib.cachemgr.restore.ZipFileRestorer;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;

import gov.nist.oar.distrib.RequireWebSite;
import gov.nist.oar.distrib.EnvVarIncludesWords;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;

/*
 * tests BasicCache, too
 */
public class SimpleCacheManagerZipTest {

    @Rule
    public final TemporaryFolder tempf = new TemporaryFolder();

    @ClassRule
    public static TestRule siterule = new RequireWebSite("http://archive.apache.org/dist/commons/lang/binaries/commons-lang3-3.4-bin.zip.md5");

    @ClassRule
    public static TestRule envrule = new EnvVarIncludesWords("OAR_TEST_INCLUDE", "net");

    String aipbase = "http://archive.apache.org/dist/commons/lang/";
    Logger log = LoggerFactory.getLogger(getClass());
    public SimpleCacheManagerZipTest() {
        ch.qos.logback.classic.Logger log =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(WebLongTermStorage.class);
        log.setLevel(Level.DEBUG);
    }
  
    File dbf = null;
    File repodir = null;
    StorageInventoryDB sidb = null;
    List<CacheVolume> cvlist = null;
    SimpleCache cache = null;

    String createDB() throws IOException, InventoryException {
        File tf = tempf.newFile("testdb.sqlite");
        String out = tf.getAbsolutePath();
        SQLiteStorageInventoryDB.initializeDB(out);
        return out;
    }

    @After
    public void tearDown() {
        dbf.delete();
    }

    @Before
    public void setUp() throws IOException, InventoryException {
        dbf = new File(createDB());
        assertTrue(dbf.exists());

        sidb = new SQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");

        cvlist = new ArrayList<CacheVolume>(2);
        for(int i=0; i < 3; i++) {
            String name = "Cache"+Integer.toString(i);
            File cd = tempf.newFolder(name);
            CacheVolume cv = new FilesystemCacheVolume(cd.toString(), name);
            sidb.registerVolume(name, 1200000, null);
            cvlist.add(cv);
        }

        OldSelectionStrategy ss = new OldSelectionStrategy(1200000, 0, 0);
        DeletionPlanner dp = new DefaultDeletionPlanner(sidb, cvlist, ss);
        cache = new SimpleCache(sidb, cvlist, dp);
    }

    @Test
    public void testCtor() throws IOException, CacheManagementException {
        LongTermStorage lts = new WebLongTermStorage(aipbase, "md5");
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));
        for(Long space : sidb.getAvailableSpace().values()) {
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
        assertTrue("Unexpected cache name: "+co.volname, co.volname.startsWith("Cache"));
        assertEquals("source/commons-lang3-3.7-src.tar.gz", co.name);

        assertEquals(862725, co.getSize());
        assertEquals("ad3206706dddc694b310630449f6a2f2",
                     co.getMetadatumString("checksum", "not-set"));

        File cf = new File(new File(tempf.getRoot(), co.volname), "source/commons-lang3-3.7-src.tar.gz");
        assertTrue("Can't find cache file", cf.exists());
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
                File cf = new File(new File(tempf.getRoot(), co.volname), co.name);
                assertTrue("Can't find cache file", cf.exists());
            }
        }
        assertEquals(3, ncached);

        for(Long space : sidb.getAvailableSpace().values()) {
            assertNotEquals(120000L, space.longValue());
        }
        
    }
}
