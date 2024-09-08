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
package gov.nist.oar.distrib.cachemgr.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.DeletionPlan;
import gov.nist.oar.distrib.cachemgr.DeletionPlanner;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.storage.NullCacheVolume;

import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class DefaultDeletionPlannerTest {

    @TempDir
    public File tempDir;

    File dbf = null;
    StorageInventoryDB sidb = null;
    NullCacheVolume cv = null;
    List<CacheVolume> cvlist = null;

    String createDB() throws IOException, InventoryException {
        File tf = new File(tempDir, "testdb.sqlite");
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
        cvlist = new ArrayList<>(2);
        cvlist.add(cv);

        dbf = new File(createDB());
        assertTrue(dbf.exists());

        sidb = new SQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 200000, null);

        int[] sizes = {229, 9321, 44001, 100311, 953, 2230, 100};
        addDataToVolume(sizes, "foobar");
    }

    public long addDataToVolume(int[] sizes, String volname) throws InventoryException {
        String nm;
        JSONObject md;
        long total = 0L;
        for (int size : sizes) {
            nm = Integer.toString(size);
            md = new JSONObject();
            md.put("size", size);
            sidb.addObject("file" + nm, volname, nm, md);
            total += size;
            cv.addObjectName(nm);
        }
        return total;
    }

    @Test
    public void testCtor() throws InventoryException {
        SizeLimitedSelectionStrategy strat = new BySizeSelectionStrategy(1000000);
        new DefaultDeletionPlanner(sidb, cvlist, strat);
    }

    @Test
    public void testCreateDeletionPlanFor() throws CacheManagementException {
        cv = new NullCacheVolume("foobar");
        SizeLimitedSelectionStrategy strat = new BySizeSelectionStrategy(1000000);
        DeletionPlanner planr = new DefaultDeletionPlanner(sidb, cvlist, strat);

        DeletionPlan dp = planr.createDeletionPlanFor(cv, 1000);
        assertNotNull(dp);
        assertEquals(0L, dp.getByteCountToBeRemoved());
        assertEquals(0.0, dp.score, 0.0);

        dp = planr.createDeletionPlanFor(cv, 50000);
        assertNotNull(dp);
        Map<String, Long> used = sidb.getUsedSpace();
        assertEquals(157145, used.get("foobar").longValue());
        long remove = dp.getByteCountToBeRemoved();
        assertTrue(remove > 7145);
        assertTrue(dp.score > 1000.0);
        assertEquals(100311L, remove);

        List<CacheObject> deletables = dp.getDeletableObjects();
        assertEquals(1, deletables.size());
        assertEquals(100311, deletables.get(0).getSize());
    }

    @Test
    public void testOrderDeletionPlans() throws CacheManagementException {
        // this volume is overfull
        cvlist.add(new NullCacheVolume("cranky"));
        sidb.registerVolume("cranky", 8000, null);
        int[] sizesc = {229, 6321, 953, 2230, 100};
        addDataToVolume(sizesc, "cranky");
        assertEquals(-1833, sidb.getAvailableSpaceIn("cranky"));

        // this volume is nearly full
        cvlist.add(new NullCacheVolume("finbar"));
        sidb.registerVolume("finbar", 10000, null);
        int[] sizesb = {229, 321, 953, 2230, 100, 229, 321, 953, 2230, 100};
        addDataToVolume(sizesb, "finbar");
        assertEquals(2334, sidb.getAvailableSpaceIn("finbar"));

        // this volume is empty
        cvlist.add(new NullCacheVolume("empty"));
        sidb.registerVolume("empty", 200000, null);

        SizeLimitedSelectionStrategy strat = new BySizeSelectionStrategy(1000000, 10000.0);
        DeletionPlanner planr = new DefaultDeletionPlanner(sidb, cvlist, strat);

        List<DeletionPlan> plans = planr.orderDeletionPlans(7000L, cvlist);

        DeletionPlan dp = plans.get(0);
        assertEquals("foobar", dp.getVolumeName());
        assertEquals(0L, dp.getByteCountToBeRemoved());
        assertEquals(0.0, dp.score, 0.0);
        List<CacheObject> deletables = dp.getDeletableObjects();
        assertEquals(0, deletables.size());

        dp = plans.get(1);
        assertEquals("empty", dp.getVolumeName());
        assertEquals(0L, dp.getByteCountToBeRemoved());
        assertEquals(0.0, dp.score, 0.0);
        deletables = dp.getDeletableObjects();
        assertEquals(0, deletables.size());

        dp = plans.get(3);
        assertEquals("finbar", dp.getVolumeName());

        long remove = dp.getByteCountToBeRemoved();
        assertTrue(remove < 7000);
        assertTrue(remove > 2274);
        assertTrue(dp.score < 1000.0);

        deletables = dp.getDeletableObjects();
        assertEquals(4, deletables.size());
        assertEquals(2230, deletables.get(0).getSize());
        assertEquals(2230, deletables.get(1).getSize());
        assertEquals(953, deletables.get(2).getSize());
        assertEquals(953, deletables.get(3).getSize());

        dp = plans.get(2);
        assertEquals("cranky", dp.getVolumeName());
        remove = dp.getByteCountToBeRemoved();

        assertTrue(remove < 50000);
        assertTrue(remove > 7145);
        assertTrue(dp.score < 1000.0);
    }
}
