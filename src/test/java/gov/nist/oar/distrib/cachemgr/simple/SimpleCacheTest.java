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

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.DeletionPlan;
import gov.nist.oar.distrib.cachemgr.DeletionPlanner;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.Reservation;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.Cache;
import gov.nist.oar.distrib.cachemgr.VolumeStatus;
import gov.nist.oar.distrib.cachemgr.storage.NullCacheVolume;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * tests BasicCache, too
 */
public class SimpleCacheTest
    implements SimpleCache.ReservationListener, SimpleCache.SaveListener, SimpleCache.DeletionListener,
               VolumeStatus
{
    @Rule
    public final TemporaryFolder tempf = new TemporaryFolder();

    File dbf = null;
    StorageInventoryDB sidb = null;
    List<CacheVolume> cvlist = null;
    SimpleCache cache = null;
    String heard = "";

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
        cache = null;
        heard = "";
        dbf = new File(createDB());
        assertTrue(dbf.exists());

        sidb = new SQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");

        cvlist = new ArrayList<CacheVolume>(2);
        NullCacheVolume cv = new NullCacheVolume("foobar");
        cvlist.add(cv);
        sidb.registerVolume("foobar", 22000, null);
        cv = new NullCacheVolume("cranky");
        cvlist.add(cv);
        sidb.registerVolume("cranky", 20000, null);
        
        int[] sizes = { 229, 9321, 9001, 980, 2230, 100 };
        addDataToVolume(sizes, (NullCacheVolume) cvlist.get(0));
        int[] sizesc = { 229, 6321, 953, 10031, 2230, 100 };
        addDataToVolume(sizesc, (NullCacheVolume) cvlist.get(1));
    }

    public long addDataToVolume(int[] sizes, NullCacheVolume cv) throws InventoryException {
        long now = System.currentTimeMillis();
        String volname = cv.getName();

        String nm = null;
        JSONObject md = null;
        long total = 0L;
        for (int i=0; i < sizes.length; i++) {
            nm = Integer.toString(i);
            md = new JSONObject();
            md.put("size", sizes[i]);
            sidb.addObject(volname+nm, volname, nm, md);
            total += sizes[i];
            md.put("since", now-(sizes[i]*60000));
            sidb.updateMetadata(volname, nm, md);
            cv.addObjectName(nm);
        }

        return total;
    }

    @Test
    public void testCtor() throws CacheManagementException {
        cache = new SimpleCache("Simple", sidb, cvlist);
        DeletionPlanner ds = cache.getDeletionPlanner();
        assertNotNull("No planner!", ds);
        assertTrue("Can't find foobar2", cache.isCached("foobar2"));
        assertTrue("Can't find cranky4", cache.isCached("cranky4"));
        assertFalse("Found fictional object, goober", cache.isCached("goober"));
    }

    @Test
    public void testUncache() throws CacheManagementException {
        cache = new SimpleCache("Simple", sidb, cvlist);
        DeletionPlanner ds = cache.getDeletionPlanner();
        assertNotNull("No planner!", ds);
        assertTrue("Can't find foobar2", cache.isCached("foobar2"));
        assertTrue("Can't find cranky4", cache.isCached("cranky4"));

        cache.uncache("foobar2");
        assertFalse("Failed to delete foobar2", cache.isCached("foobar2"));
        assertTrue("Can't find cranky4", cache.isCached("cranky4"));

        cache.uncache("cranky4");
        assertFalse("foobar2 reappeared!", cache.isCached("foobar2"));
        assertFalse("Failed to delete cranky4", cache.isCached("cranky4"));
    }

    @Test
    public void testFindObject() throws CacheManagementException {
        long now = System.currentTimeMillis();
        
        cache = new SimpleCache("Simple", sidb, cvlist);
        DeletionPlanner ds = cache.getDeletionPlanner();
        assertNotNull("No planner!", ds);
        assertTrue("Can't find foobar2", cache.isCached("foobar2"));
        assertTrue("Can't find cranky4", cache.isCached("cranky4"));

        CacheObject co = cache.findObject("foobar2");
        assertEquals("foobar2", co.id);
        assertEquals("2", co.name);
        assertEquals(9001, co.getSize());
        assertTrue("Funny timestamp on foobar2: age="+Long.toString(now-co.getMetadatumLong("since", 0L)),
                   now-co.getMetadatumLong("since", 0L)-9001*60000 < 5000);

        co = cache.findObject("cranky1");
        assertEquals("cranky1", co.id);
        assertEquals("1", co.name);
        assertEquals(6321, co.getSize());
        assertTrue("Funny timestamp on cranky1: age="+Long.toString(now-co.getMetadatumLong("since", 0L)),
                   now-co.getMetadatumLong("since", 0L)-6321*60000 < 5000);
    }

    @Test
    public void testReserveSpaceWithPrefs() throws CacheManagementException {
        long now = System.currentTimeMillis();
        
        cache = new SimpleCache("Simple", sidb, cvlist);
        assertNotNull("No planner!", cache.getDeletionPlanner());

        Reservation res = cache.reserveSpace(4000L, 0);
        assertEquals(4000, res.getSize());
        assertEquals("cranky", res.getVolumeName());
    }

    @Test
    public void testReserveSpace2() throws CacheManagementException {
        long now = System.currentTimeMillis();
        
        cache = new SimpleCache("Simple", sidb, cvlist);
        assertNotNull("No planner!", cache.getDeletionPlanner());

        Reservation res = cache.reserveSpace(20000L, 0);
        assertEquals(20000, res.getSize());
        assertEquals("foobar", res.getVolumeName());
    }

    @Test
    public void testReserveSpace() throws CacheManagementException {
        long now = System.currentTimeMillis();
        
        cache = new SimpleCache("Simple", sidb, cvlist);
        assertNotNull("No planner!", cache.getDeletionPlanner());

        Reservation res = cache.reserveSpace(4000L);
        assertEquals(4000, res.getSize());
        assertEquals("cranky", res.getVolumeName());
    }

    public boolean objectsDeleted(Cache c, CacheVolume vol, List<String> deleted, long freed) {
        Logger log = LoggerFactory.getLogger("SimpleCacheTest");
        assertTrue("Unexpected cache given to listener", c == cache);
        assertNotNull(vol);
        log.info("Objects deleted from "+c.getName()+":"+vol.getName());
        // assertNotNull(deleted);  // Not supported yet
        // assertTrue(deleted.size() > 0);
        assertTrue(freed > 0);
        heard += String.format("freed on cranky: %d; ", freed);
        return true;
    }

    public boolean objectSaved(Cache c, CacheObject obj) {
        Logger log = LoggerFactory.getLogger("SimpleCacheTest");
        assertTrue("Unexpected cache given to listener", c == cache);
        assertNotNull(obj);
        log.info("Saved object to "+c.getName()+":"+obj.volume.getName());
        heard += String.format("saved to %s: %d; ", obj.volume.getName(), obj.getSize());
        return true;
    }
            
    public boolean reservationMade(Cache c, CacheVolume vol, long size) {
        Logger log = LoggerFactory.getLogger("SimpleCacheTest");
        assertTrue("Unexpected cache given to listener", c == cache);
        assertNotNull(vol);
        log.info("Reservation made in "+c.getName()+":"+vol.getName());
        assertTrue(size > 0);
        heard += String.format("reservation on %s: %d; ", vol.getName(), size);
        return false;
    }

    @Test
    public void testListeners() throws CacheManagementException {
        long now = System.currentTimeMillis();
        
        cache = new SimpleCache("Simple", sidb, cvlist);
        cache.addReservationListener(this);
        cache.addDeletionListener(this);
        cache.addSaveListener(this);
        assertNotNull("No planner!", cache.getDeletionPlanner());

        Reservation res = cache.reserveSpace(4000L);
        assertEquals(4000, res.getSize());
        assertEquals("cranky", res.getVolumeName());
        assertTrue("Missing listener message in "+heard,
                   heard.contains("reservation on cranky: 4000"));
        assertTrue("Missing listener message in "+heard,
                   heard.contains("freed on cranky: 10031"));

        heard = "";
        res = cache.reserveSpace(300L);
        assertTrue("Missing listener message in "+heard,
                   heard.contains("reservation on cranky: 300"));  // because listener was not removed
        assertTrue("Unexpected listener message in "+heard,
                   ! heard.contains("freed on cranky: "));  // because listener was removed

        JSONObject md = new JSONObject();
        md.put("size", 13L);
        ByteArrayInputStream objstrm = new ByteArrayInputStream("file contents".getBytes());
        try {
            res.saveAs(objstrm, "Gurn", "gurn.txt", md);
            assertTrue("Missing listener message in "+heard,
                       heard.contains("saved to cranky: 13;"));
        }
        finally {
            try { objstrm.close(); } catch (IOException ex) { }
        }
    }

    @Test
    public void testVolumeNames() {
        cache = new SimpleCache("Simple", sidb, cvlist);
        Set<String> volnames = cache.volumeNames();
        assertTrue("Missing volume", volnames.contains("cranky"));
        assertTrue("Missing volume", volnames.contains("foobar"));
    }

    @Test
    public void testAddCacheVolume() throws CacheManagementException {
        cache = new SimpleCache("Simple", sidb, cvlist);
        Set<String> volnames = cache.volumeNames();
        assertTrue("Missing volume", volnames.contains("cranky"));
        assertTrue("Missing volume", volnames.contains("foobar"));
        assertTrue("Missing volume", ! volnames.contains("noobie"));

        // add new volume for first time
        CacheVolume vol = new NullCacheVolume("noobie");
        cache.addCacheVolume(vol, 50000, null, false);
        volnames = cache.volumeNames();
        assertTrue("Missing volume", volnames.contains("cranky"));
        assertTrue("Missing volume", volnames.contains("foobar"));
        assertTrue("Missing volume", volnames.contains("noobie"));
        
        // vol = cache.getVolume("noobie");
        // assertNotNull(vol);

        JSONObject md = cache.getInventoryDB().getVolumeInfo("noobie");
        assertEquals(md.getInt("status"), VOL_FOR_UPDATE);
        assertEquals(md.getLong("capacity"), 50000);

        // update the metadata
        md = new JSONObject();
        md.put("status", VOL_FOR_GET);
        md.put("foo", "bar");
        cache.addCacheVolume(vol, 60000, md, true);
        
        md = cache.getInventoryDB().getVolumeInfo("noobie");
        assertEquals(md.getInt("status"), VOL_FOR_GET);
        assertEquals(md.getLong("capacity"), 60000);
        assertEquals(md.getString("foo"), "bar");

        // update the metadata again, but try upgrading status
        md = new JSONObject();
        md.put("status", VOL_FOR_UPDATE);
        md.put("foo", "bend");
        cache.addCacheVolume(vol, 40000, md, true);
        
        md = cache.getInventoryDB().getVolumeInfo("noobie");
        assertEquals(md.getInt("status"), VOL_FOR_GET);
        assertEquals(md.getLong("capacity"), 40000);
        assertEquals(md.getString("foo"), "bend");

        // test update only if not registered
        md = new JSONObject();
        md.put("status", VOL_FOR_UPDATE);
        md.put("foo", "bar");
        cache.addCacheVolume(vol, 50000, md, false);
        
        md = cache.getInventoryDB().getVolumeInfo("noobie");
        assertEquals(md.getInt("status"), VOL_FOR_GET);
        assertEquals(md.getLong("capacity"), 40000);
        assertEquals(md.getString("foo"), "bend");
        
    }

}
