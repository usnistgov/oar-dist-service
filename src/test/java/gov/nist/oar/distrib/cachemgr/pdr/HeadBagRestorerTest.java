package gov.nist.oar.distrib.cachemgr.pdr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileNotFoundException;
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

import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.Reservation;
import gov.nist.oar.distrib.cachemgr.RestorationException;
import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

public class HeadBagRestorerTest {

    final static String ltsdir = System.getProperty("project.test.resourceDirectory");

    static BagStorage publicLtstore = null;
    static BagStorage restrictedLtstore = null;
    HeadBagRestorer restorer = null;
    Path testdir = null;

    @BeforeAll
    public static void setUpClass() throws FileNotFoundException {
        publicLtstore = new FilesystemLongTermStorage(ltsdir);
        restrictedLtstore = new FilesystemLongTermStorage(ltsdir + "/restricted");
    }

    @AfterAll
    public void tearDown() {
        if (testdir != null)
            FileSystemUtils.deleteRecursively(testdir.toFile());
        testdir = null;
    }

    @Test
    public void testDoesNotExist() throws StorageVolumeException {
        HeadBagRestorer rest = new HeadBagRestorer(publicLtstore);

        assertFalse(rest.doesNotExist("67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip"));
        assertFalse(rest.doesNotExist("mds1491.1_1_0.mbag0_4-1.zip"));
        assertFalse(! rest.doesNotExist("mds1491.1_2_0.mbag0_4-2.zip"));

        rest = new HeadBagRestorer(restrictedLtstore, publicLtstore);

        assertFalse(rest.doesNotExist("67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip"));
        assertFalse(rest.doesNotExist("mds1491.1_1_0.mbag0_4-1.zip"));
        assertFalse(rest.doesNotExist("mds1491.1_2_0.mbag0_4-2.zip"));

        rest = new HeadBagRestorer(restrictedLtstore);

        assertFalse(! rest.doesNotExist("67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip"));
        assertFalse(! rest.doesNotExist("mds1491.1_1_0.mbag0_4-1.zip"));
        assertFalse(rest.doesNotExist("mds1491.1_2_0.mbag0_4-2.zip"));
    }

    @Test
    public void testGetSizeOf() throws StorageVolumeException {
        HeadBagRestorer rest = new HeadBagRestorer(restrictedLtstore, publicLtstore);

        assertEquals(13746, rest.getSizeOf("mds1491.1_2_0.mbag0_4-2.zip"));
        assertEquals(14077, rest.getSizeOf("mds1491.1_1_0.mbag0_4-1.zip"));
        assertEquals(9841, rest.getSizeOf("mds1491.mbag0_2-0.zip"));

        rest = new HeadBagRestorer(restrictedLtstore);

        assertEquals(13746, rest.getSizeOf("mds1491.1_2_0.mbag0_4-2.zip"));
        try {
            rest.getSizeOf("mds1491.1_1_0.mbag0_4-1.zip");
            fail("Mistakenly found hidden bag: mds1491.1_1_0.mbag0_4-1.zip");
        } catch (ObjectNotFoundException ex) { /* success! */ }
    }


    @Test
    public void testGetChecksum() throws StorageVolumeException {
        HeadBagRestorer rest = new HeadBagRestorer(restrictedLtstore, publicLtstore);

        assertEquals("0a9b1223136c91d57d0e1db70634c616e9c40e2ffd282df389cef3f80a0c2107",
                     rest.getChecksum("mds1491.1_2_0.mbag0_4-2.zip").hash);
        assertEquals("1fc4bf7eb432429c5fe1f2736d6075cb44bd2155533db3df6fe1c866333fc768",
                     rest.getChecksum("mds1491.1_1_0.mbag0_4-1.zip").hash);
        assertEquals("3de9d9e32831be693e341306db79e636ebd61b6a78f9482e8e3038a6e8eba569",
                     rest.getChecksum("mds1491.mbag0_2-0.zip").hash);
    
        rest = new HeadBagRestorer(publicLtstore);
        assertEquals("1fc4bf7eb432429c5fe1f2736d6075cb44bd2155533db3df6fe1c866333fc768",
                     rest.getChecksum("mds1491.1_1_0.mbag0_4-1.zip").hash);
        try {
            rest.getChecksum("mds1491.1_2_0.mbag0_4-2.zip");
            fail("Mistakenly found hidden bag: mds1491.1_2_0.mbag0_4-2.zip");
        } catch (ObjectNotFoundException ex) { /* success! */ }
    }

    @Test
    public void testNameForObject() {
        HeadBagRestorer rest = new HeadBagRestorer(restrictedLtstore, publicLtstore);
        assertEquals("goob", rest.nameForObject("goob"));
        rest = new HeadBagRestorer(publicLtstore);
        assertEquals("goob", rest.nameForObject("goob"));
    }

    public void setUpTestdir() throws IOException {
        // create a test directory where we can write stuff
        Path indir = Paths.get(System.getProperty("user.dir"));
        if (indir.toFile().canWrite())
            testdir = Files.createTempDirectory(indir, "_unittest");
        else
            testdir = Files.createTempDirectory("_unittest");
    }

    @Test
    public void testRestoreObject()
        throws RestorationException, StorageVolumeException, JSONException, InventoryException, IOException
    {
        setUpTestdir();
        File cvdir = new File(testdir.toFile(), "cache");
        cvdir.mkdirs();
        CacheVolume cv = new FilesystemCacheVolume(cvdir.toString());
        assertFalse(cv.exists("mds1491.1_2_0.mbag0_4-2.zip"));

        String dbf = (new File(testdir.toFile(), "sidb.sqlite3")).toString();
        SQLiteStorageInventoryDB.initializeDB(dbf);
        StorageInventoryDB db = new SQLiteStorageInventoryDB(dbf);
        db.registerVolume(cv.getName(), 20000000, null);  // 20 MB capacity
        db.registerAlgorithm(Checksum.SHA256);

        Reservation resv = Reservation.reservationFor(cv, db, 13746);
        JSONObject md = new JSONObject();
        md.put("priority", 5);

        String bag = "mds1491.1_2_0.mbag0_4-2.zip";
        HeadBagRestorer rest = new HeadBagRestorer(restrictedLtstore, publicLtstore);
        rest.restoreObject(bag, resv, "mds1491.1_2_0.zip", md);

        assertTrue(new File(cvdir, "mds1491.1_2_0.zip").isFile());
        assertTrue(cv.exists("mds1491.1_2_0.zip"));
        assertEquals(13746, db.findObject(cv.getName(), "mds1491.1_2_0.zip").getSize());
        assertEquals(5, db.findObject(cv.getName(), "mds1491.1_2_0.zip").getMetadatumInt("priority",11));
        assertEquals(0L, resv.getSize());
        resv.drop();
        
        bag = "mds1491.1_1_0.mbag0_4-1.zip";
        resv = Reservation.reservationFor(cv, db, 14077);
        rest.restoreObject(bag, resv, rest.nameForObject(bag), md);

        assertTrue(new File(cvdir, bag).isFile());
        assertTrue(cv.exists(bag));
        assertEquals(14077, db.findObject(cv.getName(), bag).getSize());
        assertEquals(5, db.findObject(cv.getName(), bag).getMetadatumInt("priority",11));
        assertEquals(0L, resv.getSize());
        resv.drop();
        
        rest = new HeadBagRestorer(restrictedLtstore);
        try {
            rest.restoreObject(bag, resv, rest.nameForObject(bag), md);
            fail("Mistakenly found hidden bag: "+bag);
        } catch (ObjectNotFoundException ex) { /* success! */ }
    }

    @Test
    public void testFindHeadBagFor() throws ResourceNotFoundException, StorageVolumeException {
        HeadBagRestorer rest = new HeadBagRestorer(restrictedLtstore, publicLtstore);

        assertEquals("mds1491.1_2_0.mbag0_4-2.zip", rest.findHeadBagFor("mds1491"));
        assertEquals("mds1491.1_1_0.mbag0_4-1.zip", rest.findHeadBagFor("mds1491", "1.1.0"));
        assertEquals("mds1491.mbag0_2-0.zip", rest.findHeadBagFor("mds1491", "1"));

        rest = new HeadBagRestorer(publicLtstore);
        assertEquals("mds1491.1_1_0.mbag0_4-1.zip", rest.findHeadBagFor("mds1491"));

        try {
            rest.findHeadBagFor("mds1491", "1.2.0");
            fail("Unexpectedly found hiddent 1.2.0 version");
        } catch (ResourceNotFoundException ex) { /* success! */ }
    }
}
