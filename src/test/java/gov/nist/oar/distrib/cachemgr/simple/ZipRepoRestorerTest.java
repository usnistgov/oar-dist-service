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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

public class ZipRepoRestorerTest {

    ZipRepoRestorer rstr = null;
    static LongTermStorage lts = null;
    static Path testdir = null;
    static File repodir = null;
    static File  outdir = null;
    
    @BeforeAll
    public static void setUpClass() throws IOException {
        // Create a test directory where we can write stuff
        Path indir = Paths.get(System.getProperty("user.dir"));
        if (indir.toFile().canWrite())
            testdir = Files.createTempDirectory(indir, "_unittest");
        else
            testdir = Files.createTempDirectory("_unittest");
        
        repodir = new File(testdir.toFile(), "repo");
        repodir.mkdir();
        outdir = new File(testdir.toFile(), "out");

        String[] zips = { "mds1491.mbag0_2-0.zip", "mds1491.1_1_0.mbag0_4-1.zip" };
        for (String file : zips) {
            Files.copy(ZipRepoRestorerTest.class.getResourceAsStream("/" + file),
                       (new File(repodir, file)).toPath());
        }

        lts = new FilesystemLongTermStorage(repodir.toString());
    }

    @BeforeEach
    public void setUp() throws IOException {
        outdir.mkdir();
        rstr = new ZipRepoRestorer(repodir.toString());
    }

    @AfterEach
    public void tearDown() {
        FileSystemUtils.deleteRecursively(outdir);
        rstr = null;
    }

    @AfterAll
    public static void tearDownClass() {
        FileSystemUtils.deleteRecursively(testdir.toFile());
    }

    @Test
    public void testGetRepoDir() {
        assertEquals(repodir.toString(), rstr.getRepoDir().toString());
    }

    @Test
    public void testDoesNotExist() throws StorageVolumeException {
        assertTrue(rstr.doesNotExist("gurn/goober.txt"));
        assertFalse(rstr.doesNotExist("mds1491.mbag0_2-0/bagit.txt"));
        assertFalse(rstr.doesNotExist("mds1491.mbag0_2-0/data/trial1.json"));
    }

    @Test
    public void testGetSizeOf() throws StorageVolumeException {
        assertEquals(69, rstr.getSizeOf("mds1491.mbag0_2-0/data/trial1.json"));
        assertEquals(1562, rstr.getSizeOf("mds1491.mbag0_2-0/preserv.log"));
        assertThrows(ObjectNotFoundException.class, () -> rstr.getSizeOf("goober!"));
    }

    @Test
    public void testGetChecksum() throws StorageVolumeException {
        assertEquals("ab671096", rstr.getChecksum("mds1491.mbag0_2-0/data/trial1.json").hash);
        assertThrows(ObjectNotFoundException.class, () -> rstr.getChecksum("goober!"));
    }

    @Test
    public void testRestoreObject()
            throws RestorationException, StorageVolumeException, JSONException, InventoryException, IOException {
        File cvdir = outdir;
        CacheVolume cv = new FilesystemCacheVolume(cvdir.toString());
        assertFalse(cv.exists("preservation.log"));

        String dbf = (new File(testdir.toFile(), "sidb.sqlite3")).toString();
        SQLiteStorageInventoryDB.initializeDB(dbf);
        StorageInventoryDB db = new SQLiteStorageInventoryDB(dbf);
        db.registerVolume(cv.getName(), 20000000, null);  // 20 MB capacity
        db.registerAlgorithm(Checksum.CRC32);
        db.registerAlgorithm(Checksum.SHA256);

        Reservation resv = Reservation.reservationFor(cv, db, 1562);
        JSONObject md = new JSONObject();
        md.put("priority", 5);

        rstr.restoreObject("mds1491.mbag0_2-0/preserv.log", resv, "preservation.log", md);

        assertTrue((new File(cvdir, "preservation.log")).isFile());
        assertTrue(cv.exists("preservation.log"));
        assertEquals(1562, db.findObject(cv.getName(), "preservation.log").getSize());
        assertEquals(5, db.findObject(cv.getName(), "preservation.log").getMetadatumInt("priority", 11));
        assertEquals("77237883", db.findObject(cv.getName(), "preservation.log").getMetadatumString("checksum", ""));
        assertEquals(0L, resv.getSize());
        resv.drop();

        resv = Reservation.reservationFor(cv, db, 1562);
        rstr.restoreObject("mds1491.mbag0_2-0/bag-info.txt", resv, "preservation.log", null);
        assertTrue((new File(cvdir, "preservation.log")).isFile());
        assertTrue(cv.exists("preservation.log"));
        assertEquals(625, db.findObject(cv.getName(), "preservation.log").getSize());
        assertEquals(10, db.findObject(cv.getName(), "preservation.log").getMetadatumInt("priority", 11));
        assertEquals("cb1bc46f", db.findObject(cv.getName(), "preservation.log").getMetadatumString("checksum", ""));
        assertEquals(937L, resv.getSize());
        resv.drop();

        resv = Reservation.reservationFor(cv, db, 1562);
        rstr.restoreObject("mds1491.mbag0_2-0/data/trial3/trial3a.json", resv, "data/trial3::trial3a.json", null);
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
