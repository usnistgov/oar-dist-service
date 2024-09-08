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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheManager;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.inventory.DefaultDeletionPlanner;
import gov.nist.oar.distrib.cachemgr.inventory.OldSelectionStrategy;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.restore.FileCopyRestorer;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

class SimpleCacheManagerTest {

    @TempDir
    File tempDir;  // JUnit 5's @TempDir for managing temporary files

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
    void tearDown() {
        if (dbf != null) {
            dbf.delete();
        }
    }

    @BeforeEach
    void setUp() throws IOException, InventoryException {
        dbf = new File(createDB());
        assertTrue(dbf.exists());

        sidb = new SQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");

        cvlist = new ArrayList<>(2);
        for (int i = 0; i < 3; i++) {
            String name = "Cache" + i;
            File cd = new File(tempDir, name);
            CacheVolume cv = new FilesystemCacheVolume(cd.toString(), name);
            sidb.registerVolume(name, 120000, null);
            cvlist.add(cv);
        }

        OldSelectionStrategy ss = new OldSelectionStrategy(120000, 120000, 0, 0);
        cache = new SimpleCache("Simple", sidb, cvlist, new DefaultDeletionPlanner(sidb, cvlist, ss));
    }

    File createRepo() throws IOException {
        repodir = new File(tempDir, "repo");
        repodir.mkdir();

        String[] zips = {
            "mds1491.mbag0_2-0.zip", "mds1491.1_1_0.mbag0_4-1.zip",
            "67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip",
            "67C783D4BA814C8EE05324570681708A1899.mbag0_3-1.zip"
        };

        for (String file : zips) {
            Files.copy(ZipRepoRestorerTest.class.getResourceAsStream("/" + file),
                       new File(repodir, file).toPath());
            Files.copy(ZipRepoRestorerTest.class.getResourceAsStream("/" + file + ".sha256"),
                       new File(repodir, file + ".sha256").toPath());
        }

        Files.copy(ZipRepoRestorerTest.class.getResourceAsStream("/67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip"),
                   new File(repodir, "goober.zip").toPath());
        Files.copy(ZipRepoRestorerTest.class.getResourceAsStream("/67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip.sha256"),
                   new File(repodir, "goober.zip.sha256").toPath());

        return repodir;
    }

    @Test
    void testCtor() throws IOException, CacheManagementException {
        LongTermStorage lts = new FilesystemLongTermStorage(createRepo().toString());
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));

        for (Long space : sidb.getAvailableSpace().values()) {
            assertEquals(120000L, space.longValue());
        }

        assertFalse(scm.isCached("mds1491.mbag0_2-0.zip"));
        scm.uncache("mds1491.mbag0_2-0.zip");
    }

    @Test
    void testCache() throws IOException, CacheManagementException {
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
    void testFindObject() throws IOException, CacheManagementException {
        LongTermStorage lts = new FilesystemLongTermStorage(createRepo().toString());
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));

        assertFalse(scm.isCached("mds1491.mbag0_2-0.zip"));

        assertTrue(scm.cache("mds1491.mbag0_2-0.zip"));
        assertTrue(scm.isCached("mds1491.mbag0_2-0.zip"));

        CacheObject co = scm.findObject("mds1491.mbag0_2-0.zip");
        assertTrue(co.volname.startsWith("Cache"), "Unexpected cache name: " + co.volname);
        assertEquals("mds1491.mbag0_2-0.zip", co.name);
        assertNotNull(co.volume);

        assertEquals(9841, co.getSize());
        assertEquals("3de9d9e32831be693e341306db79e636ebd61b6a78f9482e8e3038a6e8eba569",
                     co.getMetadatumString("checksum", "not-set"));

        File cf = new File(new File(tempDir, co.volname), "mds1491.mbag0_2-0.zip");
        assertTrue(cf.exists(), "Can't find cache file");

        long accesstime = co.getMetadatumLong("since", -1L);
        assertTrue(accesstime > 0);
        scm.confirmAccessOf(co);
        co = scm.findObject("mds1491.mbag0_2-0.zip");
        assertTrue(accesstime < co.getMetadatumLong("since", -1L));
    }

    @Test
    void testGetObject() throws IOException, CacheManagementException {
        LongTermStorage lts = new FilesystemLongTermStorage(createRepo().toString());
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));

        assertFalse(scm.isCached("mds1491.mbag0_2-0.zip"));
        CacheObject co = scm.getObject("mds1491.mbag0_2-0.zip");
        assertTrue(scm.isCached("mds1491.mbag0_2-0.zip"));

        File cf = new File(new File(tempDir, co.volname), "mds1491.mbag0_2-0.zip");
        assertTrue(cf.exists(), "Can't find cache file");
    }

    @Test
    void testFill1() throws IOException, CacheManagementException {
        LongTermStorage lts = new FilesystemLongTermStorage(createRepo().toString());
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));

        String[] zips = {
            "mds1491.mbag0_2-0.zip", "mds1491.1_1_0.mbag0_4-1.zip",
            "67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip",
            "67C783D4BA814C8EE05324570681708A1899.mbag0_3-1.zip"
        };
        testFill(scm, zips);

        CacheObject co = scm.getObject("goober.zip");
        assertTrue(scm.isCached("goober.zip"));

        File cf = new File(new File(tempDir, co.volname), "goober.zip");
        assertTrue(cf.exists(), "Can't find cache file");

        // something had to have gotten deleted
        int i = 0;
        for (i = 0; i < zips.length; i++) {
            if (!scm.isCached(zips[i])) break;
        }
        assertNotEquals(zips.length, i);
    }

    @Test
    void testFill2() throws IOException, CacheManagementException {
        LongTermStorage lts = new FilesystemLongTermStorage(createRepo().toString());
        SimpleCacheManager scm = new SimpleCacheManager(cache, new FileCopyRestorer(lts));

        String[] zips = {
            "67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip",
            "mds1491.1_1_0.mbag0_4-1.zip",
            "67C783D4BA814C8EE05324570681708A1899.mbag0_3-1.zip",
            "mds1491.mbag0_2-0.zip"
        };
        testFill(scm, zips);
    }

    void testFill(CacheManager scm, String[] zips) throws IOException, CacheManagementException {
        assertFalse(scm.isCached("mds1491.mbag0_2-0.zip"));

        for (String file : zips) {
            CacheObject co = scm.getObject(file);
            assertTrue(scm.isCached(co.id));
        }

        for (String file : zips) {
            CacheObject co = scm.getObject(file);
            assertTrue(scm.isCached(co.id));

            File cf = new File(new File(tempDir, co.volname), co.name);
            assertTrue(cf.exists(), "Can't find cache file");
        }

        for (Long space : sidb.getAvailableSpace().values()) {
            assertNotEquals(120000L, space.longValue());
        }
    }
}
