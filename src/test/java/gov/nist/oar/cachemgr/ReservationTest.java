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
package gov.nist.oar.cachemgr;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import gov.nist.oar.cachemgr.CacheVolume;
import gov.nist.oar.cachemgr.CacheVolumeException;
import gov.nist.oar.cachemgr.InventoryException;
import gov.nist.oar.cachemgr.StorageInventoryDB;
import gov.nist.oar.cachemgr.inventory.SQLiteStorageInventoryDB;
import gov.nist.oar.cachemgr.storage.FilesystemCacheVolume;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.json.JSONObject;
import org.json.JSONException;

public class ReservationTest {

    @Rule
    public final TemporaryFolder tempf = new TemporaryFolder();

    private CacheVolume tvol = null;
    private StorageInventoryDB tdb = null;

    class TestSQLiteStorageInventoryDB extends SQLiteStorageInventoryDB {
        public TestSQLiteStorageInventoryDB(String fn) { super(fn); }
        public int do_getVolumeID(String name) throws InventoryException { return getVolumeID(name); }
        public int do_getAlgorithmID(String name) throws InventoryException {
            return getAlgorithmID(name);
        }
    }

    String createDB() throws IOException, InventoryException {
        File tf = tempf.newFile("testdb.sqlite");
        String out = tf.getAbsolutePath();
        SQLiteStorageInventoryDB.initializeDB(out);
        return out;
    }

    @Before
    public void setUp() throws IOException, InventoryException {
        File vold = tempf.newFolder("cache");
        tvol = new FilesystemCacheVolume(vold.toString(), "gary");
        
        String dbf = createDB();
        tdb = new TestSQLiteStorageInventoryDB(dbf);
        tdb.registerVolume("gary", 200000, null);
        tdb.registerAlgorithm("sha256");
        tdb.registerAlgorithm("shaRay");
    }

    public void addRes2DB(String resname, long size) throws InventoryException {
        JSONObject md = new JSONObject();
        md.put("size", size);
        tdb.addObject("gary/"+resname, "gary", resname, md);
    }

    @Test
    public void testCtor() throws InventoryException {
        long size = 40L;
        String rnm = "_reservationXXX";
        addRes2DB(rnm, size);
        Reservation res = new Reservation(rnm, tvol, tdb, size);

        assertEquals(40L, res.getSize());
        assertEquals("gary", res.getVolumeName());
        assertEquals(rnm, res.getReservationName());
    }

    @Test
    public void testSaveAs() throws CacheManagementException {
        long size = 40L;
        String rnm = "_reservationXXX";
        assertEquals(15, rnm.length());
        addRes2DB(rnm, size);
        Reservation res = new Reservation(rnm, tvol, tdb, size);

        InputStream is = new ByteArrayInputStream(rnm.getBytes());
        JSONObject md = new JSONObject();
        md.put("size", (long) rnm.length());
        md.put("checksum", "YYZ");
        md.put("checksumAlgorithm", "shaRay");
        md.put("priority", 4);
        try {
            res.saveAs(is, "gary/busey", "busey", md);
        }
        finally {
            try { is.close(); } catch (IOException ex) { } 
        }

        assertEquals(25L, res.getSize());
        List<CacheObject> cos = tdb.findObject("gary/busey");
        assertEquals(1, cos.size());
        assertEquals("busey", cos.get(0).name);
        assertEquals(15L, cos.get(0).getSize());
        assertEquals(4, cos.get(0).getMetadatumInt("priority", -1));
        assertEquals("YYZ", cos.get(0).getMetadatumString("checksum", null));
        assertEquals("shaRay", cos.get(0).getMetadatumString("checksumAlgorithm", null));
        assertTrue("since not in metadata properties",
                   cos.get(0).metadatumNames().contains("since"));
        assertTrue("sinceDate not in metadata properties",
                   cos.get(0).metadatumNames().contains("sinceDate"));

        cos = tdb.findObject("gary/"+rnm);
        assertEquals(1, cos.size());
        assertEquals(rnm, cos.get(0).name);
        assertEquals(25L, cos.get(0).getSize());
    }


    @Test
    public void testSaveAsTooShort() throws CacheManagementException {
        long size = 40L;
        String rnm = "_reservationXXX";
        assertEquals(15, rnm.length());
        addRes2DB(rnm, size);
        Reservation res = new Reservation(rnm, tvol, tdb, size);

        InputStream is = new ByteArrayInputStream(rnm.getBytes());
        JSONObject md = new JSONObject();
        md.put("size", 45L);
        md.put("checksum", "YYZ");
        md.put("checksumAlgorithm", "shaRay");
        md.put("priority", 4);
        try {
            res.saveAs(is, "gary/busey", "busey", md);
            fail("saveAs did not throw CacheManagementException");
        } catch (CacheManagementException ex) {
            assertTrue(ex.getMessage().startsWith("Too few bytes"));
        }
        finally {
            try { is.close(); } catch (IOException ex) { } 
        }
        
    }

    @Test
    public void testSaveAsNoMD() throws CacheManagementException {
        long size = 40L;
        String rnm = "_reservationXXX";
        assertEquals(15, rnm.length());
        addRes2DB(rnm, size);
        Reservation res = new Reservation(rnm, tvol, tdb, size);

        InputStream is = new ByteArrayInputStream(rnm.getBytes());
        try {
            res.saveAs(is, "gary/busey", "busey");
        }
        finally {
            try { is.close(); } catch (IOException ex) { } 
        }

        assertEquals(25L, res.getSize());
        
        List<CacheObject> cos = tdb.findObject("gary/busey");
        assertEquals(1, cos.size());
        assertEquals("busey", cos.get(0).name);
        assertEquals(15L, cos.get(0).getSize());
        assertNull(cos.get(0).getMetadatumString("checksum", null));
        assertTrue("since not in metadata properties",
                   cos.get(0).metadatumNames().contains("since"));
        assertTrue("sinceDate not in metadata properties",
                   cos.get(0).metadatumNames().contains("sinceDate"));

        cos = tdb.findObject("gary/"+rnm);
        assertEquals(1, cos.size());
        assertEquals(rnm, cos.get(0).name);
        assertEquals(25L, cos.get(0).getSize());
    }

    @Test
    public void testSaveAsTooMuch() throws CacheManagementException {
        long size = 20L;
        String rnm = "_reservationXXX";
        assertEquals(15, rnm.length());
        addRes2DB(rnm, size);
        Reservation res = new Reservation(rnm, tvol, tdb, size);

        InputStream is = new ByteArrayInputStream(rnm.getBytes());
        try {
            res.saveAs(is, "gary/busey", "busey");
        } finally {
            try { is.close(); } catch (IOException ex) { } 
        }
        assertEquals(5L, res.getSize());
        is = new ByteArrayInputStream(rnm.getBytes());
        try {
            res.saveAs(is, "gary/hart", "hart");
        } finally {
            try { is.close(); } catch (IOException ex) { } 
        }
        assertEquals(-10L, res.getSize());
        
        is = new ByteArrayInputStream(rnm.getBytes());
        try {
            res.saveAs(is, "gary/hart", "hart");
            fail("Writing too much data did not throw CacheManagementException");
        }
        catch (CacheManagementException ex) {
            assertTrue(ex.getMessage().startsWith("No more space"));
        }            
        finally {
            try { is.close(); } catch (IOException ex) { } 
        }

        List<CacheObject> cos = tdb.findObject("gary/busey");
        assertEquals(1, cos.size());
        assertEquals("busey", cos.get(0).name);
        assertEquals(15L, cos.get(0).getSize());
        assertNull(cos.get(0).getMetadatumString("checksum", null));
        assertTrue("since not in metadata properties",
                   cos.get(0).metadatumNames().contains("since"));
        assertTrue("sinceDate not in metadata properties",
                   cos.get(0).metadatumNames().contains("sinceDate"));

        // reservation entry should now be gone (thanks to drop())
        cos = tdb.findObject("gary/"+rnm);
        assertEquals(0, cos.size());

        cos = tdb.findObject("gary/hart");
        assertEquals(1, cos.size());
        assertEquals("hart", cos.get(0).name);
        assertEquals(15L, cos.get(0).getSize());
        assertNull(cos.get(0).getMetadatumString("checksum", null));
        assertTrue("since not in metadata properties",
                   cos.get(0).metadatumNames().contains("since"));
        assertTrue("sinceDate not in metadata properties",
                   cos.get(0).metadatumNames().contains("sinceDate"));
    }

    @Test
    public void testGenerateName() {
        String nm;
        Set<String> names = new HashSet<String>(101);
        for(int i=0; i < 100; i++) {
            nm = Reservation.generateName("_reserv,", 8);
            assertTrue(nm.startsWith("_reserv"));
            assertEquals(16, nm.length());
            names.add(nm);
        }
        assertEquals(100, names.size());
    }

    @Test
    public void testReservationFor() throws InventoryException {
        Reservation res = Reservation.reservationFor(tvol, tdb, 100L);

        String id = tvol.getName()+":"+res.getReservationName();
        String resname = res.getReservationName();
        List<CacheObject> cos = tdb.findObject(id);
        assertEquals(1, cos.size());
        assertEquals(resname, cos.get(0).name);
        assertEquals(100L, cos.get(0).getSize());

        res = Reservation.reservationFor(tvol, tdb, 200);
        res = Reservation.reservationFor(tvol, tdb, 300);
        res = Reservation.reservationFor(tvol, tdb, 400);

        cos = tdb.findObject(id);
        assertEquals(1, cos.size());
        assertEquals(resname, cos.get(0).name);
        assertEquals(100L, cos.get(0).getSize());

        id = tvol.getName()+":"+res.getReservationName();
        cos = tdb.findObject(id);
        assertEquals(1, cos.size());
        assertEquals(res.getReservationName(), cos.get(0).name);
        assertEquals(400L, cos.get(0).getSize());
    }
}
