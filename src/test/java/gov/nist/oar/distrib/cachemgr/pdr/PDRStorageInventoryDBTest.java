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
package gov.nist.oar.distrib.cachemgr.pdr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.VolumeNotFoundException;
import gov.nist.oar.distrib.cachemgr.VolumeStatus;

public class PDRStorageInventoryDBTest {

    @TempDir
    File tempDir;

    class TestPDRStorageInventoryDB extends PDRStorageInventoryDB {
        protected String dbfile = null;
        public TestPDRStorageInventoryDB(String filepath) {
            super((filepath.startsWith("jdbc:sqlite:")) ? filepath : "jdbc:sqlite:" + filepath);
            dbfile = (filepath.startsWith("jdbc:sqlite:")) ? filepath.substring("jdbc:sqlite:".length()) : filepath;
        }
        public int do_getVolumeID(String name) throws InventoryException {
            return getVolumeID(name);
        }
        public int do_getAlgorithmID(String name) throws InventoryException {
            return getAlgorithmID(name);
        }
    }

    String createDB() throws IOException, InventoryException {
        File tf = new File(tempDir, "testdb.sqlite");
        String out = tf.getAbsolutePath();
        PDRStorageInventoryDB.initializeSQLiteDB(out);
        return out;
    }

    List<String> getStringColumn(ResultSet rs, int colindex) throws SQLException {
        ArrayList<String> out = new ArrayList<>();
        while (rs.next()) {
            out.add(rs.getString(colindex));
        }
        return out;
    }

    List<String> getStringColumn(ResultSet rs, String colname) throws SQLException {
        ArrayList<String> out = new ArrayList<>();
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
        assertTrue(svals.contains("volumes"), "Missing volumes table");
        assertTrue(svals.contains("algorithms"), "Missing algorithms table");
        assertTrue(svals.contains("objects"), "Missing objects table");
    }

    @Test
    public void testCtor() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
        assertEquals(0, sidb.volumes().size());
        assertEquals(0, sidb.checksumAlgorithms().size());
    }

    @Test
    public void testRegisterVolume() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
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
        assertTrue(got.contains("foobar"),"foobar not included in volumes");
        assertTrue(got.contains("foobar"), "fundrum not included in volumes");
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

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
        sidb.registerVolume("fundrum", 150000, null);
        assertEquals(VolumeStatus.VOL_FOR_UPDATE, sidb.getVolumeStatus("fundrum"));

        sidb.setVolumeStatus("fundrum", 0);
        assertEquals(VolumeStatus.VOL_DISABLED, sidb.getVolumeStatus("fundrum"));

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
            assertTrue(ex.getMessage().contains("goober"), "Unexpected message: "+ex.getMessage());
            assertTrue(ex.getMessage().contains("registered"), "Unexpected message: "+ex.getMessage());
        }
    }

    @Test
    public void testAddObject() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        CacheObject cob = sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", null);
        assertEquals("1234_goober.json", cob.name);
        assertEquals(-1L, cob.getSize());
        assertEquals(4, cob.metadatumNames().size());
        assertTrue(cob.metadatumNames().contains("size"), "size not in metadata properties");
        assertTrue(cob.metadatumNames().contains("priority"), "priority not in metadata properties");
        assertTrue(cob.metadatumNames().contains("since"),"since not in metadata properties");
        assertTrue(cob.metadatumNames().contains("sinceDate"), "sinceDate not in metadata properties");
        long since = cob.getMetadatumLong("since", -1L);
        assertTrue(since > 0, "unexpected since value: "+Long.toString(since));
        
        List<CacheObject> cos = sidb.findObject("1234/goober.json");
        assertEquals(1, cos.size());
        assertEquals("1234_goober.json", cos.get(0).name);
        assertEquals(-1L, cos.get(0).getSize());
        assertEquals(5, cos.get(0).metadatumNames().size());
        assertTrue(cos.get(0).metadatumNames().contains("size"), 
            "size not in metadata properties");
        assertTrue(cos.get(0).metadatumNames().contains("priority"), 
            "priority not in metadata properties");
        assertTrue(cos.get(0).metadatumNames().contains("checked"), 
            "checked not in metadata properties");
        assertTrue(cos.get(0).metadatumNames().contains("since"), 
            "since not in metadata properties");
        assertTrue(cos.get(0).metadatumNames().contains("sinceDate"), 
            "sinceDate not in metadata properties");
        since = cos.get(0).getMetadatumLong("since", -1L);
        assertTrue(since > 0, "unexpected since value: " + Long.toString(since));


        sidb.addObject("1234/goober.json", "fundrum", "1234_goober_2.json", null);
        cos = sidb.findObject("1234/goober.json");
        assertEquals(2, cos.size());
        assertEquals(-1L, cos.get(0).getSize());
        assertEquals(5, cos.get(0).metadatumNames().size());
        assertEquals(-1L, cos.get(1).getSize());
        assertEquals(5, cos.get(1).metadatumNames().size());

        JSONObject md = new JSONObject();
        md.put("priority", 4);
        md.put("size", 456L);
        md.put("checksum", "abcdef123456");
        md.put("checksumAlgorithm", "md5");
        md.put("color", "red");
        md.put("pdrid", "ark:/88888/gomer");
        md.put("ediid", "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
        cob = sidb.addObject("1234/goober.json", "fundrum", "1234_goober_2.json", md);
        assertEquals(456L, cob.getSize());
        assertEquals(9, cob.metadatumNames().size());
        assertTrue(cob.metadatumNames().contains("size"), 
           "size not in metadata properties");
        assertTrue(cob.metadatumNames().contains("priority"), 
                "priority not in metadata properties");
        assertTrue(cob.metadatumNames().contains("since"), 
                "since not in metadata properties");
        assertTrue(cob.metadatumNames().contains("sinceDate"), 
                "sinceDate not in metadata properties");
        assertTrue(cob.metadatumNames().contains("color"), 
                "color not in metadata properties");
        assertTrue(cob.metadatumNames().contains("checksum"), 
                "checksum not in metadata properties");
        assertTrue(cob.metadatumNames().contains("checksumAlgorithm"), 
                "checksumAlgorithm not in metadata properties");
        assertTrue(cob.metadatumNames().contains("pdrid"), 
                "pdrid not in metadata properties");
        assertTrue(cob.metadatumNames().contains("ediid"), 
                "ediid not in metadata properties");

        assertEquals("md5", cob.getMetadatumString("checksumAlgorithm", null));
        assertEquals(4, cob.getMetadatumInt("priority", 0));
        assertEquals(456L, cob.getMetadatumLong("size", -1L));
        
        cos = sidb.findObject("1234/goober.json");
        assertEquals(2, cos.size());
        CacheObject first=null, second=null;
        for(CacheObject co : cos) {
            if (co.name.equals("1234_goober.json"))
                first = co;
            else if (co.name.equals("1234_goober_2.json"))
                second = co;
        }
        assertNotNull(first, "Failed to find first registered object");
        assertNotNull(second, "Failed to find 2nd (updated) registered object");
        assertEquals(456L, second.getSize());
        assertEquals(10, second.metadatumNames().size());
        assertTrue(second.metadatumNames().contains("size"), 
        "size not in metadata properties");
        assertTrue(second.metadatumNames().contains("priority"), 
                "priority not in metadata properties");
        assertTrue(second.metadatumNames().contains("checked"), 
                "checked not in metadata properties");
        assertTrue(second.metadatumNames().contains("since"), 
                "since not in metadata properties");
        assertTrue(second.metadatumNames().contains("sinceDate"), 
                "sinceDate not in metadata properties");
        assertTrue(second.metadatumNames().contains("color"), 
                "color not in metadata properties");
        assertTrue(second.metadatumNames().contains("checksum"), 
                "checksum not in metadata properties");
        assertTrue(second.metadatumNames().contains("checksumAlgorithm"), 
                "checksumAlgorithm not in metadata properties");
        assertEquals("md5", second.getMetadatumString("checksumAlgorithm", null));
        assertEquals(-1L, first.getSize());
        assertEquals(5, first.metadatumNames().size());

        md.put("size", 3196429990L);
        sidb.addObject("gurn.fits", "foobar", "a9ej_gurn.fits", md);
        cos = sidb.findObject("gurn.fits");
        assertEquals(1, cos.size());
        assertEquals("a9ej_gurn.fits", cos.get(0).name);
        assertEquals("foobar", cos.get(0).volname);
        assertEquals(3196429990L, cos.get(0).getSize());
        assertEquals(4, cos.get(0).getMetadatumInt("priority", 10));
        assertTrue(cos.get(0).getMetadatumLong("since", -1L) > since,
            "unexpected since value: "+Long.toString(cos.get(0).getMetadatumLong("since", -1L))+
            "!>"+Long.toString(since));

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

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
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

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        JSONObject md = new JSONObject();
        md.put("priority", 4);
        md.put("size", 456L);
        md.put("pdrid", "ark:/88888/gomer");
        md.put("ediid", "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");

        sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", md);
        sidb.addObject("1234/goober.json", "fundrum", "1234_goober.json", md);

        md.put("priority", 0);
        md.put("size", 910000L);
        md.put("pdrid", "ark:/88888/gomer");
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

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
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

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
        
        try {
            sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", null);
            fail("addObject() did not throw exception for non-existant volume request");
        } catch (InventoryException ex) { }

    }

    // private <T extends Object> void assertIn(T el, T[] ary) {
    //     int i=0;
    //     for(i=0; i < ary.length; i++) {
    //         if (el.equals(ary[i])) return;
    //     }
    //     StringBuilder msg = new StringBuilder("\"");
    //     msg.append(el.toString()).append('"').append(" is not in {");
    //     for(i=0; i < ary.length && i < 3; i++) {
    //         msg.append('"').append(ary[i].toString()).append('"');
    //         if (i < ary.length-1) msg.append(", ");
    //     }
    //     if (i < ary.length) msg.append("...");
    //     msg.append("}");
    //     fail(msg.toString());
    // }
            

    @Test
    public void testRegisterAlgorithm() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestPDRStorageInventoryDB sidb = new TestPDRStorageInventoryDB(dbf.getPath());
        sidb.registerAlgorithm("sha256");
        sidb.registerAlgorithm("md5");

        assertEquals(1, sidb.do_getAlgorithmID("sha256"));
        assertEquals(2, sidb.do_getAlgorithmID("md5"));

        sidb.registerAlgorithm("sha256");
        assertEquals(1, sidb.do_getAlgorithmID("sha256"));
        assertEquals(2, sidb.do_getAlgorithmID("md5"));

        Collection<String> algs = sidb.checksumAlgorithms();
        assertTrue(algs.contains("sha256"), "Missing algorithm: sha256");
        assertTrue(algs.contains("md5"), "Missing algorithm: md5");
    }

    @Test
    public void testUpdateMetadata() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        JSONObject md = new JSONObject();
        md.put("size", 90L);
        md.put("color", "red");
        md.put("height", 72);
        md.put("priority", 1);
        md.put("pdrid", "ark:/88888/gomer");
        md.put("ediid", "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");

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
        assertEquals("ark:/88888/gomer", cos.get(foobar).getMetadatumString("pdrid", null));
        assertEquals("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
                     cos.get(foobar).getMetadatumString("ediid", null));
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
        md.put("ediid", "YYZ");

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
        assertEquals("ark:/88888/gomer", cos.get(foobar).getMetadatumString("pdrid", null));
        assertEquals("YYZ", cos.get(foobar).getMetadatumString("ediid", null));

        // fundrum has not
        assertEquals("1234_goober.json", cos.get(fundrum).name);
        assertEquals(90L, cos.get(fundrum).getSize());
        assertEquals(1, cos.get(fundrum).getMetadatumInt("priority", -1));
        assertEquals(72, cos.get(fundrum).getMetadatumInt("height", -1));
        assertEquals("red", cos.get(fundrum).getMetadatumString("color", null));
        assertFalse(cos.get(fundrum).hasMetadatum("job"));
        assertEquals("ark:/88888/gomer", cos.get(foobar).getMetadatumString("pdrid", null));
        assertEquals("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
                     cos.get(fundrum).getMetadatumString("ediid", null));
    }

    @Test
    public void testUpdateAccessTime() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
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
        assertTrue(cos.get(foobar).getMetadatumLong("since", -1) > fbmd.getLong("since"), 
            "access time not updated");
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

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
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

        // JSONObject fbmd = cos.get(foobar).exportMetadata();
        // JSONObject fdmd = cos.get(fundrum).exportMetadata();

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
    public void testGetSpace() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
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
            assertTrue(ex.getMessage().contains("not registered"), "Unexpected InventoryException message");
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

    @Test
    public void testSelectObjectsByPDRID() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        JSONObject md = new JSONObject();
        md.put("priority", 4);
        md.put("size", 456L);
        md.put("pdrid", "ark:/88888/1234");
        md.put("ediid", "1234");

        sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", md);
        sidb.addObject("1234/goober.json", "fundrum", "1234_goober.json", md);

        md.put("priority", 0);
        md.put("size", 910000L);
        md.put("pdrid", "ark:/88888/9999");
        md.put("ediid", "ark:/88888/9999");
        sidb.addObject("9999/barry.json", "foobar", "9999_barry.json", md);
        md.put("priority", 1);
        md.put("size", 50000L);
        sidb.addObject("9999/jerry.json", "foobar", "9999_jerry.json", md);
        md.put("priority", 9);
        md.put("size", 910000L);
        md.put("pdrid", "ark:/88888/0000");
        sidb.addObject("0000/hank.json", "foobar", "0000_hank.json", md);

        List<CacheObject> cos = sidb.selectObjectsByPDRID("ark:/88888/1234", 0);
        assertEquals(2, cos.size());
        // order should be the order they were put in.  should not include priority=0 or items from fundrum
        assertEquals(cos.get(0).name, "1234_goober.json");
        assertEquals(cos.get(0).volname, "foobar");
        assertEquals(cos.get(1).name, "1234_goober.json");
        assertEquals(cos.get(1).volname, "fundrum");

        cos = sidb.selectObjectsByPDRID("ark:/88888/9999", 0);
        assertEquals(2, cos.size());
        // order should be the order they were put in.  should not include priority=0 or items from fundrum
        assertEquals(cos.get(0).name, "9999_barry.json");
        assertEquals(cos.get(0).volname, "foobar");
        assertEquals(cos.get(1).name, "9999_jerry.json");
        assertEquals(cos.get(1).volname, "foobar");

        cos = sidb.selectObjectsByPDRID("ark:/88888/0000", 0);
        assertEquals(1, cos.size());
        // order should be the order they were put in.  should not include priority=0 or items from fundrum
        assertEquals(cos.get(0).name, "0000_hank.json");
        assertEquals(cos.get(0).volname, "foobar");

        cos = sidb.selectObjectsByPDRID("1234", 0);
        assertEquals(0, cos.size());
    }

    @Test
    public void testSelectObjectsByEDIID() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        JSONObject md = new JSONObject();
        md.put("priority", 4);
        md.put("size", 456L);
        md.put("pdrid", "ark:/88888/1234");
        md.put("ediid", "1234");

        sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", md);
        sidb.addObject("1234/goober.json", "fundrum", "1234_goober.json", md);

        md.put("priority", 0);
        md.put("size", 910000L);
        md.put("pdrid", "ark:/88888/9999");
        md.put("ediid", "ark:/88888/9999");
        sidb.addObject("9999/barry.json", "foobar", "9999_barry.json", md);
        md.put("priority", 1);
        md.put("size", 50000L);
        sidb.addObject("9999/jerry.json", "foobar", "9999_jerry.json", md);
        md.put("priority", 9);
        md.put("size", 910000L);
        md.put("pdrid", "ark:/88888/0000");
        sidb.addObject("0000/hank.json", "foobar", "0000_hank.json", md);

        List<CacheObject> cos = sidb.selectObjectsByEDIID("1234", 0);
        assertEquals(2, cos.size());
        // order should be the order they were put in.  should not include priority=0 or items from fundrum
        assertEquals(cos.get(0).name, "1234_goober.json");
        assertEquals(cos.get(0).volname, "foobar");
        assertEquals(cos.get(1).name, "1234_goober.json");
        assertEquals(cos.get(1).volname, "fundrum");

        cos = sidb.selectObjectsByEDIID("ark:/88888/9999", 0);
        assertEquals(3, cos.size());
        // order should be the order they were put in.  should not include priority=0 or items from fundrum
        assertEquals(cos.get(0).name, "9999_barry.json");
        assertEquals(cos.get(0).volname, "foobar");
        assertEquals(cos.get(1).name, "9999_jerry.json");
        assertEquals(cos.get(1).volname, "foobar");
        assertEquals(cos.get(2).name, "0000_hank.json");
        assertEquals(cos.get(2).volname, "foobar");

        cos = sidb.selectObjectsByEDIID("ark:/88888/0000", 0);
        assertEquals(0, cos.size());
    }

    @Test
    public void testGetVolumeTotals() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        JSONObject md = sidb.getVolumeTotals("foobar");
        assertEquals(0L, md.getLong("totalsize"));
        assertEquals(0L, md.getLong("filecount"));
        assertEquals(0L, md.getLong("since"));
        assertEquals(0L, md.getLong("checked"));
        assertEquals("(never)", md.getString("checkedDate"));

        md = new JSONObject();
        md.put("priority", 4);
        md.put("size", 456L);
        md.put("pdrid", "ark:/88888/1234");
        md.put("ediid", "1234");

        sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", md);
        md.put("size", 544L);
        sidb.addObject("1234/gurn.json", "foobar", "1234_gurn.json", md);
        sidb.addObject("1234/goober.json", "fundrum", "1234_goober.json", md);
        sidb.updateCheckedTime("fundrum", "1234_goober.json", 1634179333874L);

        md = sidb.getVolumeTotals("foobar");
        assertEquals(1000L, md.getLong("totalsize"));
        assertEquals(2L, md.getLong("filecount"));
        assertTrue(0 < md.getLong("since"));
        assertTrue(0 < md.getString("sinceDate").length());
        assertEquals(0L, md.getLong("checked"));
        assertEquals("(never)", md.getString("checkedDate"));

        md = sidb.getVolumeTotals("fundrum");
        assertEquals(544L, md.getLong("totalsize"));
        assertEquals(1L, md.getLong("filecount"));
        assertTrue(0 < md.getLong("since"));
        assertTrue(0 < md.getString("sinceDate").length());
        assertEquals(1634179333874L, md.getLong("checked"));
        assertNotEquals("(never)", md.getString("checkedDate"));
        assertNotEquals("1970-01-01T00:00:00Z", md.getString("checkedDate"));

        try {
            md = sidb.getVolumeTotals("hank");
            fail("Found non-existent volume");
        }
        catch (VolumeNotFoundException ex) { /* success! */ }
    }

    @Test
    public void testSummarizeDataset() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        assertNull(sidb.summarizeDataset("1234"));

        JSONObject md = new JSONObject();
        md.put("priority", 4);
        md.put("size", 456L);
        md.put("pdrid", "ark:/88888/1234");
        md.put("ediid", "ark:/88888/abcd");

        sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", md);
        md.put("size", 544L);
        sidb.addObject("1234/gurn.json", "fundrum", "1234_gurn.json", md);
        md.put("pdrid", "ark:/88888/2345");
        md.put("ediid", "2345");
        sidb.addObject("2345/goober.json", "fundrum", "2345_goober.json", md);

        md = sidb.summarizeDataset("1234");
        assertEquals(1000L, md.getLong("totalsize"));
        assertEquals(2L, md.getLong("filecount"));
        assertTrue(0 < md.getLong("since"));
        assertTrue(0 < md.getString("sinceDate").length());
        assertEquals(0L, md.getLong("checked"));
        assertEquals("abcd", md.getString("aipid"));
        assertEquals("ark:/88888/abcd", md.getString("ediid"));
        assertEquals("ark:/88888/1234", md.getString("pdrid"));
        assertEquals("(never)", md.getString("checkedDate"));
        
        md = sidb.summarizeDataset("2345");
        assertEquals(544L, md.getLong("totalsize"));
        assertEquals(1L, md.getLong("filecount"));
        assertTrue(0 < md.getLong("since"));
        assertTrue(0 < md.getString("sinceDate").length());
        assertEquals(0L, md.getLong("checked"));
        assertEquals("(never)", md.getString("checkedDate"));
        assertEquals("2345", md.getString("aipid"));
        assertEquals("2345", md.getString("ediid"));
        assertEquals("ark:/88888/2345", md.getString("pdrid"));

    }

    @Test
    public void testSummarizeContents() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
        sidb.registerAlgorithm("md5");
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        JSONArray summs = sidb.summarizeContents(null);
        assertEquals(0, summs.length());

        JSONObject md = new JSONObject();
        md.put("priority", 4);
        md.put("size", 456L);
        md.put("pdrid", "ark:/88888/1234");
        md.put("ediid", "1234");

        sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", md);
        md.put("size", 544L);
        sidb.addObject("1234/gurn.json", "fundrum", "1234_gurn.json", md);
        md.put("pdrid", "ark:/88888/2345");
        md.put("ediid", "2345");
        sidb.addObject("2345/goober.json", "fundrum", "2345_goober.json", md);

        summs = sidb.summarizeContents(null);
        assertEquals(2, summs.length());
        HashMap<String,JSONObject> map = new HashMap<String,JSONObject>(2);
        for(int i=0; i < summs.length(); i++)  {
            JSONObject o = summs.getJSONObject(i);
            map.put(o.getString("aipid"), o);
        }

        assertTrue(map.containsKey("1234"), "Missing dataset: 1234");
        md = map.get("1234");
        assertEquals(1000L, md.getLong("totalsize"));
        assertEquals(2L, md.getLong("filecount"));
        assertTrue(0 < md.getLong("since"));
        assertTrue(0 < md.getString("sinceDate").length());
        assertEquals(0L, md.getLong("checked"));
        assertEquals("(never)", md.getString("checkedDate"));
        
        assertTrue(map.containsKey("2345"), "Missing dataset: 2345");
        md = map.get("2345");
        assertEquals(544L, md.getLong("totalsize"));
        assertEquals(1L, md.getLong("filecount"));
        assertTrue(0 < md.getLong("since"));
        assertTrue(0 < md.getString("sinceDate").length());
        assertEquals(0L, md.getLong("checked"));
        assertEquals("(never)", md.getString("checkedDate"));

        summs = sidb.summarizeContents("foobar");
        assertEquals(1, summs.length());
        md = summs.getJSONObject(0);
        assertEquals("1234", md.getString("aipid"));
        assertEquals(456, md.getLong("totalsize"));
        assertEquals(1L, md.getLong("filecount"));
        assertTrue(0 < md.getLong("since"));
        assertTrue(0 < md.getString("sinceDate").length());
        assertEquals(0L, md.getLong("checked"));
        assertEquals("(never)", md.getString("checkedDate"));

        summs = sidb.summarizeContents("fundrum");
        assertEquals(2, summs.length());
        map = new HashMap<String,JSONObject>(2);
        for(int i=0; i < summs.length(); i++)  {
            JSONObject o = summs.getJSONObject(i);
            map.put(o.getString("aipid"), o);
        }

        assertTrue(map.containsKey("1234"), "Missing dataset: 1234");
        md = map.get("1234");
        assertEquals(544L, md.getLong("totalsize"));
        assertEquals(1L, md.getLong("filecount"));
        assertTrue(0 < md.getLong("since"));
        assertTrue(0 < md.getString("sinceDate").length());
        assertEquals(0L, md.getLong("checked"));
        assertEquals("(never)", md.getString("checkedDate"));
        
        assertTrue(map.containsKey("2345"), "Missing dataset: 2345");
        md = map.get("2345");
        assertEquals(544L, md.getLong("totalsize"));
        assertEquals(1L, md.getLong("filecount"));
        assertTrue(0 < md.getLong("since"));
        assertTrue(0 < md.getString("sinceDate").length());
        assertEquals(0L, md.getLong("checked"));
        assertEquals("(never)", md.getString("checkedDate"));
    }
}

