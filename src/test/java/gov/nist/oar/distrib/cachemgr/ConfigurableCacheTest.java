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

import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.inventory.OldSelectionStrategy;
import gov.nist.oar.distrib.cachemgr.inventory.BigOldSelectionStrategy;
import gov.nist.oar.distrib.cachemgr.inventory.BySizeSelectionStrategy;
import gov.nist.oar.distrib.cachemgr.storage.NullCacheVolume;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * tests BasicCache, too
 */
public class ConfigurableCacheTest {

    @Rule
    public final TemporaryFolder tempf = new TemporaryFolder();

    File dbf = null;
    StorageInventoryDB sidb = null;
    List<CacheVolume> cvlist = null;
    ConfigurableCache cache = null;

    String createDB() throws IOException, InventoryException {
        File tf = tempf.newFile("testdb.sqlite");
        String out = tf.getAbsolutePath();
        SQLiteStorageInventoryDB.initializeDB(out);
        return out;
    }

    class ConfigurableCacheForTesting extends ConfigurableCache {
        public ConfigurableCacheForTesting(String name, StorageInventoryDB db) {
            super(name, db);
        }
    }

    @After
    public void tearDown() {
        dbf.delete();
    }

    @Before
    public void setUp() throws IOException, InventoryException {
        cache = null;
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

    ConfigurableCache createCache() {
        ConfigurableCache out = new ConfigurableCacheForTesting("test", sidb);
        return out;
    }

    @Test
    public void testCtor() throws CacheManagementException {
        cache = createCache();
        DeletionPlanner ds = cache.getDeletionPlanner(0);
        assertNotNull("No planner!", ds);
    }

    @Test
    public void testConfigure() throws CacheManagementException {
        cache = createCache();
        try {
            cache.reserveSpace(100);
            fail("Found space in cache without volumes");
        } catch (DeletionFailureException ex) { }

        // test normal use of attached volumes
        VolumeConfig vcfg = new VolumeConfig();
        for (CacheVolume cv : cvlist) {
            cache.addCacheVolume(cv, 22000, null, vcfg, true);
        }
        Set<String> vnames = cache.volumeNames();
        Reservation resv = cache.reserveSpace(100);
        assertTrue(vnames.contains(resv.getVolumeName()));
        resv.drop();

        // test updmd=false to prevent update of metadata
        vcfg = (new VolumeConfig()).withStatus(VolumeStatus.VOL_FOR_INFO);
        for (CacheVolume cv : cvlist) {
            cache.addCacheVolume(cv, 22000, null, vcfg, false);
        }
        resv = cache.reserveSpace(100);
        assertTrue(vnames.contains(resv.getVolumeName()));
        resv.drop();

        vcfg = (new VolumeConfig()).withStatus(VolumeStatus.VOL_FOR_GET);
        for (CacheVolume cv : cvlist) {
            cache.addCacheVolume(cv, 22000, null, vcfg, true);
        }
        try {
            cache.reserveSpace(100);
            fail("Found space in unavailable caches");
        } catch (DeletionFailureException ex) { }

        // This will fail because you can't upgrade status via addCacheVolume()
        vcfg = (new VolumeConfig()).withStatus(VolumeStatus.VOL_FOR_UPDATE);
        for (CacheVolume cv : cvlist) {
            cache.addCacheVolume(cv, 22000, null, vcfg, true);
        }
        try {
            cache.reserveSpace(100);
            fail("Found space in unavailable caches");
        } catch (DeletionFailureException ex) { }

        // reset status on one volume and confirm it works
        cache.getInventoryDB().setVolumeStatus(cvlist.get(0).getName(), VolumeStatus.VOL_FOR_UPDATE);
        resv = cache.reserveSpace(100);
        assertEquals(resv.getVolumeName(), cvlist.get(0).getName());
        resv.drop();

        // turn off again
        vcfg.setStatus(VolumeStatus.VOL_DISABLED);
        for (CacheVolume cv : cvlist) {
            cache.addCacheVolume(cv, 22000, null, vcfg, true);
        }
        try {
            cache.reserveSpace(100);
            fail("Found space in unavailable caches");
        } catch (DeletionFailureException ex) { }

        // add a new volume
        CacheVolume vol = new NullCacheVolume("noobie");
        vcfg = (new VolumeConfig()).withRoles(1);
        cache.addCacheVolume(vol, 30000, null, vcfg, false);
        vnames = cache.volumeNames();
        assertTrue("new volume not added to cache", vnames.contains("noobie"));
        JSONObject md = sidb.getVolumeInfo("noobie");
        assertEquals(md.optInt("status", 100), VolumeStatus.VOL_FOR_UPDATE);
        assertEquals(md.optLong("capacity", 0L), 30000);
        assertEquals(md.optInt("roles", 8), 1);

        resv = cache.reserveSpace(100);
        assertEquals(resv.getVolumeName(), "noobie");
        resv.drop();
    }

    @Test
    public void testSelectVolumes() throws CacheManagementException {
        cache = createCache();
        VolumeConfig vcfg = new VolumeConfig();
        Collection<CacheVolume> cvs = null;
        Set<String> nms = null;

        vcfg.setRoles(1);
        cache.addCacheVolume(cvlist.get(0), 22000, null, vcfg, true);   // foobar
        vcfg.setRoles(2);
        cache.addCacheVolume(new NullCacheVolume("goofy"), 22000, null, vcfg, true);
        vcfg.setRoles(6);
        cache.addCacheVolume(new NullCacheVolume("noobie"), 30000, null, vcfg, true);

        cvs = cache.selectVolumes(0);
        assertEquals(3, cvs.size());
        nms = cvs.stream().map(cv -> cv.getName()).collect(Collectors.toSet());
        assertTrue(! nms.contains("cranky"));
        assertTrue(nms.contains("foobar"));
        assertTrue(nms.contains("goofy"));
        assertTrue(nms.contains("noobie"));

        cvs = cache.selectVolumes(1);
        assertEquals(1, cvs.size());
        nms = cvs.stream().map(cv -> cv.getName()).collect(Collectors.toSet());
        assertTrue(! nms.contains("cranky"));
        assertTrue(nms.contains("foobar"));
        assertTrue(! nms.contains("goofy"));
        assertTrue(! nms.contains("noobie"));

        cvs = cache.selectVolumes(2);
        assertEquals(2, cvs.size());
        nms = cvs.stream().map(cv -> cv.getName()).collect(Collectors.toSet());
        assertTrue(! nms.contains("cranky"));
        assertTrue(! nms.contains("foobar"));
        assertTrue(nms.contains("goofy"));
        assertTrue(nms.contains("noobie"));

        cvs = cache.selectVolumes(3);
        assertEquals(3, cvs.size());
        nms = cvs.stream().map(cv -> cv.getName()).collect(Collectors.toSet());
        assertTrue(! nms.contains("cranky"));
        assertTrue(nms.contains("foobar"));
        assertTrue(nms.contains("goofy"));
        assertTrue(nms.contains("noobie"));

        cvs = cache.selectVolumes(4);
        assertEquals(1, cvs.size());
        nms = cvs.stream().map(cv -> cv.getName()).collect(Collectors.toSet());
        assertTrue(! nms.contains("cranky"));
        assertTrue(! nms.contains("foobar"));
        assertTrue(! nms.contains("goofy"));
        assertTrue(nms.contains("noobie"));

        cvs = cache.selectVolumes(5);
        assertEquals(2, cvs.size());
        nms = cvs.stream().map(cv -> cv.getName()).collect(Collectors.toSet());
        assertTrue(! nms.contains("cranky"));
        assertTrue(nms.contains("foobar"));
        assertTrue(! nms.contains("goofy"));
        assertTrue(nms.contains("noobie"));

        cvs = cache.selectVolumes(6);
        assertEquals(2, cvs.size());
        nms = cvs.stream().map(cv -> cv.getName()).collect(Collectors.toSet());
        assertTrue(! nms.contains("cranky"));
        assertTrue(! nms.contains("foobar"));
        assertTrue(nms.contains("goofy"));
        assertTrue(nms.contains("noobie"));

        cvs = cache.selectVolumes(7);
        assertEquals(3, cvs.size());
        nms = cvs.stream().map(cv -> cv.getName()).collect(Collectors.toSet());
        assertTrue(! nms.contains("cranky"));
        assertTrue(nms.contains("foobar"));
        assertTrue(nms.contains("goofy"));
        assertTrue(nms.contains("noobie"));
    }

    @Test
    public void testGetStrategyFor() throws CacheManagementException {
        cache = createCache();
        VolumeConfig vcfg = new VolumeConfig();
        DeletionStrategy strat = null;

        cache.addCacheVolume(cvlist.get(0), 22000, null, vcfg, true);   // foobar
        vcfg.setDeletionStrategy(new BySizeSelectionStrategy(20000L));
        cache.addCacheVolume(new NullCacheVolume("goofy"), 22000, null, vcfg, true);
        vcfg.setDeletionStrategy(new BigOldSelectionStrategy(20000L));
        cache.addCacheVolume(new NullCacheVolume("noobie"), 30000, null, vcfg, true);

        strat = cache.getStrategyFor("foobar", 10, 20);
        assertTrue("Wrong strategy", strat instanceof OldSelectionStrategy);
        strat = cache.getStrategyFor("goofy", 10, 20);
        assertTrue("Wrong strategy", strat instanceof BySizeSelectionStrategy);
        strat = cache.getStrategyFor("noobie", 10, 20);
        assertTrue("Wrong strategy", strat instanceof BigOldSelectionStrategy);
    }
}
