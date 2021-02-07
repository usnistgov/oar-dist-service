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

import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.InventorySearchException;
import gov.nist.oar.distrib.cachemgr.inventory.JDBCStorageInventoryDB;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;


/**
 * An <a href="https://sqlite.org/">SQLite</a> implementation of a storage inventory database.  
 */
public class SQLiteStorageInventoryDB extends JDBCStorageInventoryDB {

    protected File dbfile = null;

    /**
     * initialize the class to access the database via a given filename
     * @param filepath   the path to the SQLite file containing the 
     *                   inventory database
     */
    public SQLiteStorageInventoryDB(String filepath) {
        // we're allowing the file path to include the jdbc URL prefix.
        super( (filepath.startsWith("jdbc:sqlite:")) ? filepath : "jdbc:sqlite:"+filepath );
        dbfile = new File((filepath.startsWith("jdbc:sqlite:"))
                          ? filepath.substring("jdbc:sqlite:".length())
                          : filepath);
    }

    /**
     * create the necessary tables and indicies for the storage inventory
     * @param filepath   the path to the SQLite file to add the inventory
     *                   tables to
     */
    public static void initializeDB(String filepath) throws InventoryException {
        // load the sql script from a resource
        Class thiscl = SQLiteStorageInventoryDB.class;
        BufferedReader rdr = null;
        StringBuilder sb = new StringBuilder();
        try {
            rdr = new BufferedReader(new InputStreamReader(thiscl.getResourceAsStream("res/sqlite_create.sql")));
            
            String line = null;
            while ((line = rdr.readLine()) != null) 
                sb.append(" ").append(line);
        }
        catch (IOException ex) {
            throw new InventoryException("Problem reading db init script: "+ex.getMessage(), ex);
        }
        finally {
            try { if (rdr != null) rdr.close(); } catch (IOException ex) { }
        }

        // split the script into separate statements
        String[] stmts = sb.toString().split(";");

        // execute each statement
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:"+filepath);
            for (String s : stmts) {
                try {
                    conn.createStatement().execute(s.trim());
                }
                catch (SQLException ex) {
                    throw new InventorySearchException("DB init SQL statement failed: "+s+":\n"
                                                       +ex.getMessage(), ex);
                }
            }
        }
        catch (SQLException ex) {
            throw new InventorySearchException("Failed to open DB file, "+filepath+": "
                                               +ex.getMessage(), ex);
        }
        finally {
            try { if (conn != null) conn.close(); } catch (SQLException ex) { }
        }
    }

    @Override
    protected void connect() throws SQLException {
        if (! dbfile.isFile())
            throw new SQLException("Missing SQLite db file: "+dbfile.toString());
        super.connect();
    }

}
