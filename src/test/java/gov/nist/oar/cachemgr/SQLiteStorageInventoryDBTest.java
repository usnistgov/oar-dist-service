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

import gov.nist.oar.cachemgr.InventoryException;
import gov.nist.oar.cachemgr.StorageInventoryDB;
import gov.nist.oar.cachemgr.inventory.JDBCStorageInventoryDB;
import gov.nist.oar.cachemgr.inventory.SQLiteStorageInventoryDB;

import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * this test also tests the JDBCStorageInventoryDB implementation
 */
public class SQLiteStorageInventoryDBTest extends SQLiteStorageInventoryDB {

    @Rule
    public final TemporaryFolder tempf = new TemporaryFolder();

    public SQLiteStorageInventoryDBTest() {
        super("");
        _dburl = null;
    }

    @After
    public void tearDown() {
        try { disconnect(); } catch (SQLException ex) {} 
    }

    String createDB() throws IOException, InventoryException {
        File tf = tempf.newFile("testdb.sqlite");
        String out = tf.getAbsolutePath();
        SQLiteStorageInventoryDB.initializeDB(out);
        dbfile = out;
        _dburl = "jdbc:sqlite:"+dbfile;
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
        String[] ss = { "TABLES" };
        svals = getStringColumn(dmd.getTables(null, null, null, ss), "TABLE_NAME");
        assertTrue("Missing volumes table", svals.contains("volumes"));
        assertTrue("Missing algorithms table", svals.contains("algorithms"));
        assertTrue("Missing objects table", svals.contains("objects"));
    }


}

