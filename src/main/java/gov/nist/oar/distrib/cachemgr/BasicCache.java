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
package gov.nist.oar.distrib.cachemgr;

import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.storage.NullCacheVolume;

import java.util.List;
import java.util.Collection;
import java.util.Set;
import java.util.HashMap;
import java.util.Deque;
import java.util.LinkedList;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.json.JSONObject;
import org.json.JSONException;

/**
 * a basic abstract base implementation of a {@link gov.nist.oar.distrib.cachemgr.Cache}.  
 * <p>
 * This implementation holds a set of {@link gov.nist.oar.distrib.cachemgr.CacheVolume}s whose contents 
 * are tracked via a {@link gov.nist.oar.distrib.cachemgr.StorageInventoryDB}, all of which are provided
 * at construction time.  The CacheVolumes are treated equally (though they might have different 
 * capacities).  This class makes use of the database model of the JDBCStorageInventoryDB implementation;
 * however it does not require it.  Subclasses need only provide a {@link #getDeletionPlanner(int)} 
 * method.  
 * <p>
 * Because caches and their content are persistent, it is possible to provide a database that is not fully 
 * in sync with the state of the cache volumes.  In particular, all volumes recorded in the storage inventory
 * database may not be represented in the volumes attached to the cache at construction time; this 
 * implementation is sensitive to this possibility and will ignore those volumes.  
 */
public abstract class BasicCache extends Cache {

    /**
     * the list of volumes that can store data.  Note that implementations are allowed to 
     * manipulate the order or contents of the volumes.  
     */
    protected HashMap<String, CacheVolume> volumes = null;

    /**
     * the inventory database that tracks the contents of these volumes.  
     */
    protected StorageInventoryDB db = null;

    private Deque<String> recent = null;  // used to help distribute files across volumes
    protected Logger log = null;

    /**
     * create the Cache without volumes.  The provided inventory database should be empty of 
     * object records and with no volumes registered.  To create a Cache with a prepopulated 
     * database, use 
     * {@link #BasicCache(String,StorageInventoryDB,Collection)}.
     * 
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles, this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb    the (empty) inventory database to use
     */
    public BasicCache(String name, StorageInventoryDB idb) {
        this(name, idb, 2, null);
    }

    /**
     * create the Cache without volumes.  The provided inventory database should be empty of 
     * object records and with no volumes registered.  To create a Cache with a prepopulated 
     * database, use 
     * {@link #BasicCache(String,StorageInventoryDB,Collection)}.
     * 
     * @param name  a name to give this cache.  In a system with multiple caches with 
     *              different roles this provides a way to distinguish between the different 
     *              caches in messages.
     * @param idb   the (empty) inventory database to use
     * @param log   a particular Logger instance that should be used.  If null, a default one
     *                will be provided.  
     */
    public BasicCache(String name, StorageInventoryDB idb, Logger log) {
        this(name, idb, 2, log);
    }

    /**
     * create the Cache without volumes.  The provided inventory database should be empty of 
     * object records and with no volumes registered.  To create a Cache with a prepopulated 
     * database, use 
     * {@link #BasicCache(String,StorageInventoryDB,Collection)}.
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb       the inventory database to use
     * @param volcount  the expected number of CacheVolumes that will be attached via addVolume()
     * @param log       a particular Logger instance that should be used.  If null, a default one
     *                    will be created.  
     */
    public BasicCache(String name, StorageInventoryDB idb, int volcount, Logger log) {
        super(name);
        db = idb;
        volumes = new HashMap<String, CacheVolume>(volcount);
        recent = new LinkedList<String>();
        if (log == null) log = LoggerFactory.getLogger("BasicCache:"+name);
        this.log = log;
    }

    /**
     * create the Cache around pre-popluated volumes and inventory database.  This constructor is 
     * for recreating an instance of a Cache around previously persisted volumes and associated 
     * database.  Thus, the volumes and their contents should already be registered with the 
     * associated database.  
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb    the inventory database to use
     * @param vols   the CacheVolumes to attach to this cache
     */
    public BasicCache(String name, StorageInventoryDB idb, Collection<CacheVolume> vols) {
        this(name, idb, vols, null);
    }

    /**
     * create the Cache around pre-popluated volumes and inventory database.  This constructor is 
     * for recreating an instance of a Cache around previously persisted volumes and associated 
     * database.  Thus, the volumes and their contents should already be registered with the 
     * associated database.  
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb    the inventory database to use
     * @param vols   the CacheVolumes to attach to this cache
     * @param log    a particular Logger instance that should be used.  If null, a default one
     *                 will be created.  
     */
    public BasicCache(String name, StorageInventoryDB idb, Collection<CacheVolume> vols, Logger log) {
        this(name, idb, vols.size(), log);
        for (CacheVolume v : vols) {
            volumes.put(v.getName(), v);
            recent.add(v.getName());   // we start with an arbitrary order.
        }
    }

    /**
     * return the names of the volumes that comprise this cache
     */
    public Set<String> volumeNames() { return volumes.keySet(); }

    /**
     * return true if the data object with the given identifier is held in the cache.  This is 
     * done by searching for an entry in the inventory database.
     * @param id   the identifier for the data object of interest.
     * @throws InventoryException  if an error occurs while searching the inventory.
     */
    @Override
    public boolean isCached(String id) throws CacheManagementException {
        List<CacheObject> found = db.findObject(id, db.VOL_FOR_GET);
        if (found.size() <= 0)
            return false;
        for(CacheObject co : found) {
            if (volumes.containsKey(co.volname))
                return true;
            log.debug("Volume {} is not part of this cache", co.volname);
            // log.warn("Volume {} is not part of this cache; updating its status", co.volname);
            // db.setVolumeStatus(co.volname, db.VOL_FOR_INFO);
        }
        return false;
    }

    /**
     * remove all copies of the data object with the given ID from the cache.  This will only remove 
     * it from cache volumes that whose status allows for updates.  
     * @param id       the identifier for the data object of interest.
     * @throws InventoryException  if an error occurs while searching or updating the inventory.
     * @throws CacheManagementException  if an error occurs removing the object from a volume
     */
    @Override
    public void uncache(String id) throws CacheManagementException {
        List<CacheObject> cos = db.findObject(id, db.VOL_FOR_UPDATE);
        for (CacheObject co : cos) {
            try {
                if (volumes.containsKey(co.volname)) {
                    volumes.get(co.volname).remove(co.name);
                    db.removeObject(co.volname, co.name);
                }
                else {
                    log.debug("Volume {} is not part of this cache", co.volname);
                    // log.warn("Volume {} is not part of this cache; updating its status", co.volname);
                    // db.setVolumeStatus(co.volname, db.VOL_FOR_INFO);
                }
            } catch (StorageVolumeException ex) {
                throw new CacheManagementException("Problem removing obj, "+id+", from vol, "+
                                                   co.volname+": "+ ex.getMessage(), ex);
            }
        }
    }

    /**
     * return a CacheObject representation of a data object if it already exists in the cache;
     * otherwise, return null. 
     * @param id       the identifier for the data object of interest.
     * @throws InventoryException  if an error occurs while searching or updating the inventory.
     */
    @Override
    public CacheObject findObject(String id) throws CacheManagementException {
        List<CacheObject> cos = db.findObject(id, db.VOL_FOR_GET);
        if (cos.size() == 0)
            return null;

        boolean prob = false;
        CacheVolume vol = null;
        for (CacheObject co : cos) {
            try {
                vol = volumes.get(co.volname);
                if (vol == null) {
                    log.debug("Volume {} is not part of this cache", co.volname);
                    // log.warn("Volume {} is not part of this cache; updating its status", co.volname);
                    // db.setVolumeStatus(co.volname, db.VOL_FOR_INFO);
                }                    
                else if (! vol.exists(co.name)) {
                    log.warn("object missing from volume, {} (updating db): {}", co.volname, co.name);
                    db.removeObject(co.volname, co.name);
                }
                else {
                    co.volume = vol;
                    return co;
                }
            } catch (StorageVolumeException ex) {
                log.error("Trouble interacting with volume="+co.volname, ex);
                prob = true;
            }
        }

        log.error("Volumes appear out of sync with inventory: all found objects inaccessible.");
        if (prob)
            throw new CacheManagementException(id+": object is inaccessible");

        log.info("Cleaning up the inventory: removing id="+id);
        uncache(id);
        return null;
    }

    /**
     * make the cache volume part of this cache.  The cache is expected to be empty.
     * @param vol       the CacheVolume to add to this Cache
     * @param capacity  the limit on the amount of space available for data objects
     * @param metadata  additional metadata about the volume.  Can be null.
     * @param updmd     if false, the metadata (including the capacity) is not updated
     *                    if the volume's name is already registered in the inventory.
     *                    If true, the metadata is update regardless, except that the 
     *                    status will not be upgraded.  
     * @throws InventoryException  if a problem occurs while registering the volume with 
     *                  the inventory database.
     */
    public void addCacheVolume(CacheVolume vol, int capacity, JSONObject metadata, boolean updmd)
        throws CacheManagementException
    {
        if (metadata == null)
            metadata = new JSONObject();

        JSONObject md = null;
        try { md = db.getVolumeInfo(vol.getName()); } catch (VolumeNotFoundException ex) { }
        if (md != null) {
            // this volume is already registered in the database
            if (updmd) {
                log.info("Updating cache volume registration: "+vol.getName());
                String[] mdnames = JSONObject.getNames(metadata);
                if (mdnames == null) mdnames = new String[0];
                for (String name : mdnames) {
                    try {
                        // Don't allow status to be upgraded
                        if (name.equals("status") && 
                            md.optInt("status", db.VOL_FOR_UPDATE) < metadata.getInt("status"))
                          log.warn("Cannot upgrade status of volume, {}; keeping it at level={}",
                                   vol.getName(), md.optInt("status", db.VOL_FOR_UPDATE));
                        else
                          md.put(name, metadata.get(name));
                    } catch (JSONException ex) {
                        log.warn("Skipping update of volume metadata property, {}, due to trouble: {}",
                                 name, ex.getMessage());
                    }
                }
            }
        }
        else {
            updmd = true;
            md = metadata;
        }
        if (md.has("capacity"))
            md.remove("capacity");

        if (updmd)
            db.registerVolume(vol.getName(), capacity, md);
        volumes.put(vol.getName(), vol);
        recent.add(vol.getName());
    }

    /**
     * return the CacheVolume with the given name or null if name does not exist in this cache
     */
    @Override
    public CacheVolume getVolume(String name) {
        return volumes.get(name);
    }

    /**
     * return a reservation for a given amount of space.  
     * <p>
     * This method will cycle through the available caches looking for space for adding a new
     * data object.  When it finds (or creates some) it returns a Reservation for the space.  
     * @param bytes        the amount of space to reserve
     * @param preferences  an and-ed set of bits indicating what the space will be used for.
     * @return Reservation   an object that provides a claim on space; its interface should be 
     *                     used to deliver the bytes into the cache when the object is available.  
     */
    @Override
    public Reservation reserveSpace(long bytes, int preferences) throws CacheManagementException {
        DeletionPlanner planner = getDeletionPlanner(preferences);
        List<DeletionPlan> plans =
            planner.orderDeletionPlans(bytes,
                                       recent.stream().map(c -> volumes.get(c)).collect(Collectors.toList()));
        return reserveSpace(plans);
    }

    protected Reservation reserveSpace(List<DeletionPlan> plans) throws CacheManagementException {
        // execute each plan until one produces the requisite space.  Typically, the first one should
        // do it.
        for (DeletionPlan dp : plans) {
            try {
                Reservation out = dp.executeAndReserve(this);

                // Turning a volume name into a CacheVolume is a little tricky
                CacheVolume vol = null;
                try {
                    if (out.getVolumeName() == null)
                        throw new CacheManagementException("Null volume name in Reservation name!");
                    vol = getVolume(out.getVolumeName());
                }
                catch (CacheManagementException e) {
                    log.error("Unable to determine volume for Reservation: "+e.getMessage());
                    log.warn("Sending NullCacheVolume to listeners");
                    vol = new NullCacheVolume((out.getVolumeName() == null) ? "Unknown" : out.getVolumeName());
                }

                // notifiy reservation listeners
                notifyReservationMade(vol, out.getSize());
                out.cache = this;

                // circulate our recent list
                recent.remove(out.getVolumeName());
                recent.addLast(out.getVolumeName());
                return out;
            } catch (DeletionFailureException ex) {
                log.warn(ex.getMessage());
                log.info("Trying next plan...");
            }
        }

        // no plans succeeded
        throw new DeletionFailureException("All deletion plans failed to produce enough space "+
                                           "(see log for details).");
    }

    /**
     * warn about errors thrown by listeners
     */
    protected void warn(String message, Exception exc) {
        log.warn(message, exc);
    }
        
    /**
     * return a deletion planner for a particular use.
     * @param preferences  an and-ed set of bits indicating what the space will be used for.
     */
    protected abstract DeletionPlanner getDeletionPlanner(int preferences);

    /**
     * return the StorageInventoryDB instance that manages this Cache.
     * <p>
     * The StorageInventoryDB is made available for direct access and manipulation for purposes 
     * other than caching and retrieving data.  In particular, external objects can conduct specific 
     * maintenance tasks via custom queries to the inventory.  
     */
    public StorageInventoryDB getInventoryDB() {
        return db;
    }

    /**
     * return an IntegrityMonitor instance that is attached to this cache that can be used to test the 
     * integrity of objects in the cache against a specific list of checks.
     */
    public IntegrityMonitor getIntegrityMonitor(List<CacheObjectCheck> checks) {
        return new BasicIntegrityMonitor(getName(), db, volumes, checks,
                                         LoggerFactory.getLogger(log.getName()+".monitor"));
    }

    /**
     * return an IntegrityMonitor instance that is attached to this cache that can be used to test the 
     * integrity of objects in the cache against a default set of checks.  This implementation runs no
     * checks.  
     */
    public IntegrityMonitor getIntegrityMonitor() {
        return new BasicIntegrityMonitor(getName(), db, volumes, 
                                         LoggerFactory.getLogger(log.getName()+".monitor"));
    }
}
