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
package gov.nist.oar.distrib.cachemgr.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.oar.distrib.cachemgr.Cache;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.DeletionPlanner;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.Reservation;
import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.VolumeStatus;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.storage.NullCacheVolume;

/*
 * tests BasicCache, too
 */
public class SimpleCacheTest
    implements SimpleCache.ReservationListener, SimpleCache.SaveListener, SimpleCache.DeletionListener, VolumeStatus
{
    @TempDir
    File tempDir;  // JUnit 5 provides @TempDir to manage temporary folders

    File dbf = null;
    StorageInventoryDB sidb = null;
    List<CacheVolume> cvlist = null;
    SimpleCache cache = null;
    String heard = "";

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
        cache = null;
        heard = "";
        dbf = new File(createDB());
        assertTrue(dbf.exists());

        sidb = new SQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");

        cvlist = new ArrayList<>(2);
        NullCacheVolume cv = new NullCacheVolume("foobar");
        cvlist.add(cv);
        sidb.registerVolume("foobar", 22000, null);
        cv = new NullCacheVolume("cranky");
        cvlist.add(cv);
        sidb.registerVolume("cranky", 20000, null);

        int[] sizes = {229, 9321, 9001, 980, 2230, 100};
        addDataToVolume(sizes, (NullCacheVolume) cvlist.get(0));
        int[] sizesc = {229, 6321, 953, 10031, 2230, 100};
        addDataToVolume(sizesc, (NullCacheVolume) cvlist.get(1));
    }

    public long addDataToVolume(int[] sizes, NullCacheVolume cv) throws InventoryException {
        long now = System.currentTimeMillis();
        String volname = cv.getName();

        String nm;
        JSONObject md;
        long total = 0L;
        for (int i = 0; i < sizes.length; i++) {
            nm = Integer.toString(i);
            md = new JSONObject();
            md.put("size", sizes[i]);
            sidb.addObject(volname + nm, volname, nm, md);
            total += sizes[i];
            md.put("since", now - (sizes[i] * 60000));
            sidb.updateMetadata(volname, nm, md);
            cv.addObjectName(nm);
        }

        return total;
    }

    @Test
    public void testCtor() throws CacheManagementException {
        cache = new SimpleCache("Simple", sidb, cvlist);
        DeletionPlanner ds = cache.getDeletionPlanner();
        assertNotNull(ds, "No planner!");
        assertTrue(cache.isCached("foobar2"), "Can't find foobar2");
        assertTrue(cache.isCached("cranky4"), "Can't find cranky4");
        assertFalse(cache.isCached("goober"), "Found fictional object, goober");
    }

    @Test
    public void testUncache() throws CacheManagementException {
        cache = new SimpleCache("Simple", sidb, cvlist);
        DeletionPlanner ds = cache.getDeletionPlanner();
        assertNotNull(ds, "No planner!");
        assertTrue(cache.isCached("foobar2"), "Can't find foobar2");
        assertTrue(cache.isCached("cranky4"), "Can't find cranky4");

        cache.uncache("foobar2");
        assertFalse(cache.isCached("foobar2"), "Failed to delete foobar2");
        assertTrue(cache.isCached("cranky4"), "Can't find cranky4");

        cache.uncache("cranky4");
        assertFalse(cache.isCached("foobar2"), "foobar2 reappeared!");
        assertFalse(cache.isCached("cranky4"), "Failed to delete cranky4");
    }

    @Test
    public void testFindObject() throws CacheManagementException {
        long now = System.currentTimeMillis();

        cache = new SimpleCache("Simple", sidb, cvlist);
        DeletionPlanner ds = cache.getDeletionPlanner();
        assertNotNull(ds, "No planner!");
        assertTrue(cache.isCached("foobar2"), "Can't find foobar2");
        assertTrue(cache.isCached("cranky4"), "Can't find cranky4");

        CacheObject co = cache.findObject("foobar2");
        assertEquals("foobar2", co.id);
        assertEquals("2", co.name);
        assertEquals(9001, co.getSize());
        assertTrue(now - co.getMetadatumLong("since", 0L) - 9001 * 60000 < 5000,
                   "Funny timestamp on foobar2");

        co = cache.findObject("cranky1");
        assertEquals("cranky1", co.id);
        assertEquals("1", co.name);
        assertEquals(6321, co.getSize());
        assertTrue(now - co.getMetadatumLong("since", 0L) - 6321 * 60000 < 5000,
                   "Funny timestamp on cranky1");

        long accesstime = co.getMetadatumLong("since", -1L);
        assertTrue(accesstime > 0);
        cache.confirmAccessOf(co);
        co = cache.findObject("cranky1");
        assertTrue(accesstime < co.getMetadatumLong("since", -1L));
    }

    @Test
    public void testReserveSpaceWithPrefs() throws CacheManagementException {
        cache = new SimpleCache("Simple", sidb, cvlist);
        assertNotNull(cache.getDeletionPlanner(), "No planner!");

        Reservation res = cache.reserveSpace(4000L, 0);
        assertEquals(4000, res.getSize());
        assertEquals("cranky", res.getVolumeName());
    }

    @Test
    public void testReserveSpace() throws CacheManagementException {
        cache = new SimpleCache("Simple", sidb, cvlist);
        assertNotNull(cache.getDeletionPlanner(), "No planner!");

        Reservation res = cache.reserveSpace(4000L);
        assertEquals(4000, res.getSize());
        assertEquals("cranky", res.getVolumeName());
    }

    // Implement listener methods

    @Override
    public boolean objectsDeleted(Cache c, CacheVolume vol, List<String> deleted, long freed) {
        Logger log = LoggerFactory.getLogger(SimpleCacheTest.class);
        assertTrue(c == cache, "Unexpected cache given to listener");
        assertNotNull(vol);
        log.info("Objects deleted from " + c.getName() + ":" + vol.getName());
        assertTrue(freed > 0);
        heard += String.format("freed on cranky: %d; ", freed);
        return true;
    }

    @Override
    public boolean objectSaved(Cache c, CacheObject obj) {
        Logger log = LoggerFactory.getLogger(SimpleCacheTest.class);
        assertTrue(c == cache, "Unexpected cache given to listener");
        assertNotNull(obj);
        log.info("Saved object to " + c.getName() + ":" + obj.volume.getName());
        heard += String.format("saved to %s: %d; ", obj.volume.getName(), obj.getSize());
        return true;
    }

    @Override
    public boolean reservationMade(Cache c, CacheVolume vol, long size) {
        Logger log = LoggerFactory.getLogger(SimpleCacheTest.class);
        assertTrue(c == cache, "Unexpected cache given to listener");
        assertNotNull(vol);
        log.info("Reservation made in " + c.getName() + ":" + vol.getName());
        assertTrue(size > 0);
        heard += String.format("reservation on %s: %d; ", vol.getName(), size);
        return false;
    }

    @Test
    public void testListeners() throws CacheManagementException {
        cache = new SimpleCache("Simple", sidb, cvlist);
        cache.addReservationListener(this);
        cache.addDeletionListener(this);
        cache.addSaveListener(this);
        assertNotNull(cache.getDeletionPlanner(), "No planner!");

        Reservation res = cache.reserveSpace(4000L);
        assertEquals(4000, res.getSize());
        assertEquals("cranky", res.getVolumeName());
        assertTrue(heard.contains("reservation on cranky: 4000"), "Missing listener message");

        heard = "";
        res = cache.reserveSpace(300L);
        assertTrue(heard.contains("reservation on cranky: 300"), "Missing listener message");

        JSONObject md = new JSONObject();
        md.put("size", 13L);
        ByteArrayInputStream objstrm = new ByteArrayInputStream("file contents".getBytes());
        try {
            res.saveAs(objstrm, "Gurn", "gurn.txt", md);
            assertTrue(heard.contains("saved to cranky: 13;"), "Missing listener message in "+heard);
        }
        finally {
            try { objstrm.close(); } catch (IOException ex) { }
        }
    }

    @Test
    public void testVolumeNames() {
        cache = new SimpleCache("Simple", sidb, cvlist);
        Set<String> volnames = cache.volumeNames();
        assertTrue(volnames.contains("cranky"), "Missing volume");
        assertTrue(volnames.contains("foobar"), "Missing volume");
    }

    @Test
    public void testAddCacheVolume() throws CacheManagementException {
        cache = new SimpleCache("Simple", sidb, cvlist);
        Set<String> volnames = cache.volumeNames();
        assertTrue(volnames.contains("cranky"), "Missing volume");
        assertTrue(volnames.contains("foobar"), "Missing volume");
        assertFalse(volnames.contains("noobie"), "Missing volume");

        CacheVolume vol = new NullCacheVolume("noobie");
        cache.addCacheVolume(vol, 50000, null, false);
        volnames = cache.volumeNames();
        assertTrue(volnames.contains("noobie"), "Missing volume");

        JSONObject md = cache.getInventoryDB().getVolumeInfo("noobie");
        assertEquals(VOL_FOR_UPDATE, md.getInt("status"));
        assertEquals(50000, md.getLong("capacity"));

        md = new JSONObject();
        md.put("status", VOL_FOR_GET);
        md.put("foo", "bar");
        cache.addCacheVolume(vol, 60000, md, true);

        md = cache.getInventoryDB().getVolumeInfo("noobie");
        assertEquals(VOL_FOR_GET, md.getInt("status"));
        assertEquals(60000, md.getLong("capacity"));
        assertEquals("bar", md.getString("foo"));
    }
}
