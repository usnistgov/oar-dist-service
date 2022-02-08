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

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.inventory.JDBCStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;

import java.io.IOException;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;

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
        assertEquals(0, sidb.volumes().size());
        assertEquals(0, sidb.checksumAlgorithms().size());
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
        assertArrayEquals(aneed, sidb.volumes().toArray());
        JSONObject md = sidb.getVolumeInfo("fundrum");
        assertEquals(150000, md.getInt("capacity"));
        assertEquals(null, md.opt("priority"));

        md.put("priority", 5);
        md.put("color", "red");
        sidb.registerVolume("fundrum", 200000, md);
        assertArrayEquals(aneed, sidb.volumes().toArray());
        md = sidb.getVolumeInfo("fundrum");
        assertEquals(200000, md.getInt("capacity"));
        assertEquals(5, md.getInt("priority"));

        sidb.registerVolume("foobar", 450000, md);
        Collection<String> got = sidb.volumes();
        assertTrue("foobar not included in volumes", got.contains("foobar"));
        assertTrue("fundrum not included in volumes", got.contains("foobar"));
        assertEquals(2, got.size());
        md = sidb.getVolumeInfo("fundrum");
        assertEquals(200000, md.getInt("capacity"));
        assertEquals(5, md.getInt("priority"));
        md = sidb.getVolumeInfo("foobar");
        assertEquals(450000, md.getInt("capacity"));
        assertEquals(5, md.getInt("priority"));
    }

    @Test
    public void testGetSetVolumeStatus() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerVolume("fundrum", 150000, null);
        assertEquals(sidb.VOL_FOR_UPDATE, sidb.getVolumeStatus("fundrum"));

        sidb.setVolumeStatus("fundrum", 0);
        assertEquals(sidb.VOL_DISABLED, sidb.getVolumeStatus("fundrum"));

        sidb.setVolumeStatus("fundrum", 8);
        assertEquals(8, sidb.getVolumeStatus("fundrum"));

        try {
            sidb.getVolumeStatus("goober");
            fail("setVolumeStatus() Failed to detect unregistered volume");
        }
        catch (InventoryException ex) {
            assertTrue(ex.getMessage().contains("goober"));
            assertTrue(ex.getMessage().contains("not registered"));
        }

        try {
            sidb.setVolumeStatus("goober", 1);
            fail("setVolumeStatus() failed to detect unregistered volume");
        }
        catch (InventoryException ex) {
            assertTrue("Unexpected message: "+ex.getMessage(),
                       ex.getMessage().contains("goober"));
            assertTrue("Unexpected message: "+ex.getMessage(),
                       ex.getMessage().contains("registered"));
        }
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

        // add an initial object
        CacheObject cob = sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", null);
        assertEquals("1234_goober.json", cob.name);
        assertEquals(-1L, cob.getSize());
        assertEquals(4, cob.metadatumNames().size());
        assertTrue("size not in metadata properties",
                   cob.metadatumNames().contains("size"));
        assertTrue("priority not in metadata properties",
                   cob.metadatumNames().contains("priority"));
        assertTrue("since not in metadata properties",
                   cob.metadatumNames().contains("since"));
        assertTrue("sinceDate not in metadata properties",
                   cob.metadatumNames().contains("sinceDate"));
        long since = cob.getMetadatumLong("since", -1L);
        assertTrue("unexpected since value: "+Long.toString(since), since > 0);
        assertTrue("cached flag not set to True", cob.cached);
        
        List<CacheObject> cos = sidb.findObject("1234/goober.json", sidb.VOL_FOR_INFO);
        assertEquals(1, cos.size());
        assertEquals("1234_goober.json", cos.get(0).name);
        assertEquals(-1L, cos.get(0).getSize());
        assertEquals(5, cos.get(0).metadatumNames().size());
        assertTrue("size not in metadata properties",
                   cos.get(0).metadatumNames().contains("size"));
        assertTrue("priority not in metadata properties",
                   cos.get(0).metadatumNames().contains("priority"));
        assertTrue("checked not in metadata properties",
                   cos.get(0).metadatumNames().contains("checked"));
        assertTrue("since not in metadata properties",
                   cos.get(0).metadatumNames().contains("since"));
        assertTrue("sinceDate not in metadata properties",
                   cos.get(0).metadatumNames().contains("sinceDate"));
        since = cos.get(0).getMetadatumLong("since", -1L);
        assertTrue("unexpected since value: "+Long.toString(since), since > 0);
        assertTrue("cached flag not set to True", cos.get(0).cached);

        // add a 2nd object with the same ID, different volume
        sidb.addObject("1234/goober.json", "fundrum", "1234_goober_2.json", null);
        cos = sidb.findObject("1234/goober.json", sidb.VOL_FOR_INFO);
        assertEquals(2, cos.size());
        assertEquals(-1L, cos.get(0).getSize());
        assertEquals(5, cos.get(0).metadatumNames().size());
        assertEquals(-1L, cos.get(1).getSize());
        assertEquals(5, cos.get(1).metadatumNames().size());
        assertTrue("cached flag not set to True", cos.get(0).cached);
        assertTrue("cached flag not set to True", cos.get(1).cached);

        // use addObject() to update the metadata
        JSONObject md = new JSONObject();
        md.put("priority", 4);
        md.put("size", 456L);
        md.put("checksum", "abcdef123456");
        md.put("checksumAlgorithm", "md5");
        md.put("color", "red");
        cob = sidb.addObject("1234/goober.json", "fundrum", "1234_goober_2.json", md);
        assertTrue("cached flag not set to True", cob.cached);
        assertEquals(456L, cob.getSize());
        assertEquals(7, cob.metadatumNames().size());
        assertTrue("size not in metadata properties",
                   cob.metadatumNames().contains("size"));
        assertTrue("priority not in metadata properties",
                   cob.metadatumNames().contains("priority"));
        assertTrue("since not in metadata properties",
                   cob.metadatumNames().contains("since"));
        assertTrue("sinceDate not in metadata properties",
                   cob.metadatumNames().contains("sinceDate"));
        assertTrue("color not in metadata properties",
                   cob.metadatumNames().contains("color"));
        assertTrue("checksum not in metadata properties",
                   cob.metadatumNames().contains("checksum"));
        assertTrue("checksumAlgorithm not in metadata properties",
                   cob.metadatumNames().contains("checksumAlgorithm"));
        assertEquals("md5", cob.getMetadatumString("checksumAlgorithm", null));
        assertEquals(4, cob.getMetadatumInt("priority", 0));
        assertEquals(456L, cob.getMetadatumLong("size", -1L));
        
        cos = sidb.findObject("1234/goober.json", sidb.VOL_FOR_INFO);
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
        assertEquals(8, second.metadatumNames().size());
        assertTrue("size not in metadata properties",
                   second.metadatumNames().contains("size"));
        assertTrue("priority not in metadata properties",
                   second.metadatumNames().contains("priority"));
        assertTrue("checked not in metadata properties",
                   second.metadatumNames().contains("checked"));
        assertTrue("since not in metadata properties",
                   second.metadatumNames().contains("since"));
        assertTrue("sinceDate not in metadata properties",
                   second.metadatumNames().contains("sinceDate"));
        assertTrue("color not in metadata properties",
                   second.metadatumNames().contains("color"));
        assertTrue("checksum not in metadata properties",
                   second.metadatumNames().contains("checksum"));
        assertTrue("checksumAlgorithm not in metadata properties",
                   second.metadatumNames().contains("checksumAlgorithm"));
        assertEquals("md5", second.getMetadatumString("checksumAlgorithm", null));
        assertEquals(-1L, first.getSize());
        assertEquals(5, first.metadatumNames().size());
        assertTrue("cached flag not set to True", first.cached);
        assertTrue("cached flag not set to True", second.cached);

        // add a third object, new ID
        md.put("size", 3196429990L);
        sidb.addObject("gurn.fits", "foobar", "a9ej_gurn.fits", md);
        cos = sidb.findObject("gurn.fits");
        assertEquals(1, cos.size());
        assertEquals("a9ej_gurn.fits", cos.get(0).name);
        assertEquals("foobar", cos.get(0).volname);
        assertEquals(3196429990L, cos.get(0).getSize());
        assertEquals(4, cos.get(0).getMetadatumInt("priority", 10));
        assertTrue("unexpected since value: "+Long.toString(cos.get(0).getMetadatumLong("since", -1L))+
                   "!>"+Long.toString(since),
                   cos.get(0).getMetadatumLong("since", -1L) > since);
        assertTrue("cached flag not set to false", cos.get(0).cached);

        // remove from wrong volume
        sidb.removeObject("fundrum", "a9ej_gurn.fits");
        cos = sidb.findObject("gurn.fits");
        assertEquals(1, cos.size());
        assertTrue("cached flag not set to true", cos.get(0).cached);

        // remove from right volume
        sidb.removeObject("foobar", "a9ej_gurn.fits");
        cos = sidb.findObject("gurn.fits");
        assertEquals(0, cos.size());

        // record should still be there with cached=0
        cos = sidb.findObject("gurn.fits", sidb.VOL_FOR_INFO);
        assertEquals(1, cos.size());
        assertTrue("cached flag not set to false", ! cos.get(0).cached);
        sidb.removeObject("foobar", "a9ej_gurn.fits", true);
        cos = sidb.findObject("gurn.fits", sidb.VOL_FOR_INFO);
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

        CacheObject co = sidb.findObject("fundrum", "1234_goober.json");
        assertEquals("1234_goober.json", co.name);
        assertEquals("fundrum", co.volname);
        
        co = sidb.findObject("foobar", "1234_goober.json");
        assertEquals("1234_goober.json", co.name);
        assertEquals("foobar", co.volname);
        
        co = sidb.findObject("foobar", "nope.json");
        assertNull(co);
    }

    @Test
    public void testSelectObjectsFromPurpose() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        JSONObject md = new JSONObject();
        md.put("priority", 4);
        md.put("size", 456L);

        sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", md);
        sidb.addObject("1234/goober.json", "fundrum", "1234_goober.json", md);

        md.put("priority", 0);
        md.put("size", 910000L);
        sidb.addObject("9999/barry.json", "foobar", "9999_barry.json", md);
        md.put("priority", 9);
        md.put("size", 910000L);
        sidb.addObject("0000/hank.json", "foobar", "0000_hank.json", md);
        md.put("priority", 1);
        md.put("size", 50000L);
        sidb.addObject("9999/jerry.json", "foobar", "9999_jerry.json", md);

        List<CacheObject> cos = sidb.selectObjectsFrom("foobar", "deletion_d", Integer.MAX_VALUE);
        assertEquals(3, cos.size());
        // order should be the order they were put in.  should not include priority=0 or items from fundrum
        assertEquals(cos.get(0).name, "1234_goober.json");
        assertEquals(cos.get(0).volname, "foobar");
        assertEquals(cos.get(1).name, "0000_hank.json");
        assertEquals(cos.get(2).name, "9999_jerry.json");

        cos = sidb.selectObjectsFrom("foobar", "deletion_p", 5000);
        assertEquals(3, cos.size());
        // order should be by priority
        assertEquals(cos.get(0).volname, "foobar");
        assertEquals(cos.get(0).name, "0000_hank.json");
        assertEquals(cos.get(1).name, "1234_goober.json");
        assertEquals(cos.get(2).name, "9999_jerry.json");
    }

    @Test
    public void testSelectObjectsForPurpose() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        JSONObject md = new JSONObject();
        md.put("priority", 4);
        md.put("size", 456L);

        sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", md);
        sidb.addObject("1234/goober.json", "fundrum", "1234_goober.json", md);

        md.put("priority", 0);
        md.put("size", 910000L);
        sidb.addObject("9999/barry.json", "foobar", "9999_barry.json", md);
        md.put("priority", 9);
        md.put("size", 910000L);
        sidb.addObject("0000/hank.json", "foobar", "0000_hank.json", md);
        md.put("priority", 1);
        md.put("size", 50000L);
        sidb.addObject("9999/jerry.json", "foobar", "9999_jerry.json", md);

        long now = System.currentTimeMillis();
        sidb.updateCheckedTime("foobar", "0000_hank.json", now);
        sidb.updateCheckedTime("fundrum", "1234_goober.json", now);

        List<CacheObject> cos = sidb.selectObjects("check", Integer.MAX_VALUE);
        assertEquals(3, cos.size());
        HashSet<String> needschecking = new HashSet<String>(3);
        needschecking.add("foobar:1234_goober.json");
        needschecking.add("foobar:9999_barry.json");
        needschecking.add("foobar:9999_jerry.json");
        for (CacheObject co : cos)
            assertTrue(needschecking.contains(co.volname+":"+co.name));
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

        Collection<String> algs = sidb.checksumAlgorithms();
        assertTrue("Missing algorithm: sha256", algs.contains("sha256"));
        assertTrue("Missing algorithm: md5", algs.contains("md5"));
    }

    @Test
    public void testUpdateMetadata() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        JSONObject md = new JSONObject();
        md.put("size", 90L);
        md.put("color", "red");
        md.put("height", 72);
        md.put("priority", 1);

        // two entries with same id, name, and metadata in different volumes
        sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", md);
        sidb.addObject("1234/goober.json", "fundrum", "1234_goober.json", md);

        List<CacheObject> cos = sidb.findObject("1234/goober.json");
        assertEquals(2, cos.size());
        int fundrum = ("fundrum".equals(cos.get(0).volname)) ?
                         0 : (("fundrum".equals(cos.get(1).volname)) ? 1 : -1);
        int foobar = ("foobar".equals(cos.get(0).volname)) ?
                         0 : (("foobar".equals(cos.get(1).volname)) ? 1 : -1);
        
        assertEquals("1234_goober.json", cos.get(foobar).name);
        assertEquals(90L, cos.get(foobar).getSize());
        assertEquals(1, cos.get(foobar).getMetadatumInt("priority", -1));
        assertEquals(72, cos.get(foobar).getMetadatumInt("height", -1));
        assertEquals("red", cos.get(foobar).getMetadatumString("color", null));
        assertFalse(cos.get(foobar).hasMetadatum("job"));
        assertEquals("1234_goober.json", cos.get(fundrum).name);
        assertEquals(90L, cos.get(fundrum).getSize());
        assertEquals(1, cos.get(fundrum).getMetadatumInt("priority", -1));
        assertEquals(72, cos.get(fundrum).getMetadatumInt("height", -1));
        assertEquals("red", cos.get(fundrum).getMetadatumString("color", null));
        assertFalse(cos.get(fundrum).hasMetadatum("job"));
        
        md = new JSONObject();
        md.put("size", 45L);
        md.put("color", "blue");
        md.put("height", 70);
        md.put("job", "retired");

        // now update one of them
        sidb.updateMetadata("foobar", "1234_goober.json", md);
        cos = sidb.findObject("1234/goober.json");
        assertEquals(2, cos.size());
        fundrum = ("fundrum".equals(cos.get(0).volname)) ?
                         0 : (("fundrum".equals(cos.get(1).volname)) ? 1 : -1);
        foobar = ("foobar".equals(cos.get(0).volname)) ?
                         0 : (("foobar".equals(cos.get(1).volname)) ? 1 : -1);
        assertNotEquals(fundrum, foobar);

        // foobar has been updated...
        assertEquals("1234_goober.json", cos.get(foobar).name);
        assertEquals(45L, cos.get(foobar).getSize());
        assertEquals(1, cos.get(foobar).getMetadatumInt("priority", -1));
        assertEquals(70, cos.get(foobar).getMetadatumInt("height", -1));
        assertEquals("blue", cos.get(foobar).getMetadatumString("color", null));
        assertEquals("retired", cos.get(foobar).getMetadatumString("job", null));

        // fundrum has not
        assertEquals("1234_goober.json", cos.get(fundrum).name);
        assertEquals(90L, cos.get(fundrum).getSize());
        assertEquals(1, cos.get(fundrum).getMetadatumInt("priority", -1));
        assertEquals(72, cos.get(fundrum).getMetadatumInt("height", -1));
        assertEquals("red", cos.get(fundrum).getMetadatumString("color", null));
        assertFalse(cos.get(fundrum).hasMetadatum("job"));
    }

    @Test
    public void testUpdateAccessTime() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        JSONObject md = new JSONObject();
        md.put("size", 90L);
        md.put("color", "red");
        md.put("height", 72);
        md.put("priority", 1);

        // two entries with same id, name, and metadata in different volumes
        sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", md);
        sidb.addObject("1234/goober.json", "fundrum", "1234_goober.json", md);

        List<CacheObject> cos = sidb.findObject("1234/goober.json");
        assertEquals(2, cos.size());
        int fundrum = ("fundrum".equals(cos.get(0).volname)) ?
                         0 : (("fundrum".equals(cos.get(1).volname)) ? 1 : -1);
        int foobar = ("foobar".equals(cos.get(0).volname)) ?
                         0 : (("foobar".equals(cos.get(1).volname)) ? 1 : -1);
        
        assertEquals("1234_goober.json", cos.get(foobar).name);
        assertEquals(90L, cos.get(foobar).getSize());
        assertEquals(1, cos.get(foobar).getMetadatumInt("priority", -1));
        assertEquals(72, cos.get(foobar).getMetadatumInt("height", -1));
        assertEquals("red", cos.get(foobar).getMetadatumString("color", null));
        assertFalse(cos.get(foobar).hasMetadatum("job"));
        assertEquals("1234_goober.json", cos.get(fundrum).name);
        assertEquals(90L, cos.get(fundrum).getSize());
        assertEquals(1, cos.get(fundrum).getMetadatumInt("priority", -1));
        assertEquals(72, cos.get(fundrum).getMetadatumInt("height", -1));
        assertEquals("red", cos.get(fundrum).getMetadatumString("color", null));
        assertFalse(cos.get(fundrum).hasMetadatum("job"));

        JSONObject fbmd = cos.get(foobar).exportMetadata();
        JSONObject fdmd = cos.get(fundrum).exportMetadata();

        sidb.updateAccessTime("foobar", "1234_goober.json");

        cos = sidb.findObject("1234/goober.json");
        assertEquals(2, cos.size());
        fundrum = ("fundrum".equals(cos.get(0).volname)) ?
                         0 : (("fundrum".equals(cos.get(1).volname)) ? 1 : -1);
        foobar = ("foobar".equals(cos.get(0).volname)) ?
                         0 : (("foobar".equals(cos.get(1).volname)) ? 1 : -1);

        // foobar updated
        assertTrue("access time not updated",
                   cos.get(foobar).getMetadatumLong("since", -1) > fbmd.getLong("since"));
        assertNotEquals("access date not updated", fbmd.getString("sinceDate"), 
                        cos.get(foobar).getMetadatumString("sinceDate", fbmd.getString("sinceDate")));

        // fundrum not updated
        assertEquals(fdmd.getLong("since"), cos.get(fundrum).getMetadatumLong("since", -2L));
        assertEquals(fdmd.getString("sinceDate"), cos.get(fundrum).getMetadatumString("sinceDate", "g"));

        // nothing else has changed
        assertEquals("1234_goober.json", cos.get(foobar).name);
        assertEquals(90L, cos.get(foobar).getSize());
        assertEquals(1, cos.get(foobar).getMetadatumInt("priority", -1));
        assertEquals(72, cos.get(foobar).getMetadatumInt("height", -1));
        assertEquals("red", cos.get(foobar).getMetadatumString("color", null));
        assertFalse(cos.get(foobar).hasMetadatum("job"));
        assertEquals("1234_goober.json", cos.get(fundrum).name);
        assertEquals(90L, cos.get(fundrum).getSize());
        assertEquals(1, cos.get(fundrum).getMetadatumInt("priority", -1));
        assertEquals(72, cos.get(fundrum).getMetadatumInt("height", -1));
        assertEquals("red", cos.get(fundrum).getMetadatumString("color", null));
        assertFalse(cos.get(fundrum).hasMetadatum("job"));
    }

    @Test
    public void testUpdateCheckedTime() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        JSONObject md = new JSONObject();
        md.put("size", 90L);
        md.put("color", "red");
        md.put("height", 72);
        md.put("priority", 1);

        // two entries with same id, name, and metadata in different volumes
        sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", md);
        sidb.addObject("1234/goober.json", "fundrum", "1234_goober.json", md);

        List<CacheObject> cos = sidb.findObject("1234/goober.json");
        assertEquals(2, cos.size());
        int fundrum = ("fundrum".equals(cos.get(0).volname)) ?
                         0 : (("fundrum".equals(cos.get(1).volname)) ? 1 : -1);
        int foobar = ("foobar".equals(cos.get(0).volname)) ?
                         0 : (("foobar".equals(cos.get(1).volname)) ? 1 : -1);
        
        assertEquals("1234_goober.json", cos.get(foobar).name);
        assertEquals(90L, cos.get(foobar).getSize());
        assertEquals(1, cos.get(foobar).getMetadatumInt("priority", -1));
        assertEquals(72, cos.get(foobar).getMetadatumInt("height", -1));
        assertEquals("red", cos.get(foobar).getMetadatumString("color", null));
        assertEquals(0L, cos.get(foobar).getMetadatumLong("checked", -1L));
        assertFalse(cos.get(foobar).hasMetadatum("job"));
        assertEquals("1234_goober.json", cos.get(fundrum).name);
        assertEquals(90L, cos.get(fundrum).getSize());
        assertEquals(1, cos.get(fundrum).getMetadatumInt("priority", -1));
        assertEquals(72, cos.get(fundrum).getMetadatumInt("height", -1));
        assertEquals("red", cos.get(fundrum).getMetadatumString("color", null));
        assertEquals(0L, cos.get(foobar).getMetadatumLong("checked", -1L));
        assertFalse(cos.get(fundrum).hasMetadatum("job"));

        JSONObject fbmd = cos.get(foobar).exportMetadata();
        JSONObject fdmd = cos.get(fundrum).exportMetadata();

        sidb.updateCheckedTime("foobar", "1234_goober.json", 50*31415927);

        cos = sidb.findObject("1234/goober.json");
        assertEquals(2, cos.size());
        fundrum = ("fundrum".equals(cos.get(0).volname)) ?
                         0 : (("fundrum".equals(cos.get(1).volname)) ? 1 : -1);
        foobar = ("foobar".equals(cos.get(0).volname)) ?
                         0 : (("foobar".equals(cos.get(1).volname)) ? 1 : -1);

        // foobar updated
        assertEquals(50*31415927, cos.get(foobar).getMetadatumLong("checked", -1));
        // fundrum not updated
        assertEquals(0L, cos.get(fundrum).getMetadatumLong("checked", -1));
    }

    @Test
    public void testGetEmptySpace() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 250000, null);

        Map<String, Long> sp = sidb.getAvailableSpace();
        assertEquals(2, sp.size());
        assertEquals(450000L, sp.get("foobar").longValue());
        assertEquals(250000L, sp.get("fundrum").longValue());

        sp = sidb.getUsedSpace();
        assertEquals(2, sp.size());
        assertEquals(0L, sp.get("foobar").longValue());
        assertEquals(0L, sp.get("fundrum").longValue());
    }

    @Test
    public void testGetSpace() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);

        String nm = null;
        JSONObject md = null;
        int[] sizes = { 229, 9321, 44001, 100311, 953, 2230, 100 };
        long total = 0L;
        for (int i=0; i < sizes.length; i++) {
            nm = Integer.toString(i);
            md = new JSONObject();
            md.put("size", sizes[i]);
            sidb.addObject("file" + nm, "foobar", nm, md);
            total += sizes[i];
        }

        Map<String, Long> used = sidb.getUsedSpace();
        assertEquals(total, used.get("foobar").longValue());
        assertEquals(used.size(), 1);

        used = sidb.getAvailableSpace();
        assertEquals(450000 - total, used.get("foobar").longValue());
        assertEquals(used.size(), 1);

        assertEquals(450000 - total, sidb.getAvailableSpaceIn("foobar"));

        try {
            sidb.getAvailableSpaceIn("goober!");
            fail("Expected an InventoryException when asking for space in unknown volume");
        } catch (InventoryException ex) {
            assertTrue("Unexpected InventoryException message",
                       ex.getMessage().contains("not registered"));
        }

        sidb.registerVolume("fundrum", 250000, null);
        for (int i=0; i < sizes.length; i++) {
            nm = Integer.toString(i);
            md = new JSONObject();
            md.put("size", sizes[i]+100);
            sidb.addObject("file" + nm, "fundrum", nm, md);
        }

        // make sure foobar is unaffected
        used = sidb.getUsedSpace();
        assertEquals(total, used.get("foobar").longValue());
        assertEquals(used.size(), 2);

        // now check fundrum
        used = sidb.getUsedSpace();
        assertEquals(total + sizes.length*100, used.get("fundrum").longValue());

        used = sidb.getAvailableSpace();
        assertEquals(450000 - total, used.get("foobar").longValue());
        assertEquals(used.size(), 2);

        assertEquals(250000 - total - sizes.length*100, used.get("fundrum").longValue());
    }
}

