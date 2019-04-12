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

import org.json.JSONObject;
import java.util.List;

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
     * @returns List<CacheObject>  the copies of the object in the cache.  Each element represents
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
     *                 values are defined in the {@list gov.nist.oar.cachemgr.VolumeStatus} interface.
     * @returns List<CacheObject>  the copies of the object in the cache.  Each element represents
     *                             a copy in a different cache volume.  This list will be empty if 
     *                             the object is not registered.
     * @throws InventoryException  if there is an error accessing the underlying database.
     */
    public List<CacheObject> findObject(String id, int purpose) throws InventoryException;

    /**
     * return all the data object with a given name in a particular cache volume.  
     * @param volname  the name of the volume to search
     * @param objname  the name of the object was given in that volume
     * @returns CacheObject  the object in the cache or null if the object is not found in the volume
     * @throws InventoryException  if there is an error accessing the underlying database.
     */
    public CacheObject findObject(String volname, String objname) throws InventoryException;

    /**
     * record the addition of an object to a volume.  The metadata stored with the 
     * object can vary by application.  
     * @param id       the identifier for the object being stored
     * @param volname  the name of the volume where the object was added
     * @param objname  the name of the object was given in that volume
     * @param metadata the metadata to be associated with that object (can be null)
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
     * @returns boolean   false if the objname is not registered as in the specified volume
     * @throws InventoryException   if there is a failure updating the database, including 
     *                       consistency errors.
     */
    public boolean updateMetadata(String volname, String objname, JSONObject metadata)
        throws InventoryException;

    /**
     * record the removal of the object with the given name from the given volume
     * @param volname  the name of the volume where the object was added
     * @param objname  the name of the object was given in that volume
     */
    public void removeObject(String volname, String objname) throws InventoryException;

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
    public String[] checksumAlgorithms() throws InventoryException;

    /**
     * return the names of cache volumes registered in the database
     */
    public String[] volumes() throws InventoryException;

    /**
     * create an entry for the given checksum algorithm in the database, making it a recognised 
     * algorithm.
     */
    public void registerAlgorithm(String algname) throws InventoryException;

    /**
     * add a cache volume that is available for storage to the database.  
     * @param name      the name to refer to the cache volume as.  This is used as 
     *                  the volume name in any CacheObject instances returned by 
     *                  findObject()
     * @param capacity  the number of bytes of data that this volume can hold.
     * @param metadata  arbitrary metadata describing this volume.  
     */
    public void registerVolume(String name, long capacity, JSONObject metadata)
        throws InventoryException;
}
