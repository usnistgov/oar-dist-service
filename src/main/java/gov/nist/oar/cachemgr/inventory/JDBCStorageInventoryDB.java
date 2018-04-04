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
package gov.nist.oar.cachemgr.inventory;

import gov.nist.oar.cachemgr.StorageInventoryDB;
import gov.nist.oar.cachemgr.InventoryException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * an abstract class representing a JDBC-based implementation of a StorageInventoryDB database.
 *
 * The implementation assumes the following table definitions exist in the database:
 * <verbatim>
 * CREATE TABLE IF NOT EXISTS algorithms (
 *    id   integer PRIMARY KEY,
 *    name text NOT NULL
 * );
 * 
 * CREATE TABLE IF NOT EXISTS volumes (
 *    id        integer PRIMARY KEY,
 *    name      text NOT NULL,
 *    priority  integer, 
 *    capacity  integer
 * );
 *
 * CREATE TABLE IF NOT EXISTS objects (
 *    name      text NOT NULL,
 *    size      integer,
 *    checksum  text,
 *    algorithm aid FOREIGN KEY,
 *    priority  integer,
 *    vid       integer FOREIGN KEY,
 *    since     integer,
 *    metadata  text
 * );
 * </verbatim>
 */
public class JDBCStorageInventoryDB implements StorageInventoryDB {

    protected String _dburl = null;
    protected Connection _conn = null;

    protected JDBCStorageInventoryDB(String dburl) {
        _dburl = dburl;
    }

    protected void connect() throws SQLException {
        if (_conn != null) disconnect();
        _conn = DriverManager.getConnection(_dburl);
    }

    protected void disconnect() throws SQLException {
        if (_conn != null) {
            _conn.close();
            _conn = null;
        }
    }

    public CacheObject[] findObject(String id) throws InventoryException {
    }

}
