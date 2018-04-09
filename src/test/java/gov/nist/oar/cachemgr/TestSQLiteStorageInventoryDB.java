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
import java.util.HashSet;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * this test also tests the JDBCStorageInventoryDB implementation
 */
public class TestSQLiteStorageInventoryDB extends SQLiteStorageInventoryDB {

    @Rule
    public final TemporaryFolder tempf = new TemporaryFolder();

    public TestSQLiteStorageInventoryDB() {
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

    @Test
    public void testInit() throws IOException, SQLException, InventoryException {
        File dbf = new File(createDB());
        assertTrue(dbf.exists());
        Connection conn = DriverManager.getConnection("jdbc:sqlite:"+dbf.toString());
        DatabaseMetaData dmd = conn.getMetaData();
        String[] types = (String[]) dmd.getTableTypes().getArray(1).getArray();
        int i = 0;
        for (i=0; i < types.length; i++) {
            if (types[i] == "TAOLE") break;
        }
        if (i >= types.length)
            fail("No Tables defined in DB");

    }


}

