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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.inventory.ChecksumCheck;
import gov.nist.oar.distrib.cachemgr.inventory.SizeCheck;
import gov.nist.oar.distrib.cachemgr.pdr.BagCacherTest;

import java.nio.file.Path;

public class BasicIntegrityMonitorTest {

    BagCacherTest cachert = new BagCacherTest();
    BasicIntegrityMonitor mon = null;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() throws StorageVolumeException, ResourceNotFoundException, CacheManagementException, IOException {
        cachert.tempDir = tempDir;  // tempDir is already a Path
        cachert.setUp();
        cachert.cacher.cacheDataset("mds1491");
    }

    @AfterEach
    public void tearDown() {
        cachert.tearDown();
    }

    @Test
    public void testCtor() {
        mon = new BasicIntegrityMonitor("Fred", cachert.sidb, cachert.cache.volumes);
        assertEquals("Fred", mon.getCacheName());
        assertEquals(cachert.sidb, mon.db);
        assertEquals(cachert.cache.volumes.size(), mon.volumes.size());
        assertEquals(0, mon.checks.size());
        assertNull(mon.selstrat);

        List<CacheObjectCheck> chks = new ArrayList<>();
        chks.add(new SizeCheck());
        chks.add(new ChecksumCheck());
        mon = new BasicIntegrityMonitor("Amy", cachert.sidb, cachert.cache.volumes, chks);
        assertEquals("Amy", mon.getCacheName());
        assertEquals(cachert.sidb, mon.db);
        assertEquals(cachert.cache.volumes.size(), mon.volumes.size());
        assertEquals(2, mon.checks.size());
        assertNull(mon.selstrat);
    }

    @Test
    public void testSelectObjectsToBeChecked() throws InventoryException {
        mon = new BasicIntegrityMonitor("test", cachert.sidb, cachert.cache.volumes);
        List<CacheObject> cos = mon.selectObjectsToBeChecked(20);
        assertEquals(14, cos.size());
        assertEquals(1, cos.stream().filter(c -> c.id.equals("mds1491/trial1.json#1.1.0")).count());
        assertEquals(1, cos.stream().filter(c -> c.id.equals("mds1491/trial2.json#1.1.0")).count());
        assertEquals(1, cos.stream().filter(c -> c.id.equals("mds1491/trial3/trial3a.json#1.1.0")).count());
    }

    @Test
    public void testCheck() throws IntegrityException, StorageVolumeException, CacheManagementException {
        List<CacheObject> cos = cachert.sidb.findObject("mds1491/trial1.json#1.1.0");
        CacheObject co = cos.get(0);
        assertNotNull(co);
        co.volume = cachert.cache.volumes.get(co.volname);
        mon = new BasicIntegrityMonitor("Fred", cachert.sidb, cachert.cache.volumes);

        // no checks registered
        mon.check(co);

        List<CacheObjectCheck> chks = new ArrayList<>();
        chks.add(new SizeCheck());
        chks.add(new ChecksumCheck());
        mon = new BasicIntegrityMonitor("Fred", cachert.sidb, cachert.cache.volumes, chks);
        mon.check(co);

        chks.remove(1);
        mon = new BasicIntegrityMonitor("Fred", cachert.sidb, cachert.cache.volumes, chks);
        JSONObject md = co.exportMetadata();
        md.put("size", 10);
        co = new CacheObject(co.name, md, co.volume);
        co.id = "mds1491/trial1.json#1.1.0";

        try {
            mon.check(co);
            fail("Failed to detect bad size: " + co.getSize());
        } catch (IntegrityException ex) {
            // Expected exception
        }
    }

    @Test
    public void testSelectCorruptedOjects() throws InventoryException, StorageVolumeException, CacheManagementException {
        List<CacheObjectCheck> chks = new ArrayList<>();
        chks.add(new SizeCheck());
        mon = new BasicIntegrityMonitor("Fred", cachert.sidb, cachert.cache.volumes, chks);
        List<CacheObject> cos = mon.selectObjectsToBeChecked(20);

        ArrayList<CacheObject> failed = new ArrayList<>();
        assertEquals(14, mon.selectCorruptedObjects(cos, failed, false));
        assertEquals(0, failed.size());

        chks.add(new ChecksumCheck());
        mon = new BasicIntegrityMonitor("Fred", cachert.sidb, cachert.cache.volumes, chks);
        mon.selectCorruptedObjects(cos, failed, false);
        assertTrue(failed.size() > 0);

        cos = cos.stream().filter(c -> c.id.contains("trial")).collect(Collectors.toList());
        failed = new ArrayList<>();
        assertEquals(3, mon.selectCorruptedObjects(cos, failed, false));
        assertEquals(0, failed.size());

        CacheObject co = cos.get(0);

        // set an incorrect size for one object
        co = cos.get(0);
        JSONObject md = co.exportMetadata();
        md.put("size", 10);
        co = new CacheObject(co.name, md, co.volume);
        cos.set(0, co);

        assertEquals(2, mon.selectCorruptedObjects(cos, failed, false));
        assertEquals(1, failed.size());
        assertEquals(co.name, failed.get(0).name);
        assertTrue(failed.get(0).volume.exists(failed.get(0).name));  // failed CO is still in volume
        assertEquals(co.volname, cachert.sidb.findObject(failed.get(0).volname, failed.get(0).name).volname);
    }

    @Test
    public void testFindCorruptedOjects() throws InventoryException, StorageVolumeException, CacheManagementException {
        List<CacheObjectCheck> chks = new ArrayList<>();
        chks.add(new SizeCheck());
        mon = new BasicIntegrityMonitor("test", cachert.sidb, cachert.cache.volumes, chks);

        // set an incorrect size for one object
        List<CacheObject> cos = cachert.sidb.findObject("mds1491/trial1.json#1.1.0");
        CacheObject co = cos.get(0);
        JSONObject md = new JSONObject();
        md.put("size", 10);
        cachert.sidb.updateMetadata(co.volname, co.name, md);

        ArrayList<CacheObject> failed = new ArrayList<>();
        assertEquals(13, mon.findCorruptedObjects(20, failed, false));
        assertEquals(1, failed.size());

        cos = mon.selectObjectsToBeChecked(20);
        assertEquals(1, cos.size());
        assertEquals(failed.get(0).name, cos.get(0).name);
    }
}
