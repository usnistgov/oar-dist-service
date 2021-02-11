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

import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.SelectionStrategy;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.InventorySearchException;
import gov.nist.oar.distrib.cachemgr.InventoryMetadataException;
import gov.nist.oar.distrib.cachemgr.VolumeNotFoundException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.inventory.JDBCStorageInventoryDB;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONTokener;
import org.json.JSONException;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.File;
import java.util.List;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;


/**
 * an extension of the JDBC-based implementation of a 
 * {@link gov.nist.oar.distrib.cachemgr.StorageInventoryDB} database, 
 * {@link gov.nist.oar.distrib.cachemgr.inventory.JDBCStorageInventoryDB} to support specifically 
 * PDR data objects.  It adds two additional columns to the object data mode:  <code>pdrid</code>
 * and <code>ediid</code>.  They allow objects to be found via either of these identifiers which may 
 * be different from the AIP identifier (stored in the <code>id</code> column).  This class, thus, adds 
 * two additional methods for selecting objects based on these identifiers.
 * <p>
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
 *    priority  integer NOT NULL,
 *    volume    integer FOREIGN KEY,
 *    name      text NOT NULL,
 *    since     integer NOT NULL,
 *    checked   integer NOT NULL,
 *    cached    boolean NOT NULL DEFAULT 0,
 *    pdrid     text,
 *    ediid     text,
 *    metadata  text
 * );
 * </pre>
 * <p>
 * To create an instance of this class (which is abstract), one should use the static factory function
 * {@link #createSQLiteDB(String)}.
 */
public abstract class PDRStorageInventoryDB extends JDBCStorageInventoryDB implements PDRConstants {

    /**
     * create an inventory database around a database accessible via a given JDBC URL.  
     * It is assumed that a JDBC driver for the database exists in the Java CLASSPATH.  
     * The value of {@link #defaultDeletionPlanSelect} will be loaded as the SQL SELECT 
     * will be used to select deletable objects from a data volume (see 
     * {@link JDBCStorageInventoryDB#JDBCStorageInventoryDB(String,String)}).
     * 
     * @param dburl    the JDBC URL to use to connect to the existing database.  
     */
    protected PDRStorageInventoryDB(String dburl) {
        super(dburl);

        add_sql = "INSERT INTO objects(" +
            "objid,name,size,checksum,algorithm,priority,volume,since,checked,cached,pdrid,ediid,metadata" +
            ") VALUES (?,?,?,?,?,?,?,?,0,?,?,?,?)";        
    }

    /*
     * load metadata stored in columns in the given search result into a JSONObject.  This is called 
     * by {@link #extractObject(ResultSet)} to export a row as a 
     * {@link gov.nist.oar.distrib.cachemgr.CacheObject}; it can be overridden by subclasses to add
     * additional metadata that are not part of the base data model for this class.  
    @Override
    protected JSONObject metadataToJSON(ResultSet rs) throws SQLException, InventoryException {
        JSONObject out = super.metadataToJSON(rs);

        // add extra properties
        String datum = rs.getString("pdrid");
        if (datum != null)
            out.put("pdrid", datum);
        datum = rs.getString("ediid");
        if (datum != null)
            out.put("pdrid", datum);
        return out;
    }
     */

    private void quietDisconnect(Connection conn) {
        try { disconnect(conn); } catch (SQLException ex) { }
    }
    private JSONObject copy(JSONObject jo) {
        return new JSONObject(jo, JSONObject.getNames(jo));
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
     * record the addition of an object to a volume.  The metadata stored with the 
     * object can vary by application.  
     * @param id       the identifier for the object being stored
     * @param volname  the name of the volume where the object was added
     * @param objname  the name of the object was given in that volume
     * @param metadata the metadata to be associated with that object (can be null)
     * @return CacheObject  a {@link gov.nist.oar.distrib.cachemgr.CacheObject} instance reflecting the 
     *                 object information saved to the database.  Note that the returned instance's 
     *                 <code>volname</code> field will be set buth its <code>volume</code> field will 
     *                 be null.
     * @throws InventoryException  if a problem occurs while interacting with the inventory database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    @Override
    public synchronized CacheObject addObject(String id, String volname, String objname, JSONObject metadata)
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
        String pdrid = null, ediid = null;

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
                nm = "pdrid";
                pdrid = getMetadatumString(metadata, nm, pdrid);
                nm = "ediid";
                ediid = getMetadatumString(metadata, nm, ediid);
            }
            catch (JSONException ex) {
                throw new InventoryMetadataException(nm + ": Metadatum has unexpected type: " + 
                                                     ex.getMessage(), nm, ex);
            }
        }
        else {
            // create a metadata object for output
            metadata = new JSONObject();
            metadata.put("size", size);
            metadata.put("priority", priority);
            metadata.put("since", since.toEpochMilli());
            metadata.put("sinceDate", ZonedDateTime.ofInstant(since, ZoneOffset.UTC)
                                                   .format(DateTimeFormatter.ISO_INSTANT));
        }
        
        int algid = getAlgorithmID(alg);
        if (algid < 0)
            throw new InventoryException("Not a registered algorithm: " + alg);

        // check to see if we have this record in the database already
        StringBuilder sb = new StringBuilder(find_sql_base);
        sb.append("AND v.name='").append(volname);
        sb.append("' AND d.name='").append(objname).append("';");
        List<CacheObject> found = queryForObjects(sb.toString());
        for(CacheObject co : found)
            // remove these entries with the same name
            removeObject(co.volname, co.name);

        // add the new object name to the database
        Connection conn = null;
        PreparedStatement stmt = null;
        try {
            conn = connect();
            stmt = conn.prepareStatement(add_sql);
            stmt.setString(1, id);
            stmt.setString(2, objname);
            stmt.setLong(3, size);
            stmt.setString(4, csum);
            stmt.setInt(5, algid);
            stmt.setInt(6, priority);
            stmt.setInt(7, volid);
            stmt.setLong(8, since.toEpochMilli());
            stmt.setBoolean(9, true);
            stmt.setString(10, pdrid);
            stmt.setString(11, ediid);
            stmt.setString(12, jmd);
            
            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            throw new InventoryException("Failed to register object " + id + ": " + ex.getMessage(), ex);
        }
        finally {
            try { if (stmt != null) stmt.close(); } catch (SQLException ex) {  }
            try { disconnect(conn); } catch (SQLException ex) {} 
        }

        return new CacheObject(objname, metadata, volname);
    }


    /**
     * append SET subclauses to the SQL UPDATE statement that will be used to update a 
     * record's metadata.  
     * <p>
     * This method can be overriden to customize the appending of that data.  In particular,
     * a subclass should call <code>super.setUpdateMetadataStmt()</code> to set data for the 
     * base data model and then append an additional properties for the extended model.  
     * @param stmt       the update statement so far to append key=value subclauses to
     * @param metadata   the metadata to update for the target record.
     */
    protected void setUpdateMetadataStmt(StringBuilder stmt, JSONObject metadata)
        throws InventoryMetadataException
    {
        String nm = null;
        super.setUpdateMetadataStmt(stmt, metadata);
        try {
            if (metadata.has("pdrid")) {
                nm = "pdrid";
                stmt.append(" pdrid='").append(metadata.getString(nm)).append("'").append(",");
            }
            if (metadata.has("ediid")) {
                nm = "ediid";
                stmt.append(" ediid='").append(metadata.getString(nm)).append("'").append(",");
            }
        } catch (JSONException ex) {
            throw new InventoryMetadataException(nm + ": Metadatum has unexpected type: " + 
                                                 ex.getMessage(), nm, ex);
        }
    }

    /**
     * return a list of cache objects having a given PDR resource identifier
     * @param purpose  an integer indicating the purpose for locating the object.  Recognized 
     *                 values are defined in the {@link gov.nist.oar.distrib.cachemgr.VolumeStatus} interface.
     * @return List<CacheObject>  the copies of the object in the cache.  Each element represents
     *                             a copy in a different cache volume.
     * @throws InventoryException  if there is an error accessing the underlying database.
     */
    public List<CacheObject> selectObjectsByPDRID(String pdrid, int purpose) throws InventoryException {

        StringBuilder sql = new StringBuilder(find_sql_base);
        sql.append("AND d.pdrid='").append(pdrid).append("' AND v.status >= ").append(purpose);
        if (purpose >= VOL_FOR_GET)
            sql.append(" AND d.cached=1");
        sql.append(";");

        // lock access to the db in case a deletion plan is progress, unless the caller just
        // wants information. 
        Object lock = (purpose >= VOL_FOR_GET) ? this : new Object();
        synchronized (lock) {
            return queryForObjects(sql.toString());
        }
    }

    /**
     * return a list of cache objects having a given EDI (Enterprise Data Inventory) resource identifier
     * @param purpose  an integer indicating the purpose for locating the object.  Recognized 
     *                 values are defined in the {@link gov.nist.oar.distrib.cachemgr.VolumeStatus} interface.
     * @return List<CacheObject>  the copies of the object in the cache.  Each element represents
     *                             a copy in a different cache volume.
     * @throws InventoryException  if there is an error accessing the underlying database.
     */
    public List<CacheObject> selectObjectsByEDIID(String ediid, int purpose) throws InventoryException {
        StringBuilder sql = new StringBuilder(find_sql_base);
        sql.append("AND d.ediid='").append(ediid).append("' AND v.status >= ").append(purpose);
        if (purpose >= VOL_FOR_GET)
            sql.append(" AND d.cached=1");
        sql.append(";");

        // lock access to the db in case a deletion plan is progress, unless the caller just
        // wants information. 
        Object lock = (purpose >= VOL_FOR_GET) ? this : new Object();
        synchronized (lock) {
            return queryForObjects(sql.toString());
        }
    }

    /**
     * return a list of cache objects having cache identifiers matching a particular pattern
     * @param idpat    an SQL text pattern to look for.  This value should include '%' wildcard characters
     *                 as needed
     * @param purpose  an integer indicating the purpose for locating the object.  Recognized 
     *                 values are defined in the {@link gov.nist.oar.distrib.cachemgr.VolumeStatus} interface.
     * @return List<CacheObject>  the copies of the object in the cache.  Each element represents
     *                             a copy in a different cache volume.
     * @throws InventoryException  if there is an error accessing the underlying database.
     */
    protected List<CacheObject> selectObjectsLikeID(String idpat, int purpose) throws InventoryException {
        StringBuilder sql = new StringBuilder(find_sql_base);
        sql.append("AND d.objid LIKE '").append(idpat).append("' AND v.status >= ").append(purpose);
        if (purpose >= VOL_FOR_GET)
            sql.append(" AND d.cached=1");
        sql.append(";");

        // lock access to the db in case a deletion plan is progress, unless the caller just
        // wants information. 
        Object lock = (purpose >= VOL_FOR_GET) ? this : new Object();
        synchronized (lock) {
            return queryForObjects(sql.toString());
        }
    }

    /**
     * return information about the current usage of the named volume, including total 
     * number of files and bytes stored in that volume.
     * <p>
     * The returned JSONObject includes the following properties:
     * <dl>
     * <dd> <b>name</b> -- the name of the volume (the same as was requested) </dd>
     * <dd> <b>filecount</b> -- the total number of files currently cached in the volume </dd>
     * <dd> <b>totalsize</b> -- the total number of bytes currently cached in the volume </dd>
     * <dd> <b>since</b> -- the last time since a file was accessed in this volume, in epoch seconds </dd>
     * <dd> <b>sinceDate</b> -- the last time since a file was accessed in this volume, 
     *      formatted as a String </dd>
     * <dd> <b>checked</b> -- the last time since a file was checked for integrity in this volume, 
     *      in epoch seconds </dd>     
     * <dd> <b>checkedDate</b> -- the last time since a file was checked for integrity in this volume, 
     *      formatted as a String </dd> 
     * </dl>
     * This method returns an object even if the volume is empty.
     * @return JSONObject -- the collection of volume properties (see above).  
     * @throws VolumeNotFoundException  if the named volume is not found in this inventory
     * @throws InventoryException   if there is an error accessing this database
     */
    public JSONObject getVolumeTotals(String volname) throws VolumeNotFoundException, InventoryException {
        // look up volume ID
        int vid = getVolumeID(volname);
        if (vid < 0)
            throw new VolumeNotFoundException(volname);

        Connection conn = null;
        Statement stmt = null;
        ResultSet res = null;
        try {
            conn = connect();
            
            JSONObject out = new JSONObject();
            out.put("name", volname);
            out.put("filecount", 0);
            out.put("totalsize", 0L);
            out.put("since", 0L);
            out.put("checked", 0L);

            // calculate totals for files having that volume ID
            String qsel = "SELECT count(*) as count,sum(size) as totsz,max(since) as newest," +
                          "min(checked) as oldest FROM objects WHERE cached=1 AND volume=" +vid;
            stmt = conn.createStatement();
            res = stmt.executeQuery(qsel);

            if (res.next() && res.getInt("count") > 0) {
                out.put("filecount", res.getInt("count"));
                out.put("totalsize", res.getLong("totsz"));
                out.put("since",     res.getLong("newest"));
                out.put("checked",   res.getLong("oldest"));
            }
            out.put("sinceDate", ZonedDateTime.ofInstant(Instant.ofEpochMilli(out.optLong("since", 0L)),
                                                         ZoneOffset.UTC)
                                              .format(DateTimeFormatter.ISO_INSTANT));
            if (out.optLong("checked", 0L) > 0L)
                out.put("checkedDate", ZonedDateTime.ofInstant(Instant.ofEpochMilli(out.optLong("oldest",0L)),
                                                               ZoneOffset.UTC)
                                                    .format(DateTimeFormatter.ISO_INSTANT));
            else
                out.put("checkedDate", "(never)");

            return out;
        }
        catch (SQLException ex) {
            throw new InventorySearchException(ex);
        }
        finally {
            try { if (res != null) res.close(); } catch (SQLException ex) { }
            try { if (stmt != null) stmt.close(); } catch (SQLException ex) { }
            quietDisconnect(conn);
        }
    }

    /**
     * return a summary of the set of files from a particular dataaset currently cached in the inventory.
     * <p>
     * the returned <code>JSONObject</code> will in include the following stats:
     * <ul>
     *   <li> "filecount" -- the number of files from the dataset currently in the volume </li>
     *   <li> "totalsize" -- the total number bytes of all files from the dataset stored in the volume </li>
     *   <li> "since" -- the last date-time a file from the dataset in the cache was accessed or added, 
     *            in epoch msecs </li>
     *   <li> "sinceDate"  -- the last date-time a file from the dataset in the cache was accessed or added, 
     *            as an ISO string </li>
     *   <li> "checked" -- the oldest date-time that a file from the dataset was last check for integrity 
     *            (checksum), in epoch msecs </li>
     *   <li> "checkedDate" -- the oldest date-time that a file from the dataset was last check for 
     *            integrity (checksum), as an ISO string </li>
     * </ul>
     * @param aipid    the AIP identifier for the dataset
     * @return JSONObject -- the collection of volume properties (see above), or null if no files with 
     *         the given AIP ID are currently cached in the inventory.
     * @throws InventoryException   if there is an error accessing this database
     */
    public JSONObject summarizeDataset(String aipid) throws InventoryException {
        StringBuilder qsel = new StringBuilder();
        qsel.append("SELECT d.ediid,count(*) as count,sum(d.size) as totsz,max(d.since) as newest,")
            .append("min(d.checked) as oldest FROM objects d, volumes v ")
            .append("WHERE d.volume=v.id AND d.cached=1 AND v.name!='old' AND d.objid LIKE '")
            .append(aipid).append("/%' GROUP BY d.ediid");

        Connection conn = null;
        Statement stmt = null;
        ResultSet res = null;
        try {
            conn = connect();

            stmt = conn.createStatement();
            res = stmt.executeQuery(qsel.toString());
            if (! res.next())
                return null;
            return extractDatasetInfo(res);
        }
        catch (SQLException ex) {
            throw new InventorySearchException(ex);
        }
        finally {
            try { if (res != null) res.close(); } catch (SQLException ex) { /* try ignoring */ }
            try { if (stmt != null) stmt.close(); } catch (SQLException ex) { }
            quietDisconnect(conn);
        }
    }

    /**
     * provide a summary of the contents of the cache by aipid.  Each object in the returned array
     * summarizes a different AIP with the same properties as returned by {@link #summarizeDataset(String)}.
     * @param volname   the name of the volume to restrict results to; if null, results span across volumes
     * @return JSONArray -- the list of dataset summaries for datasets for which there are at least one 
     *         file cached in the inventory.
     * @throws InventoryException   if there is an error accessing this database
     */
    public JSONArray summarizeContents(String volname) throws InventoryException {
        String qsel = "SELECT d.ediid,count(*) as count,sum(d.size) as totsz,max(d.since) as newest," +
                      "min(d.checked) as oldest FROM objects d, volumes v WHERE d.volume=v.id AND d.cached=1";
        if (volname != null) 
            qsel += " AND v.name='" + volname + "'";
        else
            qsel += " AND v.name!='old'";
        qsel += " GROUP BY d.ediid ORDER BY oldest";

        Connection conn = null;
        Statement stmt = null;
        ResultSet res = null;
        try {
            conn = connect();
            
            JSONArray out = new JSONArray();
            JSONObject row = null;
            stmt = conn.createStatement();
            res = stmt.executeQuery(qsel);
            while (res.next()) {
                row = extractDatasetInfo(res);
                out.put(row);
            }

            return out;
        }
        catch (SQLException ex) {
            throw new InventorySearchException(ex);
        }
        finally {
            try { if (res != null) res.close(); } catch (SQLException ex) { }
            try { if (stmt != null) stmt.close(); } catch (SQLException ex) { }
            quietDisconnect(conn);
        }
    }

    JSONObject extractDatasetInfo(ResultSet res) throws SQLException {
        JSONObject row = new JSONObject();
        String aipid = PDR_ARK_PAT.matcher(res.getString("ediid")).replaceFirst("");
        row.put("aipid",     aipid);
        row.put("filecount", res.getInt("count"));
        row.put("totalsize", res.getLong("totsz"));
        row.put("since",     res.getLong("newest"));
        row.put("sinceDate", ZonedDateTime.ofInstant(Instant.ofEpochMilli(res.getLong("newest")),
                                                     ZoneOffset.UTC)
                                          .format(DateTimeFormatter.ISO_INSTANT));

        long timems = res.getLong("oldest");
        row.put("checked",   timems);
        if (timems == 0L)
            row.put("checkedDate", "(never)");
        else
            row.put("checkedDate", ZonedDateTime.ofInstant(Instant.ofEpochMilli(res.getLong("oldest")),
                                                           ZoneOffset.UTC)
                                                .format(DateTimeFormatter.ISO_INSTANT));
        return row;
    }

    /**
     * create an instance of of this class connected to an SQLite database file
     */
    public static PDRStorageInventoryDB createSQLiteDB(String filepath) {
        class SQLitePDRSIDB extends PDRStorageInventoryDB {
            protected File dbfile = null;
            SQLitePDRSIDB(String filepath) {
                // we're allowing the file path to include the jdbc URL prefix.
                super( (filepath.startsWith("jdbc:sqlite:")) ? filepath : "jdbc:sqlite:"+filepath );
                dbfile = new File((filepath.startsWith("jdbc:sqlite:"))
                                  ? filepath.substring("jdbc:sqlite:".length())
                                  : filepath);
            }
            @Override
            protected Connection connect() throws SQLException {
                if (! dbfile.isFile())
                    throw new SQLException("Missing SQLite db file: "+dbfile.toString());
                return super.connect();
            }
        }

        return new SQLitePDRSIDB(filepath);
    }

    /**
     * create an instance of of this class connected to an SQLite database file
     */
    public static void initializeSQLiteDB(String filepath) throws InventoryException {
        // load the sql script from a resource
        Class thiscl = PDRStorageInventoryDB.class;
        BufferedReader rdr = null;
        StringBuilder sb = new StringBuilder();
        try {
            rdr = new BufferedReader(new InputStreamReader(thiscl.getResourceAsStream("res/pdr_sqlite_create.sql")));
            
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
}
