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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.VolumeStatus;

public class SQLiteStorageInventoryDBTest {

    @TempDir
    public File tempDir;

    class TestSQLiteStorageInventoryDB extends SQLiteStorageInventoryDB {
        public TestSQLiteStorageInventoryDB(String fn) { super(fn); }
        public int do_getVolumeID(String name) throws InventoryException { return getVolumeID(name); }
        public int do_getAlgorithmID(String name) throws InventoryException {
            return getAlgorithmID(name);
        }
    }

    String createDB() throws IOException, InventoryException {
        File tf = new File(tempDir, "testdb.sqlite");
        String out = tf.getAbsolutePath();
        SQLiteStorageInventoryDB.initializeDB(out);
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
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbf.toString());
        DatabaseMetaData dmd = conn.getMetaData();

        List<String> svals = getStringColumn(dmd.getTableTypes(), 1);
        assertTrue(svals.contains("TABLE"));

        String[] ss = { "TABLE" };
        svals = getStringColumn(dmd.getTables(null, null, null, ss), "TABLE_NAME");
        assertTrue(svals.contains("volumes"));
        assertTrue(svals.contains("algorithms"));
        assertTrue(svals.contains("objects"));
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
        InventoryException ex = assertThrows(InventoryException.class, () -> sidb.getVolumeInfo("fundrum"));
        assertTrue(ex.getMessage().contains("fundrum"));

        sidb.registerVolume("fundrum", 150000, null);
        assertEquals(1, sidb.volumes().size());
        JSONObject md = sidb.getVolumeInfo("fundrum");
        assertEquals(150000, md.getInt("capacity"));

        md.put("priority", 5);
        sidb.registerVolume("fundrum", 200000, md);
        assertEquals(1, sidb.volumes().size());
        md = sidb.getVolumeInfo("fundrum");
        assertEquals(200000, md.getInt("capacity"));
        assertEquals(5, md.getInt("priority"));

        sidb.registerVolume("foobar", 450000, md);
        Collection<String> got = sidb.volumes();
        assertTrue(got.contains("foobar"));
        assertTrue(got.contains("fundrum"));
        assertEquals(2, got.size());
    }

    @Test
    public void testGetSetVolumeStatus() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());
        sidb.registerVolume("fundrum", 150000, null);
        assertEquals(VolumeStatus.VOL_FOR_UPDATE, sidb.getVolumeStatus("fundrum"));

        sidb.setVolumeStatus("fundrum", 0);
        assertEquals(VolumeStatus.VOL_DISABLED, sidb.getVolumeStatus("fundrum"));

        sidb.setVolumeStatus("fundrum", 8);
        assertEquals(8, sidb.getVolumeStatus("fundrum"));

        assertThrows(InventoryException.class, () -> sidb.getVolumeStatus("goober"));
        assertThrows(InventoryException.class, () -> sidb.setVolumeStatus("goober", 1));
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

        CacheObject cob = sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", null);
        assertEquals("1234_goober.json", cob.name);
        assertEquals(-1L, cob.getSize());
        assertEquals(4, cob.metadatumNames().size());
        assertTrue(cob.metadatumNames().contains("since"));
        assertTrue(cob.cached);

        List<CacheObject> cos = sidb.findObject("1234/goober.json", VolumeStatus.VOL_FOR_INFO);
        assertEquals(1, cos.size());

        sidb.addObject("1234/goober.json", "fundrum", "1234_goober_2.json", null);
        cos = sidb.findObject("1234/goober.json", VolumeStatus.VOL_FOR_INFO);
        assertEquals(2, cos.size());

        JSONObject md = new JSONObject();
        md.put("priority", 4);
        md.put("size", 456L);
        md.put("checksum", "abcdef123456");
        md.put("checksumAlgorithm", "md5");
        cob = sidb.addObject("1234/goober.json", "fundrum", "1234_goober_2.json", md);
        assertTrue(cob.cached);
        assertEquals(456L, cob.getSize());

        cos = sidb.findObject("1234/goober.json", VolumeStatus.VOL_FOR_INFO);
        assertEquals(2, cos.size());
        CacheObject first = cos.stream().filter(c -> "1234_goober.json".equals(c.name)).findFirst().orElse(null);
        CacheObject second = cos.stream().filter(c -> "1234_goober_2.json".equals(c.name)).findFirst().orElse(null);
        assertNotNull(first);
        assertNotNull(second);

        md.put("size", 3196429990L);
        sidb.addObject("gurn.fits", "foobar", "a9ej_gurn.fits", md);
        cos = sidb.findObject("gurn.fits");
        assertEquals(1, cos.size());
        assertEquals("a9ej_gurn.fits", cos.get(0).name);
        assertEquals(3196429990L, cos.get(0).getSize());

        sidb.removeObject("fundrum", "a9ej_gurn.fits");
        cos = sidb.findObject("gurn.fits");
        assertEquals(1, cos.size());

        sidb.removeObject("foobar", "a9ej_gurn.fits");
        cos = sidb.findObject("gurn.fits");
        assertEquals(0, cos.size());
    }

    @Test
    public void testFailAddObject() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        TestSQLiteStorageInventoryDB sidb = new TestSQLiteStorageInventoryDB(dbf.getPath());

        InventoryException ex = assertThrows(InventoryException.class, () -> sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", null));
        assertTrue(ex.getMessage().contains("foobar"));
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
        md.put("priority", 1);
        sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", md);
        sidb.addObject("1234/goober.json", "fundrum", "1234_goober.json", md);

        sidb.updateMetadata("foobar", "1234_goober.json", new JSONObject().put("size", 45L).put("color", "blue").put("priority", 4));
        List<CacheObject> cos = sidb.findObject("1234/goober.json");

        CacheObject updated = cos.stream().filter(c -> "foobar".equals(c.volname)).findFirst().orElse(null);
        assertNotNull(updated);
        assertEquals(45L, updated.getSize());
        assertEquals("blue", updated.getMetadatumString("color", null));

        CacheObject notUpdated = cos.stream().filter(c -> "fundrum".equals(c.volname)).findFirst().orElse(null);
        assertNotNull(notUpdated);
        assertEquals(90L, notUpdated.getSize());
        assertEquals("red", notUpdated.getMetadatumString("color", null));
    }
}
