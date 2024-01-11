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

import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.cachemgr.BasicCache;
import gov.nist.oar.distrib.cachemgr.BasicCacheManager;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.RestorationException;
import gov.nist.oar.distrib.cachemgr.VolumeNotFoundException;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.InventorySearchException;
import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.VolumeStatus;

import java.util.List;
import java.util.Set;
import java.util.Iterator;

import org.json.JSONObject;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A CacheManager that can cache whole PDR datasets at a time (via {@link cacheDataset}).  This is done
 * via a PDR-aware restorer.
 * <p>
 * This class has two purposes.  The first is to serve as a base class to the more expansive 
 * {@link PDRCacheManager} that serves as the primary {@link gov.nist.distrib.cachemgr.CacheManager} for 
 * the distribution service.  The second is to provide a PDR-aware cache manager that can cache whole 
 * datasets; this allows it to serve as a special {@link gov.nist.distrib.cachemgr.CacheManager} for 
 * use with restricted public data.  
 * <p>
 * @see PDRDatasetRestorer 
 */
public class PDRDatasetCacheManager extends BasicCacheManager implements VolumeStatus {

    protected Logger log = null;

    /**
     * create a CacheManager specialized for the PDR
     * @param cache          the cache to manage
     * @param restorer       the restorer to use to restore objects and datasets to the cache
     * @param logger         the Logger instance to use for log messages
     */
    public PDRDatasetCacheManager(BasicCache cache, PDRDatasetRestorer restorer, Logger logger) {
        super(cache, restorer);
        if (logger == null)
            logger = LoggerFactory.getLogger(getClass());
        log = logger;
    }

    /**
     * create a CacheManager specialized for the PDR
     * @param cache          the cache to manage
     * @param restorer       the restorer to use to restore objects and datasets to the cache
     */
    public PDRDatasetCacheManager(BasicCache cache, PDRDatasetRestorer restorer) {
        this(cache, restorer, null);
    }
    
    /**
     * return a set of caching preferences for an object with the given identifier and size
     * to be applied by {@link #cache(String)} when preferences are not specified.  Other internal 
     * processes may alter those preferences as more is learned about the object during restoration. 
     * The default set returned here is expected to reflect the specific cache manager implementation
     * and/or the configured internal cache.  
     * <p>
     * This implementation returns a preference set drawn from the {@link PDRCacheRoles} definitions
     * according to PDR conventions.  
     * @param id     the identifier for the object being cached
     * @param size   the size of the object in bytes; if negative, the size is not known
     * @return int -- an ANDed set of caching preferences, or zero if no preferences are applicable
     */
    public int getDefaultPreferencesFor(String id, long size) {
        return ((PDRDatasetRestorer) restorer).getPreferencesFor(id, size, -1);
    }

    /**
     * cache all of the files from the given dataset
     * @param dsid     the AIP identifier for the dataset; this is either the old-style EDI-ID or 
     *                   local portion of the PDR ARK identifier (e.g., <code>"mds2-2119"</code>).  
     * @param version  the desired version of the dataset or null for the latest version
     * @param recache  if false and a file is already in the cache, the file will not be rewritten;
     *                    otherwise, all current cached files from the dataset will be replaced with a 
     *                    fresh copy.
     * @param prefs    any ANDed preferences for how to cache the data (particularly, where).  
     * @param target   a prefix collection name to insert the data files into within the cache
     */
    public Set<String> cacheDataset(String dsid, String version, boolean recache, int prefs, String target)
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        return ((PDRDatasetRestorer) restorer).cacheDataset(dsid, version, theCache, recache, prefs, target);
    }

    /**
     * create a name for a data object within a particular {@link gov.nist.oar.distrib.cachemgr.CacheVolume}.  
     */
    protected String determineCacheObjectName(String volname, String id) throws CacheManagementException {
        int roles = getRolesFor(volname);
        return ((PDRDatasetRestorer) restorer).nameForObject(id, roles);
    }

    int getRolesFor(String volname) {
        StorageInventoryDB sidb = ((BasicCache) theCache).getInventoryDB();
        int roles = 0;
        try {
            JSONObject md = sidb.getVolumeInfo(volname);
            roles = md.optInt("roles");
        }
        catch (VolumeNotFoundException ex) {
            log.error("Trouble getting roles: volume, {}, not registered ({})", volname, ex.getMessage());
            // keep default values
        }
        catch (InventoryException ex) {
            log.error("Trouble getting roles assigned to volume, {}: {}", volname, ex.getMessage());
            // keep default values
        }
        return roles;
    }

    /**
     * return a list of objects known to the cache that are part of the dataset having the given AIP dataset 
     * id.  The cache knows about an object if the object is currently in the cache or has once been in the 
     * cache.  The returned list may include different versions of a file.  
     * @param dsid     the AIP id for the dataset; this is either the old-style EDI-ID or 
     *                   local portion of the PDR ARK identifier (e.g., <code>"mds2-2119"</code>).  
     * @param status   A {@link gov.nist.oar.distrib.cachemgr.VolumeStatus} value indicating the status 
     *                   of the desired objects.  In particular, specify...
     *                 <ul>
     *                    <li> {@link gov.nist.oar.distrib.cachemgr.VolumeStatus#VOL_FOR_INFO VOL_FOR_INFO} 
     *                         for objects that have ever been in the cache (but may not now be), </li>
     *                    <li> {@link gov.nist.oar.distrib.cachemgr.VolumeStatus#VOL_FOR_INFO VOL_FOR_GET} 
     *                         for objects that are currently in the cache, </li>
     *                    <li> {@link gov.nist.oar.distrib.cachemgr.VolumeStatus#VOL_FOR_INFO VOL_FOR_UPDATE} 
     *                         for objects that can be removed, recached, or have their status updated 
     *                         (like the last time it was checked).</li>
     *                 </ul>
     */
    public List<CacheObject> selectDatasetObjects(String dsid, int status) throws CacheManagementException {
        PDRStorageInventoryDB sidb = getInventoryDB();
        List<CacheObject> matched = sidb.selectObjectsLikeID(dsid+"/%", status);
        CacheObject co = null;
        for (Iterator<CacheObject> it = matched.iterator(); it.hasNext();) {
            co = it.next();
            co.volume = theCache.getVolume(co.volname);
        }
        return matched;
    }

    /**
     * return a list of objects representing particular files from a dataset having the given AIP dataset 
     * id.  The cache knows about an object if the object is currently in the cache or has once been in the 
     * cache.  The different objects in the returned list can represent copies of the files in different 
     * volumes or different versions of the file.  
     * @param dsid     the AIP id for the dataset; this is either the old-style EDI-ID or 
     *                   local portion of the PDR ARK identifier (e.g., <code>"mds2-2119"</code>).  
     * @param filepath the filepath identifying the particular file of interest.  
     * @param status   A {@link gov.nist.oar.distrib.cachemgr.VolumeStatus} value indicating the status 
     *                   of the desired objects.  In particular, specify...
     *                 <ul>
     *                    <li> {@link gov.nist.oar.distrib.cachemgr.VolumeStatus#VOL_FOR_INFO VOL_FOR_INFO} 
     *                         for objects that have ever been in the cache (but may not now be), </li>
     *                    <li> {@link gov.nist.oar.distrib.cachemgr.VolumeStatus#VOL_FOR_INFO VOL_FOR_GET} 
     *                         for objects that are currently in the cache, </li>
     *                    <li> {@link gov.nist.oar.distrib.cachemgr.VolumeStatus#VOL_FOR_INFO VOL_FOR_UPDATE} 
     *                         for objects that can be removed, recached, or have their status updated 
     *                         (like the last time it was checked).</li>
     *                 </ul>
     */
    public List<CacheObject> selectFileObjects(String dsid, String filepath, int status)
        throws CacheManagementException
    {
        PDRStorageInventoryDB sidb = getInventoryDB();
        String srchid = dsid + "/" + filepath;
        List<CacheObject> matched = sidb.selectObjectsLikeID(srchid, status);
        srchid += "#%";
        List<CacheObject> old = sidb.selectObjectsLikeID(srchid, status);
        for (CacheObject co : old)
            matched.add(co);

        CacheObject co = null;
        for (Iterator<CacheObject> it = matched.iterator(); it.hasNext();) {
            co = it.next();
            co.volume = theCache.getVolume(co.volname);
        }
        return matched;
    }

    /**
     * return a CacheObject description of an object in the inventory database or null if the object is 
     * not in the dataset (with the specified status).  
     * @param aipid   the full AIP ID for the dataset or object (of the form DSID[/FILEPATH]#[VERSION])
     * @param status  the required minimal status of the object.  This should be a 
     *                {@link gov.nist.oar.distrib.cachemgr.VolumeStatus} value indicating the desired status 
     *                   of the object.  In particular, null is returned when <code>status</code> is...
     *                 <ul>
     *                    <li> {@link gov.nist.oar.distrib.cachemgr.VolumeStatus#VOL_FOR_INFO VOL_FOR_INFO} 
     *                         and the object has never been in the cache, </li>
     *                    <li> {@link gov.nist.oar.distrib.cachemgr.VolumeStatus#VOL_FOR_INFO VOL_FOR_GET} 
     *                         and the object is not currently in the cache, </li>
     *                    <li> {@link gov.nist.oar.distrib.cachemgr.VolumeStatus#VOL_FOR_INFO VOL_FOR_UPDATE} 
     *                         and the object is locked from being removed, recached, or having its status 
     *                         updated (like the last time it was checked).</li>
     *                 </ul>     
     */
    public CacheObject describeObject(String aipid, String filepath, int status) throws InventoryException {
        PDRStorageInventoryDB sidb = getInventoryDB();
        List<CacheObject> cos = sidb.findObject(aipid+"/"+filepath, status);
        if (cos.size() == 0)
            return null;
        if (cos.size() == 1)
            return cos.get(0);

        // if it is listed multiple times, pick the one latest entry
        long latest = 0;
        CacheObject out = cos.get(0);
        long since = 0L;
        for (CacheObject co : cos) {
            since = co.getMetadatumLong("since", 0L);
            if (since > latest) {
                latest = since;
                out = co;
            }
        }
        return out;
    }

    /**
     * provide a summary of the state of a particular volume in the cache.  
     * <p>
     * The returned <code>JSONObject</code> describes the volume via properties that  
     * include its static metadata (link "name" and "capacity") as well as the 
     * following status information:
     * <ul>
     *   <li> "filecount" -- the number of files currently in the volume </li>
     *   <li> "totalsize" -- the total number bytes stored in the volume </li>
     *   <li> "since" -- the last date-time an object in the cache was accessed or added, in epoch msecs </li>
     *   <li> "sinceDate"  -- the last date-time an object in the cache was accessed or added, as an 
     *            ISO string </li>
     *   <li> "checked" -- the oldest date-time that an object was last check for integrity (checksum),
     *            in epoch msecs </li>
     *   <li> "checkedDate" -- the oldest date-time that an object was last check for integrity (checksum),
     *            as an ISO string </li>
     * </ul>
     * @param name    the name of the volume to return the summary for
     */
    public JSONObject summarizeVolume(String name) throws VolumeNotFoundException, InventoryException {
        PDRStorageInventoryDB db = getInventoryDB();
        JSONObject out = db.getVolumeInfo(name);
        JSONObject totals = db.getVolumeTotals(name);
        out.put("name", name);

        for (String prop : JSONObject.getNames(totals))
            out.put(prop, totals.get(prop));

        return out;
    }

    protected PDRStorageInventoryDB getInventoryDB() {
        return (PDRStorageInventoryDB) ((BasicCache) theCache).getInventoryDB();
    }

    /**
     * provide a summary the state of the volumes that are in the cache.  
     * <p>
     * Each element in the returned array is a <code>JSONObject</code> describing a volume in the cache.  
     * The object properties provided are those returned by {@link #summarizeVolume(String)}.
     */
    public JSONArray summarizeVolumes() throws InventoryException {
        PDRStorageInventoryDB sidb = getInventoryDB();
        JSONArray out = new JSONArray();
        for(String vname : sidb.volumes())
            out.put(summarizeVolume(vname));
        return out;
    }

    /**
     * return a summary of the set of files from a particular dataaset currently in the cache.
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
     */
    public JSONObject summarizeDataset(String aipid) throws InventoryException {
        return getInventoryDB().summarizeDataset(aipid);        
    }

    /**
     * provide a summary of the contents of the cache by aipid.  Each object in the returned array
     * summarizes a different AIP with the same properties as returned by {@link #summarizeDataset(String)}.
     * @param volname   the name of the volume to restrict results to; if null, results span across volumes
     */
    public JSONArray summarizeContents(String volname) throws InventoryException {
        return getInventoryDB().summarizeContents(volname);
    }
}

