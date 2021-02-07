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
import gov.nist.oar.bags.preservation.BagUtils;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONException;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.io.File;
import java.sql.SQLException;

/**
 * an extension of the {@link PDRStorageInventoryDB} class that adds specialized functionality to 
 * service a cache of head bags. 
 * <p>
 * The NIST Public Data Repository (PDR) preserves its data into a preservation format that consists of 
 * aggregations of files conforming the to the BagIt standard using the NIST PDR BagIt profile.  The 
 * profile itself is an extenstion of the more general Multibag Profile.  This latter profile defines the 
 * concept of a head bag that provides a directory for all data in the aggregation; in the PDR extension
 * profile, the complete metadata is also stored in the head bag.  In the PDR, preservation bag files are 
 * stored in an AWS S3 bucket which has some access overheads associated with it; thus, it is helpful to 
 * cache head bags on local disk for access to the metadata.  
 * <p>
 * This class uses the same database model as {@link PDRStorageInventoryDB}; it just adds additional 
 * methods for locating head bags in the cache.  
 * <p>
 * To create an instance of this class (which is abstract), one should use the static factory function
 * {@link #createHeadBagDB(String)}.
 */
public abstract class HeadBagDB extends PDRStorageInventoryDB {

    /**
     * create an inventory database around a database accessible via a given JDBC URL.  
     * It is assumed that a JDBC driver for the database exists in the Java CLASSPATH.  
     * The value of {@link #defaultDeletionPlanSelect} will be loaded as the SQL SELECT 
     * will be used to select deletable objects from a data volume (see 
     * {@link gov.nist.oar.distrib.cachemgr.inventory.JDBCStorageInventoryDB#JDBCStorageInventoryDB(String,String)}).
     * 
     * @param dburl    the JDBC URL to use to connect to the existing database.  
     */
    protected HeadBagDB(String dburl) {
        super(dburl);
    }

    /**
     * return a list of cache objects having a given EDI (Enterprise Data Inventory) resource identifier
     * @param purpose  an integer indicating the purpose for locating the object.  Recognized 
     *                 values are defined in the {@link gov.nist.oar.distrib.cachemgr.VolumeStatus} interface.
     * @return List<CacheObject>  the copies of the object in the cache.  Each element represents
     *                             a copy in a different cache volume.
     * @throws InventoryException  if there is an error accessing the underlying database.
     */
    public List<CacheObject> selectObjectsByAIPID(String aipid, int purpose) throws InventoryException {
        StringBuilder sql = new StringBuilder(find_sql_base);
        sql.append("AND d.objid LIKE '").append(aipid).append(".%' AND v.status >= ").append(purpose);
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
     * find a cached head bag for a given dataset and version
     * @param dsid    an identifier for the resource.  It can be the resource's primary PDR ID, and EDI-ID,
     *                  or its AIP-ID.  
     */
    public List<CacheObject> findHeadBag(String dsid, String version, int purpose) throws InventoryException {
        List<CacheObject> bags = null;
        if (dsid.startsWith("ark:/"))
            bags = selectObjectsByPDRID(dsid, purpose);
        else
            bags = selectObjectsByAIPID(dsid, purpose);

        List<String> bagnames = bags.stream().map(co -> co.id)
                                             .filter(id -> BagUtils.isLegalBagName(id))
                                             .collect(Collectors.toList());
        if (version != null)
            bagnames = BagUtils.selectVersion(bagnames, version);
        if (bagnames.size() == 0)
            return new ArrayList<CacheObject>();

        final String headbag = BagUtils.findLatestHeadBag(bagnames);
        return bags.stream().filter(co -> headbag.equals(co.id)).collect(Collectors.toList());
    }

    /**
     * find the latest head bag for a given dataset in the cache
     * @param dsid    an identifier for the resource.  It can be the resource's primary PDR ID, and EDI-ID,
     *                  or its AIP-ID.  
     */
    public List<CacheObject> findHeadBag(String dsid, int purpose) throws InventoryException {
        return findHeadBag(dsid, null, purpose);
    }

    /**
     * create an instance of of this class connected to an SQLite database file
     */
    public static PDRStorageInventoryDB createSQLiteDB(String filepath) {
        return createHeadBagDB(filepath);
    }

    /**
     * create an instance of of this class connected to an SQLite database file
     */
    public static HeadBagDB createHeadBagDB(String filepath) {
        class SQLiteHeadBagDB extends HeadBagDB {
            protected File dbfile = null;
            SQLiteHeadBagDB(String filepath) {
                // we're allowing the file path to include the jdbc URL prefix.
                super( (filepath.startsWith("jdbc:sqlite:")) ? filepath : "jdbc:sqlite:"+filepath );
                dbfile = new File((filepath.startsWith("jdbc:sqlite:"))
                                  ? filepath.substring("jdbc:sqlite:".length())
                                  : filepath);
            }
            @Override
            protected void connect() throws SQLException {
                if (! dbfile.isFile())
                    throw new SQLException("Missing SQLite db file: "+dbfile.toString());
                super.connect();
            }
        }

        return new SQLiteHeadBagDB(filepath);
    }
}
