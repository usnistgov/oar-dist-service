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
package gov.nist.oar.cachemgr.unit;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import gov.nist.oar.cachemgr.CacheObject;
import gov.nist.oar.cachemgr.InventoryException;
import gov.nist.oar.cachemgr.StorageInventoryDB;
import gov.nist.oar.cachemgr.inventory.JDBCStorageInventoryDB;
import gov.nist.oar.cachemgr.inventory.SQLiteStorageInventoryDB;

import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

import org.json.JSONObject;
import org.json.JSONException;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * this test also tests the JDBCStorageInventoryDB implementation
 */
public class SQLiteStorageInventoryDBTest {

    @Rule
    public final TemporaryFolder tempf = new TemporaryFolder();

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

    List<String> getStringColumn(ResultSet rs, int colindex) throws SQLException {
        ArrayList<String> out = new ArrayList<String>();
        // rs.first();
        while (rs.next()) {
            out.add(rs.getString(colindex));
        }
        return out;
    }

    List<String> getStringColumn(ResultSet rs, String colname) throws SQLException {
        ArrayList<String> out = new ArrayList<String>();
        // rs.first();
        while (rs.next()) {
            out.add(rs.getString(colname));
        }
        return out;
    }

    @Test
    public void testInit() throws IOException, SQLException, InventoryException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());
        Connection conn = DriverManager.getConnection("jdbc:sqlite:"+dbf.toString());
        DatabaseMetaData dmd = conn.getMetaData();

        // check that we have tables defined
        List<String> svals = getStringColumn(dmd.getTableTypes(), 1);
        assertTrue(svals.contains("TABLE"));

        // check that our tables are defined
        String[] ss = { "TABLE" };
        svals = getStringColumn(dmd.getTables(null, null, null, ss), "TABLE_NAME");
        assertTrue("Missing volumes table", svals.contains("volumes"));
        assertTrue("Missing algorithms table", svals.contains("algorithms"));
        assertTrue("Missing objects table", svals.contains("objects"));
    }

    @Test
    public void testCtor() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());
        String[] mt = new String[0];
        assertArrayEquals(mt, sidb.volumes());
        assertArrayEquals(mt, sidb.checksumAlgorithms());
    }

    @Test
    public void testRegisterVolume() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());
        try {
            sidb.getVolumeInfo("fundrum");
            fail("getVolumeInfo() did not throw exception for non-existant volume request");
        } catch (InventoryException ex) { }

        String[] aneed = new String[] { "fundrum" };
        sidb.registerVolume("fundrum", 150000, null);
        assertArrayEquals(aneed, sidb.volumes());
        JSONObject md = sidb.getVolumeInfo("fundrum");
        assertEquals(150000, md.getInt("capacity"));
        assertEquals(null, md.opt("priority"));

        md.put("priority", 5);
        md.put("color", "red");
        sidb.registerVolume("fundrum", 200000, md);
        assertArrayEquals(aneed, sidb.volumes());
        md = sidb.getVolumeInfo("fundrum");
        assertEquals(200000, md.getInt("capacity"));
        assertEquals(5, md.getInt("priority"));

        sidb.registerVolume("foobar", 450000, md);
        String[] got = sidb.volumes();
        assertIn("foobar", got);
        assertIn("fundrum", got);
        assertEquals(2, got.length);
        md = sidb.getVolumeInfo("fundrum");
        assertEquals(200000, md.getInt("capacity"));
        assertEquals(5, md.getInt("priority"));
        md = sidb.getVolumeInfo("foobar");
        assertEquals(450000, md.getInt("capacity"));
        assertEquals(5, md.getInt("priority"));
    }

    @Test
    public void testAddObject() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", null);
        
        List<CacheObject> cos = sidb.findObject("1234/goober.json");
        assertEquals(1, cos.size());
        assertEquals("1234_goober.json", cos.get(0).name);
        assertEquals(-1L, cos.get(0).getSize());
        assertEquals(2, cos.get(0).metadatumNames().size());
        assertTrue("size not in metadata properties",
                   cos.get(0).metadatumNames().contains("size"));
        assertTrue("priority not in metadata properties",
                   cos.get(0).metadatumNames().contains("priority"));

        sidb.addObject("1234/goober.json", "fundrum", "1234_goober_2.json", null);
        cos = sidb.findObject("1234/goober.json");
        assertEquals(2, cos.size());
        assertEquals(-1L, cos.get(0).getSize());
        assertEquals(2, cos.get(0).metadatumNames().size());
        assertEquals(-1L, cos.get(1).getSize());
        assertEquals(2, cos.get(1).metadatumNames().size());

        JSONObject md = new JSONObject();
        md.put("priority", 4);
        md.put("size", 456L);
        md.put("checksum", "abcdef123456");
        md.put("checksumAlgorithm", "md5");
        md.put("color", "red");
        sidb.addObject("1234/goober.json", "fundrum", "1234_goober_2.json", md);
        cos = sidb.findObject("1234/goober.json");
        assertEquals(2, cos.size());
        CacheObject first=null, second=null;
        for(CacheObject co : cos) {
            if (co.name.equals("1234_goober.json"))
                first = co;
            else if (co.name.equals("1234_goober_2.json"))
                second = co;
        }
        assertNotNull("Failed to find first registered object", first);
        assertNotNull("Failed to find 2nd (updated) registered object", second);
        assertEquals(456L, second.getSize());
        assertEquals(5, second.metadatumNames().size());
        assertTrue("size not in metadata properties",
                   second.metadatumNames().contains("size"));
        assertTrue("priority not in metadata properties",
                   second.metadatumNames().contains("priority"));
        assertTrue("color not in metadata properties",
                   second.metadatumNames().contains("color"));
        assertTrue("checksum not in metadata properties",
                   second.metadatumNames().contains("checksum"));
        assertTrue("checksumAlgorithm not in metadata properties",
                   second.metadatumNames().contains("checksumAlgorithm"));
        assertEquals("md5", second.getMetadatumString("checksumAlgorithm", null));
        assertEquals(-1L, first.getSize());
        assertEquals(2, first.metadatumNames().size());

        md.put("size", 3196429990L);
        sidb.addObject("gurn.fits", "foobar", "a9ej_gurn.fits", md);
        cos = sidb.findObject("gurn.fits");
        assertEquals(1, cos.size());
        assertEquals("a9ej_gurn.fits", cos.get(0).name);
        assertEquals("foobar", cos.get(0).volume);
        assertEquals(3196429990L, cos.get(0).getSize());
        assertEquals(4, cos.get(0).getMetadatumInt("priority", 10));

        sidb.removeObject("fundrum", "a9ej_gurn.fits");
        cos = sidb.findObject("gurn.fits");
        assertEquals(1, cos.size());
        sidb.removeObject("foobar", "a9ej_gurn.fits");
        cos = sidb.findObject("gurn.fits");
        assertEquals(0, cos.size());
    }

    @Test
    public void testFindObjectVolume() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", null);
        sidb.addObject("1234/goober.json", "fundrum", "1234_goober.json", null);

        List<CacheObject> cos = sidb.findObject("1234/goober.json", "fundrum");
        assertEquals(1, cos.size());
        assertEquals("1234_goober.json", cos.get(0).name);
        assertEquals("fundrum", cos.get(0).volume);
        
        cos = sidb.findObject("1234/goober.json", "foobar");
        assertEquals(1, cos.size());
        assertEquals("1234_goober.json", cos.get(0).name);
        assertEquals("foobar", cos.get(0).volume);
        
    }

    @Test
    public void testFailAddObject() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());
        
        try {
            sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", null);
            fail("addObject() did not throw exception for non-existant volume request");
        } catch (InventoryException ex) { }

    }

    private <T extends Object> void assertIn(T el, T[] ary) {
        int i=0;
        for(i=0; i < ary.length; i++) {
            if (el.equals(ary[i])) return;
        }
        StringBuilder msg = new StringBuilder("\"");
        msg.append(el.toString()).append('"').append(" is not in {");
        for(i=0; i < ary.length && i < 3; i++) {
            msg.append('"').append(ary[i].toString()).append('"');
            if (i < ary.length-1) msg.append(", ");
        }
        if (i < ary.length) msg.append("...");
        msg.append("}");
        fail(msg.toString());
    }
            

    @Test
    public void testRegisterAlgorithm() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("sha256");
        sidb.registerAlgorithm("md5");

        assertEquals(1, sidb.do_getAlgorithmID("sha256"));
        assertEquals(2, sidb.do_getAlgorithmID("md5"));

        sidb.registerAlgorithm("sha256");
        assertEquals(1, sidb.do_getAlgorithmID("sha256"));
        assertEquals(2, sidb.do_getAlgorithmID("md5"));

        String[] algs = sidb.checksumAlgorithms();
        assertEquals("sha256", algs[0]);
        assertEquals("md5", algs[1]);
    }
}

