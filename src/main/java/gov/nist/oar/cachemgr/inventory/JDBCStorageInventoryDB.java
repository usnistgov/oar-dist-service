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

import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONException;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

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
 *    volume    integer FOREIGN KEY,
 *    since     integer,
 *    metadata  text
 * );
 * </verbatim>
 */
public class JDBCStorageInventoryDB implements StorageInventoryDB {

    protected String _dburl = null;
    protected Connection _conn = null;

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

    static final String find_sql =
        "SELECT d.objid as id, d.name as name, v.name as volume, d.size as size, "+
        "d.priority as priority, d.since as since, d.metadata as metadata " +
        "FROM objects d, volumes v WHERE d.volume=v.id ";

    /**
     * return all the known locations of an object with a given id in the volumes
     * managed by this database.  
     * @param id   the identifier for the desired object
     * @returns List<CacheObject>  the copies of the object in the cache.  Each element represents
     *                             a copy in a different cache volume.
     * @throws InventoryException  if there is an error accessing the underlying database.
     */
    public List<CacheObject> findObject(String id) throws InventoryException {
        String sql = find_sql + "AND d.objid='" + id + "';";
        return _findObject(sql);
    }

    private List<CacheObject> _findObject(String sql) throws InventoryException {
        String jmd = null;
        JSONObject md = null;
        CacheObject co = null;
        
        try {
            if (_conn == null) connect();
            Statement stmt = _conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            ArrayList<CacheObject> out = new ArrayList<CacheObject>();
            while (rs.next()) {
                jmd = rs.getString("metadata");
                if (jmd != null)
                    md = parseMetadata(jmd);
                else 
                    md = new JSONObject();
                if (rs.getObject("size") != null)
                    md.put("size", rs.getLong("size"));
                if (rs.getObject("priority") != null)
                    md.put("priority", rs.getInt("priority"));
                if (rs.getObject("since") != null) {
                    int since = rs.getInt("since");
                    md.put("since", since);
                    md.put("sinceDate", ZonedDateTime.ofInstant(Instant.ofEpochMilli(since),
                                                                ZoneOffset.UTC)
                                                     .format(DateTimeFormatter.ISO_INSTANT));
                }
                co = new CacheObject(rs.getString("name"), md, rs.getString("volume"));
                if (rs.getObject("id") != null)
                    co.id = rs.getString("id");
                out.add(co);
            }
            return out;
        }
        catch (SQLException ex) {
            throw new InventorySearchException(ex);
        }
    }

    /**
     * return all the data object with a given name in a particular cache volume
     * @param volname  the name of the volume to search
     * @param objname  the name of the object was given in that volume
     * @returns CacheObject  the object in the cache or null if the object is not found in the volume
     * @throws InventoryException  if there is an error accessing the underlying database.
     */
    public CacheObject findObject(String volname, String objname) throws InventoryException {
        String fsql = find_sql + "AND v.name='" + volname + "' AND d.name='" + objname + "';";
        List<CacheObject> objs = _findObject(fsql);
        if (objs.size() == 0) return null;

        return objs.get(0);
    }

    String add_sql = "INSERT INTO objects(" +
        "objid,name,size,checksum,algorithm,priority,volume,since,metadata" +
        ") VALUES (?,?,?,?,?,?,?,?,?)";
    
    /**
     * record the addition of an object to a volume.  The metadata stored with the 
     * object can vary by application.  
     * @param id       the identifier for the object being stored
     * @param volname  the name of the volume where the object was added
     * @param objname  the name of the object was given in that volume
     * @param metadata the metadata to be associated with that object (can be null)
     */
    public void addObject(String id, String volname, String objname, JSONObject metadata)
        throws InventoryException
    {
        // the time the file was added.  It is assumed that the file will actually be copied into the
        // volume soon before or after the call to this method.
        // long since = System.currentTimeMillis();
        Instant since = Instant.now();

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
            metadata = this.copy(metadata);
            metadata.put("since", since.toEpochMilli());
            metadata.put("sinceDate", ZonedDateTime.ofInstant(since, ZoneOffset.UTC)
                                                   .format(DateTimeFormatter.ISO_INSTANT));
            
            jmd = metadata.toString();
            String nm = "size";
            try {
                size = getMetadatumLong(metadata, nm, size);
                nm = "checksum";
                csum = getMetadatumString(metadata, nm, csum);
                nm = "checksumAlgorithm";
                alg = getMetadatumString(metadata, nm, alg);
                nm = "priority";
                priority = getMetadatumInt(metadata, nm, priority);
            }
            catch (JSONException ex) {
                throw new InventoryMetadataException(nm + ": Metadatum has unexpected type: " + 
                                                     ex.getMessage(), nm, ex);
            }
        }
        int algid = getAlgorithmID(alg);
        if (algid < 0)
            throw new InventoryException("Not a registered algorithm: " + alg);

        // check to see if we have this record in the database already
        StringBuilder sb = new StringBuilder(find_sql);
        sb.append("AND d.objid='").append(id).append("' AND v.name='").append(volname);
        sb.append("' AND d.name='").append(objname).append("';");
        List<CacheObject> found = _findObject(sb.toString());
        for(CacheObject co : found)
            // remove these entries with the same name
            removeObject(co.volname, co.name);

        // add the new object name to the database
        try {
            if (_conn == null) connect();
            PreparedStatement stmt = _conn.prepareStatement(add_sql);
            stmt.setString(1, id);
            stmt.setString(2, objname);
            stmt.setLong(3, size);
            stmt.setString(4, csum);
            stmt.setInt(5, algid);
            stmt.setInt(6, priority);
            stmt.setInt(7, volid);
            stmt.setLong(8, since.toEpochMilli());
            stmt.setString(9, jmd);
            
            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            throw new InventoryException("Failed to register object " + id + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * update the metadata for an object already in the database.  The update will apply
     * only to the entry for the object in the specified volume.  Only the entries for the 
     * metadata with the provided names will be updated; all other values normally should 
     * not change.  
     * 
     * @param volname     the name of the volume where the object of interest is stored
     * @param objname     the storage-specific name assigned to the object of interest 
     * @param metadata    the set of metadata to update.  Only the data associated in with 
     *                       names in this container will be updated.  
     * @returns boolean   false if the objname is not registered as in the specified volume
     * @throws InventoryException   if there is a failure updating the database, including 
     *                       consistency errors.
     */
    public boolean updateMetadata(String volname, String objname, JSONObject metadata)
        throws InventoryException
    {
        CacheObject obj = findObject(volname, objname);
        if (obj == null) return false;

        int volid = getVolumeID(volname);
        if (volid < 0)
            // should not happen
            throw new InventoryException("Not a registered volume: " + volname);

        // update the metadata
        String nm = null;
        StringBuilder sql = new StringBuilder("UPDATE objects SET");
        try {
            if (metadata.has("size")) {
                nm = "size";
                sql.append(" size=").append(Long.toString(metadata.getLong(nm))).append(",");
            }
            if (metadata.has("checksum")) {
                nm = "checksum";
                sql.append(" checksum='").append(metadata.getString(nm)).append("'").append(",");
            }
            if (metadata.has("priority")) {
                nm = "priority";
                sql.append(" priority=").append(Integer.toString(metadata.getInt(nm))).append(",");
            }
        } catch (JSONException ex) {
            throw new InventoryMetadataException(nm + ": Metadatum has unexpected type: " + 
                                                 ex.getMessage(), nm, ex);
        }

        Statement stmt = null;
        try {
            if (_conn == null) connect();
            JSONObject md = obj.exportMetadata();
            for (String prop : metadata.keySet())
                md.put(prop, metadata.get(prop));

            StringBuilder updsql = new StringBuilder(sql.toString());
            updsql.append(" metadata='").append(md.toString())
                  .append("' WHERE name='").append(objname)
                  .append("' AND volume=").append(volid).append(";");

            stmt = _conn.createStatement();
            stmt.execute(updsql.toString());
            if (stmt.getUpdateCount() < 1)
                return false;
        }
        catch (SQLException ex) {
            throw new InventoryException("Failed to update object " + volname+":"+objname +
                                         ": " + ex.getMessage(), ex);
        }
        finally {
            try { if (stmt != null) stmt.close(); }
            catch (SQLException ex) { }
        }

        return true;
    }

    private long getMetadatumLong(JSONObject md, String name, long defval) {
        if (! md.has(name))
            return defval;
        return md.getLong(name);
    }
    private int getMetadatumInt(JSONObject md, String name, int defval) {
        if (! md.has(name))
            return defval;
        return md.getInt(name);
    }
    private String getMetadatumString(JSONObject md, String name, String defval) {
        if (! md.has(name))
            return defval;
        return md.getString(name);
    }
    
    /**
     * return the names of checksumAlgorithms known to the database
     */
    public String[] checksumAlgorithms() throws InventoryException {
        if (_algids == null) loadAlgorithms();
        return _algids.keySet().toArray(new String[_algids.size()]);
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

    String rm_sql = "DELETE FROM objects WHERE volume=? AND name=?";
        
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
            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            throw new InventoryException("Failed to remove object " + objname + " from volume " +
                                         volname + ": " + ex.getMessage(), ex);
        }
    }

    String add_alg_sql = "INSERT INTO algorithms(name) VALUES (?)";

    /**
     * create an entry for the given checksum algorithm in the database, making it a recognised 
     * algorithm.
     */
    public void registerAlgorithm(String algname) throws InventoryException {
        if (getAlgorithmID(algname) >= 0)
            // the name is already registered; don't add again
            return;
        
        try {
            if (_conn == null) connect();
            PreparedStatement stmt = _conn.prepareStatement(add_alg_sql);
            stmt.setString(1, algname);

            stmt.executeUpdate();
            loadAlgorithms();
        }
        catch (SQLException ex) {
            throw new InventoryException("Failed to register new algorithm into DB ("+algname+
                                         "): "+ex.getMessage(), ex);
        }
    }

    String add_vol_sql = "INSERT INTO volumes(name,capacity,priority,metadata) VALUES (?,?,?,?)";
    String upd_vol_sql = "UPDATE volumes SET capacity=?, priority=?, metadata=? where name=?";

    /**
     * add a cache volume that is available for storage to the database.  
     * @param name      the name to refer to the cache volume as.  This is used as 
     *                  the volume name in any CacheObject instances returned by 
     *                  findObject()
     * @param capacity  the number of bytes of data that this volume can hold.
     * @param metadata  arbitrary metadata describing this volume.  This can be null.
     */
    public void registerVolume(String name, long capacity, JSONObject metadata)
        throws InventoryException
    {
        int id = getVolumeID(name);
        
        Integer priority = null;
        String nm = "priority", jmd = null;
        if (metadata != null) {
            try {
                priority = (Integer) metadata.get(nm);
            }
            catch (JSONException ex) {
                throw new InventoryMetadataException(nm + ": Metadatum has unexpected type: " + 
                                                     ex.getMessage(), nm, ex);
            }
            jmd = metadata.toString();
        }
        
        if (id < 0) {
            try {
                // not registered yet
                if (_conn == null) connect();
                PreparedStatement stmt = _conn.prepareStatement(add_vol_sql);
                stmt.setString(1, name);
                stmt.setLong(2, capacity);
                if (priority == null)
                    stmt.setNull(3, Types.INTEGER);
                else
                    stmt.setInt(3, priority.intValue());
                if (jmd == null)
                    stmt.setNull(4, Types.VARCHAR);
                else
                    stmt.setString(4, jmd);

                stmt.executeUpdate();
            }
            catch (SQLException ex) {
                throw new InventoryException("Failed to register new volume in DB ("+name+
                                             "): "+ex.getMessage(), ex);
            }
        }
        else {
            try {
                // not registered yet
                if (_conn == null) connect();
                PreparedStatement stmt = _conn.prepareStatement(upd_vol_sql);
                stmt.setLong(1, capacity);
                if (priority == null)
                    stmt.setNull(2, Types.INTEGER);
                else
                    stmt.setInt(2, priority.intValue());
                stmt.setString(3, jmd);
                stmt.setString(4, name);

                stmt.executeUpdate();
            }
            catch (SQLException ex) {
                throw new InventoryException("Failed to update info for registered volume ("+name+
                                             "): "+ex.getMessage(), ex);
            }
        }
        loadVolumes();
    }

    String get_vol_info = "SELECT metadata,capacity,priority FROM volumes WHERE name=?";

    /**
     * return the information associated with the registered storage volume
     * with the given name.
     */
    public JSONObject getVolumeInfo(String name) throws InventoryException {
        try {
            if (_conn == null) connect();
            PreparedStatement stmt = _conn.prepareStatement(get_vol_info);
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (! rs.next())
                throw new InventoryException("No volume registered with name="+name);

            JSONObject md = null;
            String jmd = rs.getString("metadata");
            if (jmd != null)
                md = parseMetadata(jmd);
            if (md == null)
                md = new JSONObject();
            if (rs.getObject("capacity") != null)
                md.put("capacity", rs.getLong("capacity"));
            if (rs.getObject("priority") != null)
                md.put("priority", rs.getInt("priority"));

            return md;
        }
        catch (SQLException ex) {
            throw new InventoryException("Failed to pull info for volume "+name+
                                         ": "+ex.getMessage(), ex);
        }
    }

    private JSONObject parseMetadata(String jencoded) throws InventoryException {
        try {
            return new JSONObject(new JSONTokener(jencoded));
        } catch (JSONException ex) {
            Throwable cex = ex.getCause();
            String pfx = "JSON Parsing error: ";
            if (cex != null)
                throw new InventoryException(pfx+cex);
            else
                throw new InventoryException(pfx+ex);
        }
    }

    protected void finalize() {
        try { disconnect(); } catch (SQLException ex) {} 
    }

    private JSONObject copy(JSONObject jo) {
        return new JSONObject(jo, JSONObject.getNames(jo));
    }
}
