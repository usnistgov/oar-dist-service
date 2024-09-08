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
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

public class FileCopyRestorerTest {

    Path testdir = null;
    File storeroot = null;
    LongTermStorage lts = null;
    
    public void unzip(ZipInputStream zis, File destDir) throws IOException {
        destDir = destDir.getAbsoluteFile();
        if (! destDir.isDirectory())
            throw new IllegalArgumentException(destDir.toString()+": does not exist as a directory");

        Path destp = null;
        File destf = null;
        try {
            ZipEntry ze = null;
            while ((ze = zis.getNextEntry()) != null) {
                String ep = ze.getName();
                if (ep.startsWith("/")) ep = ep.substring(1);
                destf = new File(destDir, ep);
                destp = destf.toPath();
                if (ze.isDirectory()) {
                    Files.createDirectory(destp);
                }
                else {
                    if (! destf.getParentFile().exists())
                        Files.createDirectory(destf.getParentFile().toPath());
                    Files.copy(zis, destp);
                }
                Files.setLastModifiedTime(destp, ze.getLastModifiedTime());
            }
        }
        catch (FileSystemException ex) {
            throw new IllegalStateException("Unexpected state in destination directory, "+
                                            destDir.toString() + ": " + ex.getMessage(), ex);
        }
    }

    @BeforeAll
    public void setUp() throws IOException {
        // create a test directory where we can write stuff
        Path indir = Paths.get(System.getProperty("user.dir"));
        if (indir.toFile().canWrite())
            testdir = Files.createTempDirectory(indir, "_unittest");
        else
            testdir = Files.createTempDirectory("_unittest");
        storeroot = new File(testdir.toFile(), "mds1491.mbag0_2-0");
        
        // setup a little repo
        ZipInputStream zis = new ZipInputStream(getClass().getResourceAsStream("/mds1491.mbag0_2-0.zip"));
        unzip(zis, testdir.toFile());

        lts = new FilesystemLongTermStorage(storeroot.getAbsolutePath());
    }

    @AfterAll
    public void tearDown(){
        FileSystemUtils.deleteRecursively(testdir.toFile());
        testdir = null;
    }

    @Test
    public void testDoesNotExist() throws StorageVolumeException {
        FileCopyRestorer rest = new FileCopyRestorer(lts);
        assertTrue(rest.doesNotExist("gurn/goober.txt"));
        assertFalse(rest.doesNotExist("bagit.txt"));
        assertFalse(rest.doesNotExist("data"));
        assertFalse(rest.doesNotExist("data/trial1.json"));
    }

    @Test
    public void testGetSizeOf() throws StorageVolumeException {
        FileCopyRestorer rest = new FileCopyRestorer(lts);
        assertEquals(69, rest.getSizeOf("data/trial1.json"));
        assertEquals(1562, rest.getSizeOf("preserv.log"));
        try {
            rest.getSizeOf("goober!");
            fail("Failed to raise exception for missing file");
        } catch (ObjectNotFoundException ex) { }
    }

    @Test
    public void testGetChecksum() throws StorageVolumeException {
        FileCopyRestorer rest = new FileCopyRestorer(lts);
        assertEquals("d155d99281ace123351a311084cd8e34edda6a9afcddd76eb039bad479595ec9",
                     rest.getChecksum("data/trial1.json").hash);
        try {
            rest.getChecksum("goober!");
            fail("Failed to raise exception for missing file");
        } catch (ObjectNotFoundException ex) { }
    }

    @Test
    public void testRestoreObject()
        throws RestorationException, StorageVolumeException, JSONException, InventoryException, IOException
    {
        File cvdir = new File(testdir.toFile(), "cache");
        cvdir.mkdirs();
        CacheVolume cv = new FilesystemCacheVolume(cvdir.toString());
        assertFalse(cv.exists("preservation.log"));

        String dbf = (new File(testdir.toFile(), "sidb.sqlite3")).toString();
        SQLiteStorageInventoryDB.initializeDB(dbf);
        StorageInventoryDB db = new SQLiteStorageInventoryDB(dbf);
        db.registerVolume(cv.getName(), 20000000, null);  // 20 MB capacity
        db.registerAlgorithm(Checksum.SHA256);

        Reservation resv = Reservation.reservationFor(cv, db, 1562);
        JSONObject md = new JSONObject();
        md.put("priority", 5);

        FileCopyRestorer rest = new FileCopyRestorer(lts);
        rest.restoreObject("preserv.log", resv, "preservation.log", md);

        assertTrue((new File(cvdir, "preservation.log")).isFile());
        assertTrue(cv.exists("preservation.log"));
        assertEquals(1562, db.findObject(cv.getName(), "preservation.log").getSize());
        assertEquals(5, db.findObject(cv.getName(), "preservation.log").getMetadatumInt("priority",11));
        assertEquals("3370af43681254b7f44cdcdad8b7dcd40a8c90317630c288e71b2caf84cf685f",
                     db.findObject(cv.getName(), "preservation.log").getMetadatumString("checksum",""));
        assertEquals(0L, resv.getSize());
        resv.drop();
        
        resv = Reservation.reservationFor(cv, db, 1562);
        rest.restoreObject("bag-info.txt", resv, "preservation.log", null);
        assertTrue((new File(cvdir, "preservation.log")).isFile());
        assertTrue(cv.exists("preservation.log"));
        assertEquals(625, db.findObject(cv.getName(), "preservation.log").getSize());
        assertEquals(10, db.findObject(cv.getName(), "preservation.log").getMetadatumInt("priority",11));
        assertEquals("304cafc75856b019d729c2c30ed71898048c445e0e67ffbdbcf2b69cf89b2d8d",
                     db.findObject(cv.getName(), "preservation.log").getMetadatumString("checksum",""));
        assertEquals(937L, resv.getSize());
        resv.drop();
        
        resv = Reservation.reservationFor(cv, db, 1562);
        rest.restoreObject("data/trial3/trial3a.json", resv, "data/trial3::trial3a.json", null);
        assertTrue((new File(cvdir, "data/trial3::trial3a.json")).isFile());
        assertTrue(cv.exists("data/trial3::trial3a.json"));
        assertEquals(70, db.findObject(cv.getName(), "data/trial3::trial3a.json").getSize());
        assertEquals(10, db.findObject(cv.getName(), "data/trial3::trial3a.json").getMetadatumInt("priority",11));
        assertEquals("7b58010c841b7748a48a7ac6366258d5b5a8d23d756951b6059c0e80daad516b",
                     db.findObject(cv.getName(), "data/trial3::trial3a.json").getMetadatumString("checksum",""));
        assertEquals(1492L, resv.getSize());
        resv.drop();
        
        assertTrue(cv.exists("preservation.log"));
        assertEquals(20000000-(70+625), db.getAvailableSpaceIn(cv.getName()));
    }
}
