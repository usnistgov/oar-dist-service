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

import org.json.JSONObject;
import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * an interface for storing metadata about objects in the cache, including
 * their location.  It provides an inteface for update the locations of objects 
 * as they are added and removed from volumes.  
 *
 * This interface allows for multiple implementations for the database.  
 */
public interface StorageInventoryDB extends VolumeStatus {

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
    public List<CacheObject> findObject(String id) throws InventoryException;

    /**
     * return all the known locations of an object with a given id in the volumes
     * managed by this database.  
     * @param id       the identifier for the desired object
     * @param purpose  an integer indicating the purpose for locating the object.  Recognized 
     *                 values are defined in the {@link gov.nist.oar.distrib.cachemgr.VolumeStatus} interface.
     * @return List<CacheObject>  the copies of the object in the cache.  Each element represents
     *                             a copy in a different cache volume.  This list will be empty if 
     *                             the object is not registered.
     * @throws InventoryException  if there is an error accessing the underlying database.
     */
    public List<CacheObject> findObject(String id, int purpose) throws InventoryException;

    /**
     * return all the data object with a given name in a particular cache volume.  
     * @param volname  the name of the volume to search
     * @param objname  the name of the object was given in that volume
     * @return CacheObject  the object in the cache or null if the object is not found in the volume
     * @throws InventoryException  if there is an error accessing the underlying database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public CacheObject findObject(String volname, String objname) throws InventoryException;

    /**
     * return all data objects found in the specified data volume for a particular purpose.  The 
     * purpose specified can affect what files are selected and/or how they are sorted in the returned 
     * list.  The default behavior is to assume that the listing for creating a deletion plan for the
     * the specified volume.  It is recommended that implementations then sort these results to put 
     * files more likely to be deleted first.  
     * @param volname     the name of the volume to list objects from.
     * @param purpose     a label that indicates the purpose for retrieving the list so as to 
     *                    affect object selection and sorting.  The recognized values are implementation-
     *                    specific except that if set to null, an empty string, or otherwise unrecognized,
     *                    it should be assumed that the list is for creating a deletion plan.  The 
     *                    label typically maps to a particular selection query optimized for a 
     *                    particular purpose.  
     * @throws InventoryException  if there is an error accessing the underlying database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public List<CacheObject> selectObjectsFrom(String volname, String purpose) throws InventoryException;

    /**
     * return a list of data objects found in the specified data volume according to a given 
     * selection strategy.  
     * @param volname     the name of the volume to list objects from.
     * @param strategy    an encapsulation of the strategy that should be used for selecting the 
     *                    records.  
     * @throws InventoryException  if there is an error accessing the underlying database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public List<CacheObject> selectObjectsFrom(String volname, SelectionStrategy strategy)
        throws InventoryException;

    /**
     * record the addition of an object to a volume.  The metadata stored with the 
     * object can vary by application.  
     * @param id       the identifier for the object being stored
     * @param volname  the name of the volume where the object was added
     * @param objname  the name of the object was given in that volume
     * @param metadata the metadata to be associated with that object (can be null)
     * @throws InventoryException  if there is an error accessing the underlying database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public void addObject(String id, String volname, String objname, JSONObject metadata)
        throws InventoryException;

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
    public boolean updateMetadata(String volname, String objname, JSONObject metadata)
        throws InventoryException;

    /**
     * update the time of last access for an object to the current time.
     * <pre>
     * Note that this time should be initialized automatically when the object is first added
     * to a volume (via {@link #addObject(String,String,String,JSONObject) addObject()}); thus, it 
     * should not be necessary to call this to initialize the access time.
     */
    public boolean updateAccessTime(String volname, String objname) throws InventoryException;

    /**
     * record the removal of the object with the given name from the given volume.  
     * <p>
     * This will typically be implemented as <code>removeObject(volname, objname, false)</code>.
     * @param volname  the name of the volume where the object was added
     * @param objname  the name of the object was given in that volume
     * @throws InventoryException  if there is an error accessing the underlying database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public void removeObject(String volname, String objname) throws InventoryException;

    /**
     * record the removal of the object with the given name from the given volume
     * @param volname  the name of the volume where the object was added
     * @param objname  the name of the object was given in that volume
     * @param purge    if false, a record for the object will remain in the database but marked as 
     *                    uncached.  If true, the record will be complete removed from the database.
     * @throws InventoryException  if there is an error accessing the underlying database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public void removeObject(String volname, String objname, boolean purge) throws InventoryException;

    /*
     * remove all object entries.  This should be used when reinitializing the database.
     *
    public void removeAllObjects() throws InventoryException;
     */

    // need to add a method that returns records that can be used to create
    // deletion plans.

    /**
     * return the names of checksumAlgorithms known to the database
     */
    public Collection<String> checksumAlgorithms() throws InventoryException;

    /**
     * return the names of cache volumes registered in the database
     */
    public Collection<String> volumes() throws InventoryException;

    /**
     * create an entry for the given checksum algorithm in the database, making it a recognised 
     * algorithm.
     */
    public void registerAlgorithm(String algname) throws InventoryException;

    /**
     * add a cache volume that is available for storage to the database.  
     * @param name      the name to refer to the cache volume as.  This is used as 
     *                  the volume name in any CacheObject instances returned by 
     *                  {@link #findObject(String) findObject()}
     * @param capacity  the number of bytes of data that this volume can hold.
     * @param metadata  arbitrary metadata describing this volume.  
     */
    public void registerVolume(String name, long capacity, JSONObject metadata)
        throws InventoryException;

    /**
     * return the information associated with the registered storage volume
     * with the given name.
     */
    public JSONObject getVolumeInfo(String name) throws InventoryException;

    /**
     * update the status of a registered volume
     * @throws InventoryException  if there is an error accessing the underlying database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public void setVolumeStatus(String volname, int status) throws InventoryException;

    /**
     * get the current status of a registered volume.  Recognized values are defined in the 
     * {@link gov.nist.oar.distrib.cachemgr.VolumeStatus} interface; other application-specific values 
     * are allowed. 
     * @throws InventoryException  if there is an error accessing the underlying database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public int getVolumeStatus(String volname) throws InventoryException;

    /**
     * return the amount of available (unused) space in the specified volume, in bytes
     * @throws InventoryException  if there is an error accessing the underlying database.
     * @throws VolumeNotFoundException  if a volname is not recognized as a registered volume name.
     */
    public long getAvailableSpaceIn(String volname) throws InventoryException;

    /**
     * return the amount of available (unused) space in each volume, in bytes
     * @return   a map giving the space for each volume where the key is the volume name and 
     *           the value is the space as a Long.  
     * @throws InventoryException  if there is an error accessing the underlying database.
     */
    public Map<String, Long> getAvailableSpace() throws InventoryException;

    /**
     * return the amount of space currently being used in each volume, in bytes.  
     * This is the sum of the sizes of all data objects and reservations currently in the 
     * the volume.  
     * @return   a map giving the space for each volume where the key is the volume name and 
     *           the value is the space as a Long.  
     * @throws InventoryException  if there is an error accessing the underlying database.
     */
    public Map<String, Long> getUsedSpace() throws InventoryException;
}
