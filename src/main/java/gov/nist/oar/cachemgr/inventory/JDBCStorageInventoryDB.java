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
import gov.nist.oar.cachemgr.InventorySearchException;
import gov.nist.oar.cachemgr.InventoryMetadataException;
import gov.nist.oar.cachemgr.CacheObject;

import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonException;
import javax.json.stream.JsonParsingException;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReaderFactory;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.IOException;
import java.io.StringReader;

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
    protected JsonReaderFactory jfac = Json.createReaderFactory(null);

    private HashMap<String, Integer> _volids = null;
    private HashMap<String, Integer> _algids = null;

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

    static final String find_sql = "SELECT d.name as name, v.name as volume, d.metadata as metadata " +
        "FROM datasets d, volumes v WHERE d.vid=v.id AND d.id='";

    public CacheObject[] findObject(String id) throws InventoryException {
        String sql = find_sql + id + "';";
        String jmd = null;
        JsonObject md = null;
        
        try {
            if (_conn == null) connect();
            Statement stmt = _conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            ArrayList<CacheObject> out = new ArrayList<CacheObject>();
            while (rs.next()) {
                jmd = rs.getString("metadata");
                if (jmd != null) {
                    try {
                        md = jfac.createReader(new StringReader(jmd)).readObject();
                    } catch (JsonParsingException ex) {
                        throw new InventoryException(ex);
                    } catch (JsonException ex) {
                        Throwable cex = ex.getCause();
                        if (cex != null)
                            throw new InventoryException(cex);
                        else
                            throw new InventoryException(ex);
                    }
                } else {
                    md = null;
                }
                out.add(new CacheObject(rs.getString("name"), md, rs.getString("volume")));
            }
            return out.toArray(new CacheObject[out.size()]);
        }
        catch (SQLException ex) {
            throw new InventorySearchException(ex);
        }
    }

    String add_sql = "INSERT INTO objects(" +
        "name,size,checksum,algorithm,priority,vid,since,metadata" +
        ") VALUES (?,?,?,?,?,?,?,?)";
    
    /**
     * record the addition of an object to a volume.  The metadata stored with the 
     * object can vary by application.  
     * @param id       the identifier for the object being stored
     * @param volname  the name of the volume where the object was added
     * @param objname  the name of the object was given in that volume
     * @param metadata the metadata to be associated with that object (can be null)
     */
    public void addObject(String id, String volname, String objname, JsonObject metadata)
        throws InventoryException
    {
        // the time the file was added.  It is assumed that the file will actually be copied into the
        // volume soon before or after the call to this method.
        long since = System.currentTimeMillis();

        int volid = getVolumeID(volname);
        if (volid < 0)
            throw new InventoryException("Not a registered volume: " + volname);

        long size = -1;
        String csum = null;
        String alg = "sha256";
        int priority = 10;
        String jmd = null;

        // pull info from metadata object that have their own columns in the table.
        if (metadata != null) {
            jmd = metadata.toString();
            String nm = "size";
            try {
                size = getMetadatumLong(metadata, nm, size);
                nm = "checksum";
                csum = metadata.getString(nm, csum);
                nm = "checksumAlgorithm";
                alg = metadata.getString(nm, alg);
                nm = "priority";
                priority = metadata.getInt(nm, priority);
            }
            catch (ClassCastException ex) {
                throw new InventoryMetadataException(nm + ": Metadatum has unexpected type: " + 
                                                     ex.getMessage(), nm, ex);
            }
        }
        int algid = getAlgorithmID(volname);
        if (algid < 0)
            throw new InventoryException("Not a registered algorithm: " + alg);

        try {
            if (_conn == null) connect();
            PreparedStatement stmt = _conn.prepareStatement(add_sql);
            stmt.setString(1, objname);
            stmt.setLong(2, size);
            stmt.setString(3, csum);
            stmt.setInt(4, algid);
            stmt.setInt(5, priority);
            stmt.setInt(6, volid);
            stmt.setString(7, jmd);

            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            throw new InventoryException("Failed to register object " + id + ": " + ex.getMessage(), ex);
        }
    }

    private long getMetadatumLong(JsonObject md, String name, long defval) {
        JsonNumber out = md.getJsonNumber(name);
        return (out == null) ? defval : out.longValueExact();
    }
    
    /**
     * return the names of checksumAlgorithms known to the database
     */
    public String[] checksumAlgorithms() throws InventoryException {
        if (_algids == null) loadAlgorithms();
        return _algids.keySet().toArray(new String[_volids.size()]);
    }

    private void loadAlgorithms() throws InventoryException {
        if (_algids != null) _algids = null;
        String sql = "SELECT id,name FROM algorithms";
        
        try {
            if (_conn == null) connect();
            Statement stmt = _conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            _algids = new HashMap<String, Integer>();

            while (rs.next()) {
                _algids.put(rs.getString("name"), new Integer(rs.getInt("id")));
            }
        } catch (SQLException ex) {
            _algids = null;
            throw new InventorySearchException("Failed to load checksum algorithm info from inventory: " +
                                               ex.getMessage(), ex);
        }
    }

    /**
     * return the primary key ID for the volume with the given name
     * @param name   the name of the volume
     */
    protected int getAlgorithmID(String name) throws InventoryException {
        if (_algids == null)
            loadAlgorithms();

        Integer out = _algids.get(name);
        if (out == null)
            return -1;
        return out.intValue();
    }

    /**
     * return the names of cache volumes registered in the database
     */
    public String[] volumes() throws InventoryException {
        if (_volids == null) loadVolumes();
        return _volids.keySet().toArray(new String[_volids.size()]);
    }

    private void loadVolumes() throws InventoryException {
        if (_volids != null) _volids = null;
        String sql = "SELECT id,name FROM volumes";
        
        try {
            if (_conn == null) connect();
            Statement stmt = _conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            _volids = new HashMap<String, Integer>();

            while (rs.next()) {
                _volids.put(rs.getString("name"), new Integer(rs.getInt("id")));
            }
        } catch (SQLException ex) {
            _volids = null;
            throw new InventorySearchException("Failed to load volume info from inventory: " +
                                               ex.getMessage(), ex);
        }
    }

    /**
     * return the primary key ID for the volume with the given name
     * @param name   the name of the volume
     */
    protected int getVolumeID(String name) throws InventoryException {
        if (_volids == null)
            loadVolumes();

        Integer out = _volids.get(name);
        if (out == null)
            return -1;
        return out.intValue();
    }

    String rm_sql = "DELETE FROM objects WHERE volid=? AND name=?";
        
    /**
     * record the removal of the object with the given name from the given volume
     * @param volname  the name of the volume where the object was added
     * @param objname  the name of the object was given in that volume
     */
    public void removeObject(String volname, String objname) throws InventoryException {
        int volid = getVolumeID(volname);
        if (volid < 0)
            throw new InventoryException("Not a registered volume: " + volname);

        try {
            if (_conn == null) connect();
            PreparedStatement stmt = _conn.prepareStatement(rm_sql);
            stmt.setInt(1, volid);
            stmt.setString(2, objname);
        }
        catch (SQLException ex) {
            throw new InventoryException("Failed to remove object " + objname + " from volume " +
                                         volname + ": " + ex.getMessage(), ex);
        }
    }


}
