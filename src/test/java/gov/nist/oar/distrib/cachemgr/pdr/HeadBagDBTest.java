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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.InventoryException;

/**
 * This test also tests the PDRStorageInventoryDB implementation
 */
public class HeadBagDBTest {

    @TempDir
    public Path tempDir;

    String createDB() throws IOException, InventoryException {
        File tf = tempDir.resolve("testdb.sqlite").toFile();
        String out = tf.getAbsolutePath();
        HeadBagDB.initializeSQLiteDB(out);
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

        // Check that we have tables defined
        List<String> svals = getStringColumn(dmd.getTableTypes(), 1);
        assertTrue(svals.contains("TABLE"));

        // Check that our tables are defined
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

        PDRStorageInventoryDB sidb = HeadBagDB.createSQLiteDB(dbf.getPath());
        assertEquals(0, sidb.volumes().size());
        assertEquals(0, sidb.checksumAlgorithms().size());
        assertTrue(sidb instanceof HeadBagDB);
    }

    public void loadDB(HeadBagDB sidb) throws InventoryException {
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);

        JSONObject md = new JSONObject();
        md.put("size", 456L);
        md.put("pdrid", "ark:/88888/1234");
        md.put("ediid", "1234");

        sidb.addObject("1234.1_0_0.mbag0_4-3", "foobar", "1234.1_0_0.mbag0_4-3.zip", md);
        sidb.addObject("1234.1_0_0.mbag0_4-3", "fundrum", "1234.1_0_0.mbag0_4-3.zip", md);
        sidb.addObject("1234.1_0_1.mbag0_4-4", "foobar", "1234.1_0_1.mbag0_4-4.zip", md);
        sidb.addObject("1234.1_23_0.mbag0_4-85", "foobar", "1234.1_23_0.mbag0_4-85.zip", md);
        sidb.addObject("1234.zip", "fundrum", "1234.zip", md);

        md.put("pdrid", "ark:/88888/mds2-9999");
        md.put("ediid", "ark:/88888/mds2-9999");
        sidb.addObject("mds2-9999.1_0_0.mbag0_4-0", "foobar", "mds2-9999.1_0_0.mbag0_4-0.zip", md);
        sidb.addObject("mds2-9999.1_1_0.mbag0_4-8", "foobar", "mds2-9999.1_1_0.mbag0_4-8.zip", md);

        md.put("pdrid", "ark:/88888/mds2-0000");
        md.put("ediid", "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX1004");
        sidb.addObject("mds2-9999.zip", "foobar", "mds2-9999.zip", md);
    }

    @Test
    public void testFindHeadBag() throws InventoryException, IOException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());

        HeadBagDB sidb = HeadBagDB.createHeadBagDB(dbf.getPath());
        loadDB(sidb);

        List<CacheObject> cos = sidb.findHeadBag("1234", "1.0.0", 0);
        assertEquals(2, cos.size());
        assertEquals("1234.1_0_0.mbag0_4-3.zip", cos.get(0).name);
        assertEquals("1234.1_0_0.mbag0_4-3", cos.get(0).id);
        assertEquals("1234.1_0_0.mbag0_4-3.zip", cos.get(1).name);
        assertEquals("1234.1_0_0.mbag0_4-3", cos.get(1).id);

        cos = sidb.findHeadBag("ark:/88888/1234", "1.0.0", 0);
        assertEquals(2, cos.size());
        assertEquals("1234.1_0_0.mbag0_4-3.zip", cos.get(0).name);
        assertEquals("1234.1_0_0.mbag0_4-3", cos.get(0).id);
        assertEquals("1234.1_0_0.mbag0_4-3.zip", cos.get(1).name);
        assertEquals("1234.1_0_0.mbag0_4-3", cos.get(1).id);

        cos = sidb.findHeadBag("ark:/88888/1234", 0);
        assertEquals(1, cos.size());
        assertEquals("1234.1_23_0.mbag0_4-85.zip", cos.get(0).name);
        assertEquals("1234.1_23_0.mbag0_4-85", cos.get(0).id);

        cos = sidb.findHeadBag("ark:/88888/mds2-9999", null, 0);
        assertEquals(1, cos.size());
        assertEquals("mds2-9999.1_1_0.mbag0_4-8.zip", cos.get(0).name);
        assertEquals("mds2-9999.1_1_0.mbag0_4-8", cos.get(0).id);

        cos = sidb.findHeadBag("ark:/88888/mds2-9999", "2.0", 0);
        assertEquals(0, cos.size());
        cos = sidb.findHeadBag("ark:/88888", 0);
        assertEquals(0, cos.size());
        cos = sidb.findHeadBag("888888833333", "1.0.0", 0);
        assertEquals(0, cos.size());
    }
}
