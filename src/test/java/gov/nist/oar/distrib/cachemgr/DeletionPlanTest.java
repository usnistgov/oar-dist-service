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
package gov.nist.oar.distrib.cachemgr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.storage.NullCacheVolume;

public class DeletionPlanTest {

    @TempDir
    Path tempDir;

    File dbf = null;
    StorageInventoryDB sidb = null;
    NullCacheVolume cv = null;

    String createDB() throws IOException, InventoryException {
        File tf = new File(tempDir.toFile(), "testdb.sqlite");
        String out = tf.getAbsolutePath();
        SQLiteStorageInventoryDB.initializeDB(out);
        return out;
    }

    @AfterEach
    public void tearDown() {
        dbf.delete();
    }

    @BeforeEach
    public void setUp() throws IOException, InventoryException {
        cv = new NullCacheVolume("foobar");
        dbf = new File(createDB());
        assertTrue(dbf.exists());

        sidb = new SQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 200000, null);

        String nm = null;
        JSONObject md = null;
        int[] sizes = { 229, 9321, 44001, 100311, 953, 2230, 100 };
        for (int i=0; i < sizes.length; i++) {
            nm = Integer.toString(i);
            md = new JSONObject();
            md.put("size", sizes[i]);
            sidb.addObject("file" + nm, "foobar", nm, md);
            cv.addObjectName(nm);
        }
    }

    @Test
    public void testCtor() throws InventoryException {
        List<CacheObject> cos = sidb.selectObjectsFrom("foobar", "", 500);
        DeletionPlan dp = new DeletionPlan(cv, sidb, cos, 1000, 500);
        assertEquals(1000, dp.getByteCountToBeRemoved());
        assertEquals(500, dp.getByteCountNeeded());
        assertEquals("foobar", dp.getVolumeName());
        assertEquals(cv, dp.volume);
        assertEquals(0.0, dp.score, 0.0000000001);
    }

    @Test
    public void testExecute() throws InventoryException, DeletionFailureException {
        long used = sidb.getUsedSpace().get("foobar");
        List<CacheObject> cos = sidb.selectObjectsFrom("foobar", "", 500);
        assertEquals(7, cos.size());
        cos.remove(4);
        cos.remove(3);
        cos.remove(1);
        cos.remove(0);

        DeletionPlan dp = new DeletionPlan(cv, sidb, cos, 46200, 500);
        long removed = dp.execute();
        assertEquals(44001+2230, removed);

        assertFalse(cv.exists("2"));
        assertFalse(cv.exists("5"));
        assertTrue(cv.exists("6"));
        assertEquals(used-removed, (long) sidb.getUsedSpace().get("foobar"));

        cos = sidb.selectObjectsFrom("foobar", "", 500);
        assertEquals(5, cos.size());

        // test guard against deleting from a protected volume
        sidb.setVolumeStatus("foobar", VolumeStatus.VOL_FOR_GET);
        dp = new DeletionPlan(cv, sidb, cos, 46200, 500);
        try {
            dp.execute();
            fail("Failed to prevent removal from volume with VOL_FOR_GET set");
        } catch (IllegalStateException ex) {
            sidb.setVolumeStatus("foobar", VolumeStatus.VOL_FOR_UPDATE);
            assertEquals(5, sidb.selectObjectsFrom("foobar", "", 500).size());
        }

        sidb.setVolumeStatus("foobar", VolumeStatus.VOL_DISABLED);
        dp = new DeletionPlan(cv, sidb, cos, 46200, 500);
        try {
            dp.execute();
            fail("Failed to prevent removal from volume with VOL_DISABLED set");
        } catch (IllegalStateException ex) {
            sidb.setVolumeStatus("foobar", VolumeStatus.VOL_FOR_UPDATE);
            assertEquals(5, sidb.selectObjectsFrom("foobar", "", 500).size());
        }
    }

    @Test
    public void testExecuteAndReserve() throws InventoryException, DeletionFailureException {
        List<CacheObject> cos = sidb.selectObjectsFrom("foobar", "", 500);
        assertEquals(7, cos.size());
        cos.remove(4);
        cos.remove(3);
        cos.remove(1);
        cos.remove(0);

        DeletionPlan dp = new DeletionPlan(cv, sidb, cos, 46200, 46000);
        Reservation res = dp.executeAndReserve();
        assertEquals(46000, res.getSize());

        assertFalse(cv.exists("2"));
        assertFalse(cv.exists("5"));
        assertTrue(cv.exists("6"));

        cos = sidb.selectObjectsFrom("foobar", "", 500);
        assertEquals(6, cos.size());
    }

    @Test
    public void testExecuteAndReserveTooLittle() throws InventoryException, DeletionFailureException {
        List<CacheObject> cos = sidb.selectObjectsFrom("foobar", "", 500);
        assertEquals(7, cos.size());
        cos.remove(4);
        cos.remove(3);
        cos.remove(1);
        cos.remove(0);

        Reservation res = null;
        DeletionPlan dp = new DeletionPlan(cv, sidb, cos, 60000, 46000);
        try {
            res = dp.executeAndReserve();
            fail("Failed to detect plan execution was not successful as advertised");
        } catch (DeletionFailureException ex) {
            assertEquals(null, res);
        }

        assertFalse(cv.exists("2"));
        assertFalse(cv.exists("5"));
        assertFalse(cv.exists("6"));

        cos = sidb.selectObjectsFrom("foobar", "", 500);
        assertEquals(4, cos.size());
    }

    @Test
    public void testExecuteAndReserveNotEnough() throws InventoryException, DeletionFailureException {
        List<CacheObject> cos = sidb.selectObjectsFrom("foobar", "", 500);
        assertEquals(7, cos.size());
        cos.remove(4);
        cos.remove(3);
        cos.remove(1);
        cos.remove(0);

        Reservation res = null;
        DeletionPlan dp = new DeletionPlan(cv, sidb, cos, 46000, 90000);
        try {
            res = dp.executeAndReserve();
            fail("Failed to detect deletion failure; available space: "+sidb.getAvailableSpace());
        } catch (DeletionFailureException ex) {
            assertEquals(null, res);
        }

        assertFalse(cv.exists("2"));
        assertFalse(cv.exists("5"));
        assertTrue(cv.exists("6"));

        cos = sidb.selectObjectsFrom("foobar", "", 500);
        assertEquals(5, cos.size());
    }
}
