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
package gov.nist.oar.distrib.cachemgr;

import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ObjectNotFoundException;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * a basic implementation of the IntegrityMonitor interface.  (It is not restricted to being used with 
 * {@link BasicCache} although {@link BasicCache} returns an instance of this via its 
 * {@link BasicCache#createIntegrityMonitor(List)} method.)
 * <p>
 * This implementation has access to the cache's {@link StorageInventoryDB} and its {@link CacheVolume}s to 
 * do its work.  It can optionally be configured with a {@link SelectionStrategy} instance which will be 
 * used to select objects from the database to check; otherwise, it will use the database "check" purpose 
 * (via {@link StorageInventoryDB#selectObjects(String,int)}).  Thus, in the latter case, the 
 * {@link StorageInventoryDB} must support the "check" purpose and return a sensible selection of objects.
 * (Subclasses of {@link gov.nist.oar.distrib.cachemgr.inventory.JDBCStorageInventoryDB} all do.)
 */
public class BasicIntegrityMonitor implements IntegrityMonitor {

    private String name = null;
    protected List<CacheObjectCheck> checks = null;
    protected StorageInventoryDB db = null;
    protected Map<String, CacheVolume> volumes = null;
    protected SelectionStrategy selstrat = null;
    protected Logger log = null;

    /**
     * create a basic monitor attached to a simple cache.  
     * @param cachename    the name of the cache that this IntegrityMonitor monitors
     * @param sidb         the inventory database for the cache being monitored.  This 
     *                     {@link StorageInventoryDB} instance must support the "check" purpose via its
     *                     {@link StorageInventoryDB#selectObjects(String,int) selectObjects()} method.
     * @param cachevols    the cache's {@link CacheVolume}s.  The string keys are the volumes' names, as
     *                     registered in the inventory database, {@code sidb}; all writable volumes registered
     *                     in the database must appear in this map.  
     */
    public BasicIntegrityMonitor(String cachename, StorageInventoryDB sidb, 
                                 Map<String, CacheVolume> cachevols)
    {
        this(cachename, sidb, cachevols, new ArrayList<CacheObjectCheck>());
    }
                                 
    /**
     * create a basic monitor attached to a simple cache.  
     * @param cachename    the name of the cache that this IntegrityMonitor monitors
     * @param sidb         the inventory database for the cache being monitored.  This 
     *                     {@link StorageInventoryDB} instance must support the "check" purpose via its
     *                     {@link StorageInventoryDB#selectObjects(String,int) selectObjects()} method.
     * @param cachevols    the cache's {@link CacheVolume}s.  The string keys are the volumes' names, as
     *                     registered in the inventory database, {@code sidb}; all writable volumes registered
     *                     in the database must appear in this map.  
     * @param logger       the logger to use to report errors and warnings; if null, a default is created.
     */
    public BasicIntegrityMonitor(String cachename, StorageInventoryDB sidb, 
                                 Map<String, CacheVolume> cachevols, Logger logger)
    {
        this(cachename, sidb, cachevols, new ArrayList<CacheObjectCheck>(), logger);
    }
                                 
    /**
     * create a basic monitor attached to a simple cache.  
     * @param cachename    the name of the cache that this IntegrityMonitor monitors
     * @param sidb         the inventory database for the cache being monitored.  This 
     *                     {@link StorageInventoryDB} instance must support the "check" purpose via its
     *                     {@link StorageInventoryDB#selectObjects(String,int) selectObjects()} method.
     * @param cachevols    the cache's {@link CacheVolume}s.  The string keys are the volumes' names, as
     *                     registered in the inventory database, {@code sidb}; all writable volumes registered
     *                     in the database must appear in this map.  
     * @param checklist    the integrity checks that should run on the cache objects selected from the database.
     *                     The checks will be applied in the order that they appear in this list.
     * @param logger       the logger to use to report errors and warnings; if null, a default is created.
     */
    public BasicIntegrityMonitor(String cachename, StorageInventoryDB sidb, Map<String, CacheVolume> cachevols,
                                 List<CacheObjectCheck> checklist, Logger logger)
    {
        name = cachename;
        db = sidb;
        volumes = cachevols;
        if (checklist == null)
            checklist = new ArrayList<CacheObjectCheck>();
        checks = checklist;
        if (logger == null)
            logger = LoggerFactory.getLogger(getClass());
        log = logger;
    }
                                 
    /**
     * create a basic monitor attached to a simple cache.  
     * @param cachename    the name of the cache that this IntegrityMonitor monitors
     * @param sidb         the inventory database for the cache being monitored.  This 
     *                     {@link StorageInventoryDB} instance must support the "check" purpose via its
     *                     {@link StorageInventoryDB#selectObjects(String,int) selectObjects()} method.
     * @param cachevols    the cache's {@link CacheVolume}s.  The string keys are the volumes' names, as
     *                     registered in the inventory database, {@code sidb}; all writable volumes registered
     *                     in the database must appear in this map.  
     * @param checklist    the integrity checks that should run on the cache objects selected from the database.
     *                     The checks will be applied in the order that they appear in this list.
     */
    public BasicIntegrityMonitor(String cachename, StorageInventoryDB sidb, Map<String, CacheVolume> cachevols,
                                 List<CacheObjectCheck> checklist)
    {
        this(cachename, sidb, cachevols, checklist, (Logger) null);
    }
                                 
    /**
     * create a basic monitor attached to a simple cache.  
     * @param cachename    the name of the cache that this IntegrityMonitor monitors
     * @param sidb         the inventory database for the cache being monitored.  This 
     *                     {@link StorageInventoryDB} instance must support the "check" purpose via its
     *                     {@link StorageInventoryDB#selectObjects(String,int) selectObjects()} method.
     * @param cachevols    the cache's {@link CacheVolume}s.  The string keys are the volumes' names, as
     *                     registered in the inventory database, {@code sidb}; all writable volumes registered
     *                     in the database must appear in this map.  
     * @param strategy     a {@link SelectionStrategy} to use to select cache objects to check (via 
     *            {@link StorageInventoryDB#selectObjects(SelectionStrategy) selectObjects(SelectionStrategy)}).
     * @param logger       the logger to use to report errors and warnings; if null, a default is created.
     */
    public BasicIntegrityMonitor(String cachename, StorageInventoryDB sidb, Map<String, CacheVolume> cachevols, 
                                 List<CacheObjectCheck> checklist, SelectionStrategy strategy, Logger logger)
    {
        this(cachename, sidb, cachevols, checklist, logger);
        selstrat = strategy;
    }
    
    /**
     * create a basic monitor attached to a simple cache.  
     * @param cachename    the name of the cache that this IntegrityMonitor monitors
     * @param sidb         the inventory database for the cache being monitored.  This 
     *                     {@link StorageInventoryDB} instance must support the "check" purpose via its
     *                     {@link StorageInventoryDB#selectObjects(String,int) selectObjects()} method.
     * @param cachevols    the cache's {@link CacheVolume}s.  The string keys are the volumes' names, as
     *                     registered in the inventory database, {@code sidb}; all writable volumes registered
     *                     in the database must appear in this map.  
     * @param strategy     a {@link SelectionStrategy} to use to select cache objects to check (via 
     *            {@link StorageInventoryDB#selectObjects(SelectionStrategy) selectObjects(SelectionStrategy)}).
     */
    public BasicIntegrityMonitor(String cachename, StorageInventoryDB sidb, Map<String, CacheVolume> cachevols, 
                                 List<CacheObjectCheck> checklist, SelectionStrategy strategy)
    {
        this(cachename, sidb, cachevols, checklist, strategy, null);
    }
    
    /**
     * return the name of the cache that this instance monitors.  Typically, this equals the value 
     * returned by the {@link Cache}'s {@link Cache#getName() getName()} method.
     */
    @Override
    public String getCacheName() { return name; }

    /**
     * Check the given object from the cache by applying all configured integrity checks.  If all 
     * checks pass, the object's entry in the inventory database will be updated accordingly.
     * <p> 
     * The given object must have its {@link CacheObject#volume} field specified and that volume must be
     * registered in the storage inventory database.  
     * <p>
     * In this implementation, if one of the configured tests fails, no further checking occurs.
     * @param co    the object to check
     * @throws IntegrityException       if the check was executed successfully but found problem with the 
     *                                  object.
     * @throws ObjectNotFoundException  if the object is not found (perhaps because it was removed) in 
     *                                  volume indicated in the CacheObject
     * @throws StorageVolumeException   if some other error occurs while trying to access the object within 
     *                                  the storage volume
     * @throws CacheManagementException if the check could not be run successfully due to some error other 
     *                                  than a problem access the object from storage.
     * @throws IllegalArgumentException if the given {@link CacheObject}'s {@link CacheObject#volume volume}
     *                                  field is null.
     */
    @Override
    public void check(CacheObject co)
        throws IntegrityException, StorageVolumeException, CacheManagementException
    {
        if (co.volume == null)
            throw new IllegalArgumentException("CacheObject's volume field is null");
        for (CacheObjectCheck chk : checks) {
            chk.check(co);   // exception thrown upon failure
        }

        // success!
        try {
            db.updateCheckedTime(co.volname, co.name, Instant.now().toEpochMilli());
        }
        catch (InventoryException ex) {
            log.error("Problem updating check status for object (volume={}, name={}): {}",
                      co.volname, co.name, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Return a list of CacheObjects that are most in need of being checked.  The list will be ordered 
     * from most in need to less than in need.  (Ordering is controlled by the {@link SelectionStrategy} 
     * used, but typically the list will be ordered based on the time the object was last checked, oldest
     * to youngest.)  This implementation will use the default {@link SelectionStrategy} set at construction 
     * time.
     * @param max   the maximum number of objects to return.  
     */
    @Override
    public List<CacheObject> selectObjectsToBeChecked(int max) throws InventoryException {
        if (selstrat != null)
            return db.selectObjects(selstrat);

        List<CacheObject> out = db.selectObjects("check", max);
        for (CacheObject co : out) {
            co.volume = volumes.get(co.volname);
        }
        return out;
    }

    /**
     * Return a list of CacheObjects that are most in need of being checked.  The list will be ordered 
     * according to the given {@link SelectionStrategy}.
     */
    @Override
    public List<CacheObject> selectObjectsToBeChecked(SelectionStrategy strat) throws InventoryException {
        List<CacheObject> out = db.selectObjects(strat);
        for (CacheObject co : out) {
            co.volume = volumes.get(co.volname);
        }
        return out;
    }

    /**
     * Check the given list of objects in the cache and return a list of those that fail the tests.
     * An object is included in the returned list if any of the checks applied to it throw an 
     * {@link IntegrityException}.  Once an object fails a check, no further checking is done.  
     * All other exceptions are generallly ignored except when there are 
     * an excessive number of {@link StorageVolumeException} exceptions (although these other exceptions
     * may cause error messages to be logged). 
     * @param cos     the list of objects to check.
     * @param failed  an editable list to which this method can add the {@link CacheObject}s that fail 
     *                any integrity check. 
     * @param deleteOnFail  if true, then an object that fails its checks should be immediately removed from
     *                      it cache volume.  
     * @return int -- the number of successfully check objects from the input list, {@code cos}
     */
    @Override
    public int selectCorruptedObjects(List<CacheObject> cos, List<CacheObject> failed, boolean deleteOnFail)
        throws StorageVolumeException, CacheManagementException
    {
        int faillim=10, failcnt=0, successcnt=0;
        for(CacheObject co : cos) {
            try {
                check(co);
                successcnt++;
            }
            catch (IntegrityException ex) {
                failed.add(co);
                if (deleteOnFail) removeObject(co);
            }
            catch (ObjectNotFoundException ex) {
                log.warn("Unable to check object as it is no longer in cache volume: volname={}, name={}",
                         co.volname, co.name);
                failed.add(co);
                if (deleteOnFail) removeObject(co);
            }
            catch (StorageVolumeException ex) {
                if (++failcnt > faillim)
                    throw new StorageVolumeException("Too many check failures; latest: "+ex.getMessage(), ex);
                log.error("Problem accessing object in storage during check (volname={}, name={}): {}",
                          co.volname, co.name, ex.getMessage());
            }
            catch (CacheManagementException ex) {
                if (++failcnt > faillim)
                    throw new CacheManagementException("Too many check failures; latest: "+ex.getMessage(), ex);
                log.error("Problem interacting with cache during check (volname={}, name={}): {}",
                          co.volname, co.name, ex.getMessage());
            }
        }
        return successcnt;
    }

    protected boolean removeObject(CacheObject co) throws StorageVolumeException, InventoryException {
        synchronized (db) {
            boolean out = co.volume.remove(co.name);
            db.removeObject(co.volname, co.name);
            return out;
        }
    }

    /**
     * Look for objects in this cache that require an integrity check and check them to find corrupted 
     * objects.  This method will update the checked status of each object that successfully passes all 
     * integrity checks; thus, with each call to this method, a different set of objects will be checked
     * (except when the number of objects in the cache is small compared to {@code max}.  
     * <p>
     * An object is included in the {@code failed} list if any of the checks applied to it throw an 
     * {@link IntegrityException}.  Once an object fails a check, no further checking is done on it.  
     * All other exceptions are generallly ignored except when there are 
     * an excessive number of {@link StorageVolumeException} exceptions (although these other exceptions
     * may cause error messages to be logged). 
     * <p>
     * This method basically combines {@link #selectObjectsToBeChecked(int)} and 
     * {@link #selectCorruptedObjects(List,List,boolean)} into one call.  
     * @param max     the maximum number of objects to check
     * @param failed  an editable list to which this method can add the {@link CacheObject}s that fail 
     *                any integrity check. 
     * @param deleteOnFail  if true, then an object that fails its checks should be immediately removed from
     *                      it cache volume.  
     * @return int -- the number of successfully check objects from the input list, {@code cos}
     */
    public int findCorruptedObjects(int max, List<CacheObject> failed, boolean deleteOnFail)
        throws InventoryException, StorageVolumeException, CacheManagementException
    {
        return selectCorruptedObjects(selectObjectsToBeChecked(max), failed, deleteOnFail);
    }
}
