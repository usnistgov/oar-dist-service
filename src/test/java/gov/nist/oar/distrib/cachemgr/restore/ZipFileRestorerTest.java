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
package gov.nist.oar.distrib.cachemgr.restore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.FileSystemUtils;

import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.Reservation;
import gov.nist.oar.distrib.cachemgr.RestorationException;
import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

public class ZipFileRestorerTest {

    static Path testdir = null;
    static File storeroot = null;
    static LongTermStorage lts = null;

    @BeforeAll
    public static void setUp() throws IOException {
        // create a test directory where we can write stuff
        Path indir = Paths.get(System.getProperty("user.dir"));
        if (indir.toFile().canWrite())
            testdir = Files.createTempDirectory(indir, "_unittest");
        else
            testdir = Files.createTempDirectory("_unittest");
        storeroot = new File(testdir.toFile(), "ltstore");
        storeroot.mkdirs();

        // setup a little repo
        Files.copy(ZipFileRestorerTest.class.getResourceAsStream("/mds1491.mbag0_2-0.zip"),
                (new File(storeroot, "mds1491.mbag0_2-0.zip")).toPath());

        lts = new FilesystemLongTermStorage(storeroot.getAbsolutePath());
    }

    @AfterAll
    public static void tearDown() {
        FileSystemUtils.deleteRecursively(testdir.toFile());
        testdir = null;
    }

    @Test
    public void testDoesNotExist() throws StorageVolumeException {
        ZipFileRestorer rest = new ZipFileRestorer(lts, "mds1491.mbag0_2-0.zip", "id:");
        assertTrue(rest.doesNotExist("gurn/goober.txt"));
        assertFalse(rest.doesNotExist("id:gurn/goober.txt"));
        assertFalse(rest.doesNotExist("id:mds1491.mbag0_2-0/bagit.txt"));
        assertFalse(rest.doesNotExist("id:mds1491.mbag0_2-0/data"));
        assertFalse(rest.doesNotExist("id:mds1491.mbag0_2-0/data/trial1.json"));
    }

    @Test
    public void testGetSizeOf() throws StorageVolumeException {
        ZipFileRestorer rest = new ZipFileRestorer(lts, "mds1491.mbag0_2-0.zip");
        assertEquals(69, rest.getSizeOf("mds1491.mbag0_2-0/data/trial1.json"));
        assertEquals(1562, rest.getSizeOf("mds1491.mbag0_2-0/preserv.log"));
        try {
            rest.getSizeOf("goober!");
            fail("Failed to raise exception for missing file");
        } catch (ObjectNotFoundException ex) {
            // success!
        }
    }

    @Test
    public void testGetChecksum() throws StorageVolumeException {
        ZipFileRestorer rest = new ZipFileRestorer(lts, "mds1491.mbag0_2-0.zip", "");
        assertEquals("ab671096", rest.getChecksum("mds1491.mbag0_2-0/data/trial1.json").hash);

        try {
            rest.getChecksum("goober!");
            fail("Failed to raise exception for missing file");
        } catch (ObjectNotFoundException ex) {
            // success!
        }
    }

    @Test
    public void testRestoreObject() throws RestorationException, StorageVolumeException, JSONException, InventoryException, IOException {
        File cvdir = new File(testdir.toFile(), "cache");
        cvdir.mkdirs();
        CacheVolume cv = new FilesystemCacheVolume(cvdir.toString());
        assertFalse(cv.exists("preservation.log"));

        String dbf = (new File(testdir.toFile(), "sidb.sqlite3")).toString();
        SQLiteStorageInventoryDB.initializeDB(dbf);
        StorageInventoryDB db = new SQLiteStorageInventoryDB(dbf);
        db.registerVolume(cv.getName(), 20000000, null); // 20 MB capacity
        db.registerAlgorithm(Checksum.CRC32);
        db.registerAlgorithm(Checksum.SHA256);

        Reservation resv = Reservation.reservationFor(cv, db, 1562);
        JSONObject md = new JSONObject();
        md.put("priority", 5);

        ZipFileRestorer rest = new ZipFileRestorer(lts, "mds1491.mbag0_2-0.zip");
        rest.restoreObject("mds1491.mbag0_2-0/preserv.log", resv, "preservation.log", md);

        assertTrue((new File(cvdir, "preservation.log")).isFile());
        assertTrue(cv.exists("preservation.log"));
        assertEquals(1562, db.findObject(cv.getName(), "preservation.log").getSize());
        assertEquals(5, db.findObject(cv.getName(), "preservation.log").getMetadatumInt("priority", 11));
        assertEquals("77237883", db.findObject(cv.getName(), "preservation.log").getMetadatumString("checksum", ""));
        assertEquals(0L, resv.getSize());
        resv.drop();

        resv = Reservation.reservationFor(cv, db, 1562);
        rest.restoreObject("mds1491.mbag0_2-0/bag-info.txt", resv, "preservation.log", null);
        assertTrue((new File(cvdir, "preservation.log")).isFile());
        assertTrue(cv.exists("preservation.log"));
        assertEquals(625, db.findObject(cv.getName(), "preservation.log").getSize());
        assertEquals(10, db.findObject(cv.getName(), "preservation.log").getMetadatumInt("priority", 11));
        assertEquals("cb1bc46f", db.findObject(cv.getName(), "preservation.log").getMetadatumString("checksum", ""));
        assertEquals(937L, resv.getSize());
        resv.drop();

        resv = Reservation.reservationFor(cv, db, 1562);
        rest.restoreObject("mds1491.mbag0_2-0/data/trial3/trial3a.json", resv, "data/trial3::trial3a.json", null);
        assertTrue((new File(cvdir, "data/trial3::trial3a.json")).isFile());
        assertTrue(cv.exists("data/trial3::trial3a.json"));
        assertEquals(70, db.findObject(cv.getName(), "data/trial3::trial3a.json").getSize());
        assertEquals(10, db.findObject(cv.getName(), "data/trial3::trial3a.json").getMetadatumInt("priority", 11));
        assertEquals("c6723c3a", db.findObject(cv.getName(), "data/trial3::trial3a.json").getMetadatumString("checksum", ""));
        assertEquals(1492L, resv.getSize());
        resv.drop();

        assertTrue(cv.exists("preservation.log"));
        assertEquals(20000000 - (70 + 625), db.getAvailableSpaceIn(cv.getName()));
    }
}
