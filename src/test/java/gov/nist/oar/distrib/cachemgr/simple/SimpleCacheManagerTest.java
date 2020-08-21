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
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;
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

import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

/*
 * tests BasicCache, too
 */
public class SimpleCacheManagerTest {

    @Rule
    public final TemporaryFolder tempf = new TemporaryFolder();

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
            sidb.registerVolume(name, 120000, null);
            cvlist.add(cv);
        }

        OldSelectionStrategy ss = new OldSelectionStrategy(120000, 120000, 0, 0);
        DeletionPlanner dp = new DefaultDeletionPlanner(sidb, cvlist, ss);
        cache = new SimpleCache("Simple", sidb, cvlist, dp);
    }

    public File createRepo() throws IOException {
        repodir = tempf.newFolder("repo");
        String[] zips = { "mds1491.mbag0_2-0.zip", "mds1491.1_1_0.mbag0_4-1.zip",
                          "67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip",
                          "67C783D4BA814C8EE05324570681708A1899.mbag0_3-1.zip"     };
        for (String file : zips) {
            Files.copy(ZipRepoRestorerTest.class.getResourceAsStream("/"+file),
                       (new File(repodir, file)).toPath());
            Files.copy(ZipRepoRestorerTest.class.getResourceAsStream("/"+file+".sha256"),
                       (new File(repodir, file+".sha256")).toPath());
        }
        Files.copy(ZipRepoRestorerTest.class.getResourceAsStream("/67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip"),
                   (new File(repodir, "goober.zip")).toPath());
        Files.copy(ZipRepoRestorerTest.class.getResourceAsStream("/67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip.sha256"),
                   (new File(repodir, "goober.zip.sha256")).toPath());

        return repodir;
    }

    @Test
    public void testCtor() throws IOException, CacheManagementException {
        LongTermStorage lts = new FilesystemLongTermStorage(createRepo().toString());
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));
        for(Long space : sidb.getAvailableSpace().values()) {
            assertEquals(120000L, space.longValue());
        }
        assertFalse(scm.isCached("mds1491.mbag0_2-0.zip"));
        scm.uncache("mds1491.mbag0_2-0.zip");
    }

    @Test
    public void testCache() throws IOException, CacheManagementException {
        LongTermStorage lts = new FilesystemLongTermStorage(createRepo().toString());
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));

        assertFalse(scm.isCached("mds1491.mbag0_2-0.zip"));

        assertTrue(scm.cache("mds1491.mbag0_2-0.zip"));
        assertTrue(scm.isCached("mds1491.mbag0_2-0.zip"));
        assertFalse(scm.cache("mds1491.mbag0_2-0.zip"));
        assertTrue(scm.isCached("mds1491.mbag0_2-0.zip"));
        assertFalse(scm.cache("mds1491.mbag0_2-0.zip", false));
        assertTrue(scm.isCached("mds1491.mbag0_2-0.zip"));
        assertTrue(scm.cache("mds1491.mbag0_2-0.zip", true));
        assertTrue(scm.isCached("mds1491.mbag0_2-0.zip"));

        scm.uncache("mds1491.mbag0_2-0.zip");
        assertFalse(scm.isCached("mds1491.mbag0_2-0.zip"));
    }

    @Test
    public void testFindObject() throws IOException, CacheManagementException {
        LongTermStorage lts = new FilesystemLongTermStorage(createRepo().toString());
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));

        assertFalse(scm.isCached("mds1491.mbag0_2-0.zip"));

        assertTrue(scm.cache("mds1491.mbag0_2-0.zip"));
        assertTrue(scm.isCached("mds1491.mbag0_2-0.zip"));

        CacheObject co = scm.findObject("mds1491.mbag0_2-0.zip");
        assertTrue("Unexpected cache name: "+co.volname, co.volname.startsWith("Cache"));
        assertEquals("mds1491.mbag0_2-0.zip", co.name);

        assertEquals(9841, co.getSize());
        assertEquals("3de9d9e32831be693e341306db79e636ebd61b6a78f9482e8e3038a6e8eba569",
                     co.getMetadatumString("checksum", "not-set"));

        File cf = new File(new File(tempf.getRoot(), co.volname), "mds1491.mbag0_2-0.zip");
        assertTrue("Can't find cache file", cf.exists());
    }

    @Test
    public void testGetObject() throws IOException, CacheManagementException {
        LongTermStorage lts = new FilesystemLongTermStorage(createRepo().toString());
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));

        assertFalse(scm.isCached("mds1491.mbag0_2-0.zip"));
        CacheObject co = scm.getObject("mds1491.mbag0_2-0.zip");
        assertTrue(scm.isCached("mds1491.mbag0_2-0.zip"));

        File cf = new File(new File(tempf.getRoot(), co.volname), "mds1491.mbag0_2-0.zip");
        assertTrue("Can't find cache file", cf.exists());
    }

    @Test
    public void testFill1() throws IOException, CacheManagementException {
        LongTermStorage lts = new FilesystemLongTermStorage(createRepo().toString());
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));

        String[] zips = { "mds1491.mbag0_2-0.zip", "mds1491.1_1_0.mbag0_4-1.zip",
                          "67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip",
                          "67C783D4BA814C8EE05324570681708A1899.mbag0_3-1.zip"     };
        testFill(scm, zips);

        CacheObject co = scm.getObject("goober.zip");
        assertTrue(scm.isCached("goober.zip"));

        File cf = new File(new File(tempf.getRoot(), co.volname), "goober.zip");
        assertTrue("Can't find cache file", cf.exists());

        // something had to have gotten deleted
        int i = 0;
        for (i=0; i < zips.length; i++) {
            if (! scm.isCached(zips[i])) break;
        }
        assertNotEquals(zips.length, i);

    }

    @Test
    public void testFill2() throws IOException, CacheManagementException {
        LongTermStorage lts = new FilesystemLongTermStorage(createRepo().toString());
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));

        String[] zips = { "67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip",
                          "mds1491.1_1_0.mbag0_4-1.zip",
                          "67C783D4BA814C8EE05324570681708A1899.mbag0_3-1.zip",
                          "mds1491.mbag0_2-0.zip"                               };
        testFill(scm, zips);
    }

    
    public void testFill(CacheManager scm, String[] zips) throws IOException, CacheManagementException {
        assertFalse(scm.isCached("mds1491.mbag0_2-0.zip"));

        for (String file : zips) {
            CacheObject co = scm.getObject(file);
            assertTrue(scm.isCached(co.id));
        }

        for (String file : zips) {
            CacheObject co = scm.getObject(file);
            assertTrue(scm.isCached(co.id));

            File cf = new File(new File(tempf.getRoot(), co.volname), co.name);
            assertTrue("Can't find cache file", cf.exists());
        }

        for(Long space : sidb.getAvailableSpace().values()) {
            assertNotEquals(120000L, space.longValue());
        }
        
    }
}


