/*
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
package gov.nist.oar.cachemgr;

import java.util.List;
import java.util.HashMap;
import java.util.Deque;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.json.JSONObject;
import org.json.JSONException;

/**
 * a basic implementation of a {@link gov.nist.oar.cachemgr.Cache}.  
 *
 * This implementation holds a set of {@link gov.nist.oar.cachemgr.CacheVolume}s whose contents 
 * are tracked via a {@link gov.nist.oar.cachemgr.StorageInventoryDB}, all of which are provided
 * at construction time.  The CacheVolumes are treated equally (though they might have different 
 * capacities).  This class makes use of the database model of the JDBCStorageInventoryDB implementation;
 * however it does not require it.
 */
public class BasicCache extends Cache {

    /**
     * the list of volumes that can store data.  Note that implementations are allowed to 
     * manipulate the order or contents of the volumes.  
     */
    protected HashMap<String, CacheVolume> volumes = null;

    /**
     * the inventory database that tracks the contents of these volumes.  
     */
    protected StorageInventoryDB db = null;

    private Deque<String> recent = null;
    private Logger log = null;

    /**
     * create the Cache without volumes.  The provided inventory database should be empty of 
     * object records and with no volumes registered.  To create a Cache with a prepopulated 
     * database, use 
     * {@link gov.nist.oar.cachemgr.BasicCache#BasicCache(StorageInventoryDB,List)}.
     * 
     * @param idb         the (empty) inventory database to use
     */
    public BasicCache(StorageInventoryDB idb) {
        this(idb, 2, null);
    }

    /**
     * create the Cache without volumes.  The provided inventory database should be empty of 
     * object records and with no volumes registered.  To create a Cache with a prepopulated 
     * database, use 
     * {@link gov.nist.oar.cachemgr.BasicCache#BasicCache(StorageInventoryDB,List)}.
     * 
     * @param idb   the (empty) inventory database to use
     * @param log   a particular Logger instance that should be used.  If null, a default one
     *                will be provided.  
     */
    public BasicCache(StorageInventoryDB idb, Logger log) {
        this(idb, 2, log);
    }

    /**
     * create the Cache without volumes.  The provided inventory database should be empty of 
     * object records and with no volumes registered.  To create a Cache with a prepopulated 
     * database, use 
     * {@link gov.nist.oar.cachemgr.BasicCache#BasicCache(StorageInventoryDB,List)}.
     * @param idb         the inventory database to use
     * @param volcount    the expected number of CacheVolumes that will be attached via addVolume()
     * @param log   a particular Logger instance that should be used.  If null, a default one
     *                will be created.  
     */
    public BasicCache(StorageInventoryDB idb, int volcount, Logger log) {
        db = idb;
        volumes = new HashMap<String, CacheVolume>(volcount);
        recent = new LinkedList<String>();
        this.log = log;
        if (this.log == null) log = LoggerFactory.getLogger(this.getClass());
    }

    /**
     * create the Cache around pre-popluated volumes and inventory database.  This constructor is 
     * for recreating an instance of a Cache around previously persisted volumes and associated 
     * database.  Thus, the volumes and their contents should already be registered with the 
     * associated database.  
     * @param idb      the inventory database to use
     * @param vols     the CacheVolumes to attach to this cache
     */
    public BasicCache(StorageInventoryDB idb, List<CacheVolume> vols) {
        this(idb, vols, null);
    }

    /**
     * create the Cache around pre-popluated volumes and inventory database.  This constructor is 
     * for recreating an instance of a Cache around previously persisted volumes and associated 
     * database.  Thus, the volumes and their contents should already be registered with the 
     * associated database.  
     * @param idb      the inventory database to use
     * @param vols     the CacheVolumes to attach to this cache
     * @param log   a particular Logger instance that should be used.  If null, a default one
     *                will be created.  
     */
    public BasicCache(StorageInventoryDB idb, List<CacheVolume> vols, Logger log) {
        this(idb, vols.size(), log);
        for (CacheVolume v : vols) {
            volumes.put(v.getName(), v);
            recent.add(v.getName());   // we start with an arbitrary order.
        }
    }

    /**
     * return true if the data object with the given identifier is held in the cache.  This is 
     * done by searching for an entry in the inventory database.
     * @param id   the identifier for the data object of interest.
     * @throws InventoryException  if an error occurs while searching the inventory.
     */
    public boolean isCached(String id) throws CacheManagementException {
        return db.findObject(id).size() > 0;
    }

    /**
     * remove all copies of the data object with the given ID from the cache
     * @param id       the identifier for the data object of interest.
     * @throws InventoryException  if an error occurs while searching or updating the inventory.
     * @throws CacheVolumeException  if an error occurs removing the object from a volume
     */
    public void uncache(String id) throws CacheManagementException {
        List<CacheObject> cos = db.findObject(id);
        for (CacheObject co : cos) {
            if (volumes.containsKey(co.volname))
                volumes.get(co.volname).remove(co.name);
            db.removeObject(co.volname, co.name);
        }
    }

    /**
     * return a CacheObject representation of a data object if it already exists in the cache;
     * otherwise, return null. 
     * @param id       the identifier for the data object of interest.
     * @throws InventoryException  if an error occurs while searching or updating the inventory.
     * @throws CacheVolumeException  if an error occurs removing the object from a volume
     */
    public CacheObject findObject(String id) throws CacheManagementException {
        List<CacheObject> cos = db.findObject(id);
        if (cos.size() == 0)
            return null;

        boolean prob = false;
        CacheVolume vol = null;
        for (CacheObject co : cos) {
            try {
                vol = volumes.get(co.volname);
                if (vol != null && vol.exists(co.name))
                    return co;
            } catch (CacheVolumeException ex) {
                log.error("Trouble interacting with volume="+co.volname, ex);
                prob = true;
            }
        }

        log.error("Volumes appear out of sync with inventory: all found objects inaccessible.");
        if (prob)
            throw new ObjectNotFoundException(id+": object is inaccessible");

        log.info("Cleaning up the inventory: removing id="+id);
        uncache(id);
        return null;
    }

    /**
     * make the cache volume part of this cache.  The cache is expected to be empty.
     * @param vol       the CacheVolume to add to this Cache
     * @param capacity  the limit on the amount of space available for data objects
     * @param metadata  additional metadata about the volume.  Can be null.
     * @throws InventoryException  if a problem occurs while registering the volume with 
     *                  the inventory database.
     */
    public void addCacheVolume(CacheVolume vol, int capacity, JSONObject metadata)
        throws CacheManagementException
    {
        if (volumes.containsKey(vol.getName()))
            throw new InventoryException(vol.getName() +
                                         ": a volume with this name is already registered");
        volumes.put(vol.getName(), vol);
        recent.add(vol.getName());
        try {
            db.registerVolume(vol.getName(), capacity, metadata);
        } catch (InventoryException ex) {
            recent.removeLast();
            volumes.remove(vol.getName());
            throw ex;
        }
    }

    /**
     * return a reservation for a given amount of space.  
     * @param bytes        the amount of space to reserve
     * @param preferences  an and-ed set of bits indicating what the space will be used for.
     * @return Reservation   an object that provides a claim on space; its interface should be 
     *                     used to deliver the bytes into the cache when the object is available.  
     */
    public Reservation reserveSpace(long bytes, int preferences) throws CacheManagementException {
        return null;
    }
}
