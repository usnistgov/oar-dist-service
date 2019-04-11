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

}
