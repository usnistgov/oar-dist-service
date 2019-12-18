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
import gov.nist.oar.cachemgr.SelectionStrategy;
import gov.nist.oar.cachemgr.InventoryException;
import gov.nist.oar.cachemgr.InventorySearchException;
import gov.nist.oar.cachemgr.InventoryMetadataException;
import gov.nist.oar.cachemgr.VolumeNotFoundException;
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
import java.util.Map;
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
 * an abstract class representing a JDBC-based implementation of a {@link StorageInventoryDB} database.
 *
 * The implementation assumes the following table definitions exist in the database:
 * <pre>
 * CREATE TABLE IF NOT EXISTS algorithms (
 *    id   integer PRIMARY KEY,
 *    name text NOT NULL
 * );
 * 
 * CREATE TABLE IF NOT EXISTS volumes (
 *    id        integer PRIMARY KEY,
 *    name      text NOT NULL,
 *    priority  integer, 
 *    capacity  integer,
 *    status    integer NOT NULL,    // 0=disabled, 1=can getinfo, 2=can get obj, 3=can update
 *    metadata  text
 * );
 *
 * CREATE TABLE IF NOT EXISTS objects (
 *    objid     text NOT NULL,
 *    size      integer,
 *    checksum  text,
 *    algorithm aid FOREIGN KEY,
 *    priority  integer,
 *    volume    integer FOREIGN KEY,
 *    name      text NOT NULL,
 *    since     integer,
 *    cached    boolean NOT NULL DEFAULT 0,
 *    metadata  text
 * );
 * </pre>
 */
public class JDBCStorageInventoryDB implements StorageInventoryDB {

    static final String find_sql =
        "SELECT d.objid as id, d.name as name, v.name as volume, d.size as size, "+
        "d.priority as priority, d.since as since, d.metadata as metadata " +
        "FROM objects d, volumes v WHERE d.volume=v.id AND d.cached=1 ";

    static final String deletion_pSelect = 
        find_sql + "AND v.status>2 AND d.cached=1 AND d.priority>0 AND v.name=? "
                 + "ORDER BY d.priority DESC, d.since ASC";

    static final String deletion_sSelect = 
        find_sql + "AND v.status>2 AND d.cached=1 AND d.priority>0 AND v.name=? "
                 + "ORDER BY d.priority DESC, d.size DESC, d.since ASC";

    static final String deletion_dSelect = 
        find_sql + "AND v.status>2 AND d.cached=1 AND d.priority>0 AND v.name=? "
                 + "ORDER BY d.since ASC, d.priority DESC";

    static final String defaultDeletionPlanSelect = deletion_pSelect;

    static HashMap<String, String> purposes = new HashMap<String, String>(2);
    static {
        JDBCStorageInventoryDB.purposes.put("deletion_d", deletion_dSelect);
        JDBCStorageInventoryDB.purposes.put("deletion_p", deletion_pSelect);
        JDBCStorageInventoryDB.purposes.put("deletion_s", deletion_sSelect);
        JDBCStorageInventoryDB.purposes.put("deletion",   deletion_pSelect);
        JDBCStorageInventoryDB.purposes.put("",           deletion_pSelect);
    }

    protected String _dburl = null;
    protected Connection _conn = null;

    private HashMap<String, Integer> _volids = null;
    private HashMap<String, Integer> _algids = null;

    protected String dplanselect = defaultDeletionPlanSelect;

    /**
     * create an inventory database around a database accessible via a given JDBC URL.  
     * It is assumed that a JDBC driver for the database exists in the Java CLASSPATH.  
     * The value of {@link #defaultDeletionPlanSelect} will be loaded as the SQL SELECT 
     * will be used to select deletable objects from a data volume (see 
     * {@link #JDBCStorageInventoryDB(String,String)}).
     * 
     * @param dburl    the JDBC URL to use to connect to the existing database.  
     */
    protected JDBCStorageInventoryDB(String dburl) {
        _dburl = dburl;
    }

    /**
     * create an inventory database around a database accessible via a given JDBC URL.  
     * It is assumed that a JDBC driver for the database exists in the Java CLASSPATH.  
     * @param dburl        the JDBC URL to use to connect to the existing database.  
     * @param dplanselect  the SQL SELECT statement to use to generate a list of deletable 
     *                     cache objects from a cache volume.  It must be in PreparedStatement
     *                     style with one '?', representing the name of the data volume to 
     *                     search.  
     */
    protected JDBCStorageInventoryDB(String dburl, String dplanselect) {
        this(dburl);
        this.dplanselect = dplanselect;
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

    /**
     * return all the known locations of an object with a given id in the volumes
     * managed by this database.  The implementation should minimize the assumptions 
     * for the purpose of the query.  (VOL_FOR_GET is recommended.)
     * @param id   the identifier for the desired object
     * @return List<CacheObject>  the copies of the object in the cache.  Each element represents
     *                             a copy in a different cache volume.  This list will be empty if 
     *                             the object is not registered.
     * @throws InventoryException  if there is an error accessing the underlying database.
     */
    public List<CacheObject> findObject(String id) throws InventoryException {
        return this.findObject(id, VOL_FOR_GET);
    }

    /**
     * return all the known locations of an object with a given id in the volumes
     * managed by this database.  
     * @param id       the identifier for the desired object
     * @param purpose  an integer indicating the purpose for locating the object.  Recognized 
     *                 values are defined in the {@link gov.nist.oar.cachemgr.VolumeStatus} interface.
     * @return List<CacheObject>  the copies of the object in the cache.  Each element represents
     *                             a copy in a different cache volume.
     * @throws InventoryException  if there is an error accessing the underlying database.
     */
    public List<CacheObject> findObject(String id, int purpose) throws InventoryException {
        StringBuilder sql = new StringBuilder(find_sql);
        sql.append("AND d.objid='").append(id).append("' AND v.status > ").append(purpose);
        if (purpose >= VOL_FOR_GET)
            sql.append(" AND d.cached=1");
        sql.append(";");

        // lock access to the db in case a deletion plan is progress, unless the caller just
        // wants information. 
        Object lock = (purpose >= VOL_FOR_GET) ? this : new Object();
        synchronized (lock) {
            return _findObject(sql.toString());
        }
    }

    /**
     * submit an SQL to the underlying data base to return matching objects.
     *
     * @param objsql   an SQL query that returns a list of data objects
     */
    protected List<CacheObject> _findObject(String objsql) throws InventoryException {
        try {
            if (_conn == null) connect();
            Statement stmt = _conn.createStatement();
            ResultSet rs = stmt.executeQuery(objsql);
            ArrayList<CacheObject> out = new ArrayList<CacheObject>();
            while (rs.next()) {
                out.add(_extractObject(rs));
            }
            return out;
        }
        catch (SQLException ex) {
            throw new InventorySearchException(ex);
        }
    }

    /*
     * convert the current database result into a CacheObject
     */
    private CacheObject _extractObject(ResultSet rs) throws SQLException, InventoryException {
        String jmd = null;
        JSONObject md = null;
        CacheObject co = null;

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
            long since = rs.getLong("since");
            md.put("since", since);
            md.put("sinceDate", ZonedDateTime.ofInstant(Instant.ofEpochMilli(since),
                                                        ZoneOffset.UTC)
                                             .format(DateTimeFormatter.ISO_INSTANT));
        }
        co = new CacheObject(rs.getString("name"), md, rs.getString("volume"));
        if (rs.getObject("id") != null)
            co.id = rs.getString("id");
        return co;
    }

    /**
     * return all data objects found in the specified data volume for a particular purpose.  The 
     * purpose specified can affect what files are selected and/or how they are sorted in the returned 
     * list.  The default behavior is to assume that the listing for creating a deletion plan for the
     * the specified volume.  This implementation ignores the purpose parameter and always returns
     * objects appropriate for deletion.
     * @param volname     the name of the volume to list objects from.
     * @param purpose     a label that indicates the purpose for retrieving the list so as to 
     *                    affect object selection and sorting.  The recognized values include
     *                    <code>deletion_p</code> and <code>deletion_d</code>; both select objects 
     *                    for creating a deletion plan, but the former orders first by the object's 
     *                    priority value and then by the date of last access while the latter orders 
     *                    first by last access and then by priority.  <code>deletion</code> is
     *                    handled as <code>deletion_p</code> and will be used by default if purpose 
     *                    is empty or null.
     */
    public List<CacheObject> selectObjectsFrom(String volname, String purpose) throws InventoryException {
        int i=0, lim = 5000;   // TODO: make limit configurable

        String selectquery = _selectQuery(purpose);

        PreparedStatement stmt = null;
        try {
            if (_conn == null) connect();
            stmt = _conn.prepareStatement(selectquery);
            stmt.setString(1, volname);

            synchronized (this) {
                ResultSet rs = stmt.executeQuery();
                ArrayList<CacheObject> out = new ArrayList<CacheObject>();
                while (i < lim && rs.next()) {
                    out.add(_extractObject(rs));
                    i++;
                }

                // log limit reached?
                return out;
            }
        }
        catch (SQLException ex) {
            throw new InventoryException("Failure while listing objects in vol=" + volname +
                                         ": " + ex.getMessage(), ex);
        }
        finally {
            try { if (stmt != null) stmt.close(); }
            catch (SQLException ex) { }
        }
    }

    protected String _selectQuery(String purpose) {
        if (purpose == null) purpose = "";
        return purposes.getOrDefault(purpose, purposes.get(""));
    }

    /**
     * return a list of data objects found in the specified data volume according to a given 
     * selection strategy.  
     * @param volname     the name of the volume to list objects from.
     * @param strategy    an encapsulation of the strategy that should be used for selecting the 
     *                    records.  
     */
    public List<CacheObject> selectObjectsFrom(String volname, SelectionStrategy strategy)
        throws InventoryException
    {
        strategy.reset();
        String selectquery = _selectQuery(strategy.getPurpose());

        PreparedStatement stmt = null;
        try {
            if (_conn == null) connect();
            stmt = _conn.prepareStatement(selectquery);
            stmt.setString(1, volname);

            synchronized (this) {
                ResultSet rs = stmt.executeQuery();
                ArrayList<CacheObject> out = new ArrayList<CacheObject>();
                CacheObject co = null;
                while (! strategy.limitReached() && rs.next()) {
                    co = _extractObject(rs);
                    strategy.score(co);
                    out.add(co);
                }

                strategy.sort(out);
                return out;
            }
        }
        catch (SQLException ex) {
            throw new InventoryException("Failure while listing objects in vol=" + volname +
                                         ": " + ex.getMessage(), ex);
        }
        finally {
            try { if (stmt != null) stmt.close(); }
            catch (SQLException ex) { }
        }
    }
    
    /**
     * return all the data objects with a given name in a particular cache volume
     * @param volname  the name of the volume to search
     * @param objname  the name of the object was given in that volume
     * @return CacheObject  the object in the cache or null if the object is not found in the volume
     * @throws InventoryException  if there is an error accessing the underlying database.
     */
    public CacheObject findObject(String volname, String objname) throws InventoryException {
        String fsql = find_sql + "AND v.name='" + volname + "' AND d.name='" + objname + "';";
        List<CacheObject> objs = null;

        // lock access to the db in case a deletion plan is progress, unless the caller just
        // wants information. 
        synchronized (this) {
            objs = _findObject(fsql);
        }
        if (objs.size() == 0) return null;

        return objs.get(0);
    }

    String add_sql = "INSERT INTO objects(" +
        "objid,name,size,checksum,algorithm,priority,volume,since,cached,metadata" +
        ") VALUES (?,?,?,?,?,?,?,?,?,?)";
    
    /**
     * record the addition of an object to a volume.  The metadata stored with the 
     * object can vary by application.  
     * @param id       the identifier for the object being stored
     * @param volname  the name of the volume where the object was added
     * @param objname  the name of the object was given in that volume
     * @param metadata the metadata to be associated with that object (can be null)
     * @throws InventoryException  if a problem occurs while interacting with the inventory database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public synchronized void addObject(String id, String volname, String objname, JSONObject metadata)
        throws InventoryException
    {
        // the time the file was added.  It is assumed that the file will actually be copied into the
        // volume soon before or after the call to this method.
        // long since = System.currentTimeMillis();
        Instant since = Instant.now();

        int volid = getVolumeID(volname);
        if (volid < 0)
            throw new VolumeNotFoundException(volname);

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
        sb.append("AND v.name='").append(volname);
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
            stmt.setBoolean(9, true);
            stmt.setString(10, jmd);
            
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
     * @return boolean   false if the objname is not registered as in the specified volume
     * @throws InventoryException   if there is a failure updating the database, including 
     *                       consistency errors.  
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public synchronized boolean updateMetadata(String volname, String objname, JSONObject metadata)
        throws InventoryException
    {
        CacheObject obj = findObject(volname, objname);
        if (obj == null) return false;

        int volid = getVolumeID(volname);
        if (volid < 0)
            // should not happen
            throw new VolumeNotFoundException(volname);

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

            synchronized (this) {
                stmt = _conn.createStatement();
                stmt.execute(updsql.toString());
                if (stmt.getUpdateCount() < 1)
                    return false;
            }
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

    String rm_sql = "UPDATE objects SET cached=0 WHERE volume=? AND name=?";
    String prg_sql = "DELETE FROM objects WHERE volume=? AND name=?";
        
    /**
     * record the removal of the object with the given name from the given volume
     * @param volname  the name of the volume where the object was added
     * @param objname  the name of the object was given in that volume
     * @throws InventoryException  if a problem occurs while interacting with the inventory database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public synchronized void removeObject(String volname, String objname) throws InventoryException {
        removeObject(volname, objname, false);
    }

    /**
     * record the removal of the object with the given name from the given volume
     * @param volname  the name of the volume where the object was added
     * @param objname  the name of the object was given in that volume
     * @param purge    if false, a record for the object will remain in the database but marked as 
     *                    uncached.  If true, the record will be complete removed from the database.
     * @throws InventoryException  if there is an error accessing the underlying database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public void removeObject(String volname, String objname, boolean purge) throws InventoryException {
        String sql = rm_sql;
        if (purge)
            sql = prg_sql;
        _removeObject(volname, objname, sql);
    }

    private synchronized void _removeObject(String volname, String objname, String sqltmpl)
        throws InventoryException
    {
        int volid = getVolumeID(volname);
        if (volid < 0)
            throw new VolumeNotFoundException(volname);

        PreparedStatement stmt = null;
        try {
            if (_conn == null) connect();
            stmt = _conn.prepareStatement(sqltmpl);
            stmt.setInt(1, volid);
            stmt.setString(2, objname);
            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            throw new InventoryException("Failed to remove object " + objname + " from volume " +
                                         volname + ": " + ex.getMessage(), ex);
        }
        finally {
            try { if (stmt != null) stmt.close(); }
            catch (SQLException ex) { }
        }
    }

    /**
     * remove all object entries.  This should be used when reinitializing the database.
     * @return boolean   false if the database was apparently empty already, true otherwise.
     */
    public synchronized boolean removeAllObjects() throws InventoryException {
        String sql = "DELETE FROM objects;";
        Statement stmt = null;
        try {
            if (_conn == null) connect();
            stmt = _conn.createStatement();
            stmt.execute(sql);
            return (stmt.getUpdateCount() > 0);
        }
        catch (SQLException ex) {
            throw new InventoryException("Problem emptying database: "+ex.getMessage(), ex);
        }
        finally {
            try { if (stmt != null) stmt.close(); }
            catch (SQLException ex) { }
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

    String add_vol_sql = "INSERT INTO volumes(name,capacity,priority,status,metadata) VALUES (?,?,?,?,?)";
    String upd_vol_sql = "UPDATE volumes SET capacity=?, priority=?, status=?, metadata=? WHERE name=?";

    /**
     * add a cache volume that is available for storage to the database.  
     * @param name      the name to refer to the cache volume as.  This is used as 
     *                  the volume name in any CacheObject instances returned by 
     *                  findObject()
     * @param capacity  the number of bytes of data that this volume can hold.
     * @param metadata  arbitrary metadata describing this volume.  This can be null.
     */
    public synchronized void registerVolume(String name, long capacity, JSONObject metadata)
        throws InventoryException
    {
        int id = getVolumeID(name);
        
        Integer priority = null;
        int status = VOL_FOR_UPDATE;  // fully functional
        String nm = "priority", jmd = null;
        if (metadata != null) {
            try {
                priority = (Integer) metadata.get(nm);
                nm = "status";
                status = metadata.optInt(nm, status);
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
                stmt.setInt(4, status);
                if (jmd == null)
                    stmt.setNull(5, Types.VARCHAR);
                else
                    stmt.setString(5, jmd);

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
                stmt.setInt(3, status);
                stmt.setString(4, jmd);
                stmt.setString(5, name);

                stmt.executeUpdate();
            }
            catch (SQLException ex) {
                throw new InventoryException("Failed to update info for registered volume ("+name+
                                             "): "+ex.getMessage(), ex);
            }
        }
        loadVolumes();
    }

    String get_vol_info = "SELECT metadata,capacity,priority,status FROM volumes WHERE name=?";

    /**
     * return the information associated with the registered storage volume
     * with the given name.
     * @param name    the name of the volume to get info on
     * @throws InventoryException  if a failure occurs while interacting with the inventory database
     * @throws VolumeNotFoundException  if the requested name is not registered as a known volume
     */
    public JSONObject getVolumeInfo(String name) throws InventoryException {
        try {
            if (_conn == null) connect();
            PreparedStatement stmt = _conn.prepareStatement(get_vol_info);
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (! rs.next())
                throw new VolumeNotFoundException(name);

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
            if (rs.getObject("status") != null)
                md.put("status", rs.getInt("status"));

            return md;
        }
        catch (SQLException ex) {
            throw new InventoryException("Failed to pull info for volume "+name+
                                         ": "+ex.getMessage(), ex);
        }
    }

    /**
     * update the status of a registered volume
     * @throws InventoryException  if there is an error accessing the underlying database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public synchronized void setVolumeStatus(String volname, int status) throws InventoryException {
        int volid = getVolumeID(volname);
        if (volid < 0)
            throw new VolumeNotFoundException(volname);

        String sql = "UPDATE volumes SET status="+Integer.toString(status)+" WHERE id="+volid+";";

        Statement stmt = null;
        try {
            if (_conn == null) connect();
            stmt = _conn.createStatement();
            stmt.executeUpdate(sql);
        }
        catch (SQLException ex) {
            throw new InventoryException("Failed to update status of volume " +
                                         volname + ": " + ex.getMessage(), ex);
        }
        finally {
            try { if (stmt != null) stmt.close(); }
            catch (SQLException ex) { }
        }
    }

    /**
     * get the current status of a registered volume.  Recognized values are defined in the 
     * {@link gov.nist.oar.cachemgr.VolumeStatus} interface; other application-specific values 
     * are allowed. 
     * @throws InventoryException  if there is an error accessing the underlying database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public int getVolumeStatus(String volname) throws InventoryException {
        String sql = "SELECT status FROM volumes WHERE name='"+volname+"';";

        Statement stmt = null;
        try {
            if (_conn == null) connect();
            stmt = _conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            if (! rs.next())
                throw new VolumeNotFoundException(volname);
            return rs.getInt("status");
        }
        catch (SQLException ex) {
            throw new InventoryException("Failed to pull info for volume "+volname+
                                         ": "+ex.getMessage(), ex);
        }
        finally {
            try { if (stmt != null) stmt.close(); }
            catch (SQLException ex) { }
        }
    }

    /**
     * return the amount of available (unused) space in the specified volume, in bytes
     * @throws InventoryException  if there is an error accessing the underlying database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public long getAvailableSpaceIn(String volname) throws InventoryException {
        // This will throw a VolumeNotFoundException if the volume is not registered
        JSONObject md = getVolumeInfo(volname);
        
        String sum_sql =
            "SELECT v.name as volume, min(v.capacity)-sum(d.size) as size FROM objects d, volumes v "+
            "WHERE v.name='"+volname+"' and d.volume=v.id and d.cached=1 GROUP BY v.name";
        try {
            return _get_sum(sum_sql).get(volname).longValue();
        }
        catch (NullPointerException ex) {
            // there are no records in the database for this volume; i.e., it's empty
            return getMetadatumLong(md, "capacity", 0L);
        }
    }

    /**
     * return the amount of available (unused) space in the specified volume, in bytes
     */
    public Map<String, Long> getAvailableSpace() throws InventoryException {
        String sum_sql =
            "SELECT v.name as volume, min(v.capacity)-sum(d.size) as size FROM objects d, volumes v "+
            "WHERE d.volume=v.id and d.cached=1 GROUP BY v.name";
        return _get_sum(sum_sql);
    }

    /**
     * return the amount of space currently being used in each of the volumes, in bytes.  
     * This is the sum of the sizes of all data objects and reservations currently in each
     * volume.  
     */
    public Map<String, Long> getUsedSpace() throws InventoryException {
        String sum_sql =
            "SELECT v.name as volume, sum(d.size) as size FROM objects d, volumes v "+
            "WHERE d.volume=v.id and d.cached=1 GROUP BY v.name";
        return _get_sum(sum_sql);
    }

    private Map<String, Long> _get_sum(String sql) throws InventoryException {
        Statement stmt = null;
        try {
            if (_conn == null) connect();
            stmt = _conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            HashMap<String, Long> out = new HashMap<String, Long>(20);
            String name = null;
            while (rs.next()) {
                name = rs.getString(1);
                if (name != null)
                    out.put(name, new Long(rs.getLong(2)));
            }
            return out;
        }
        catch (SQLException ex) {
            throw new InventorySearchException(ex);
        }
        finally {
            try { if (stmt != null) stmt.close(); }
            catch (SQLException ex) {  }
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
