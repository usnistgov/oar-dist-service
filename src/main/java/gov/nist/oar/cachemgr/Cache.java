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

/**
 * a set of {@link gov.nist.oar.cachemgr.CacheVolume}s that can be used to (temporarily) store 
 * data objects.
 *
 * A data cache is useful in an environment where data products are stored on slow persistent
 * storage and where there is a limited amount of fast storage space suited for delivering that
 * data to users/applications.  Data caches can be set on the fast storage to house the most
 * recently or most requested data products.  A Cache class provides a means for moving data
 * objects in and out of the cache.
 * 
 * A Cache instance has at its disposal a set of {@link gov.nist.oar.cachemgr.CacheVolume}s.  
 * Encapsulated in the cache is a {@link gov.nist.oar.cachemgr.StorageInventoryDB} which tracks
 * which volume(s) an object is stored in (along with other metadata like size and checksums).  
 * Also encapsulated is a strategy for determining the best volume to place new objects; a big 
 * part of this strategy is figuring out what objects to get rid of to make room for a new
 * object.  The {@link reserveSpace(long)} functions and the {@link gov.nist.oar.cachemgr.Reservation} 
 * they return are used to add new objects into the cache.  
 */
public abstract class Cache {

    /**
     * return true if the data object with the given identifier is held in the cache
     * @param id   the identifier for the data object of interest.
     */
    public abstract boolean isCached(String id);

    /**
     * remove all copies of the data object with the given ID from the cache
     * @param id       the identifier for the data object of interest.
     */
    public abstract void uncache(String id) throws CacheManagementException;

    /**
     * return a CacheObject representation of a data object if it already exists in the cache;
     * otherwise, return null. 
     * @param id       the identifier for the data object of interest.
     */
    public abstract CacheObject findObject(String id);

    /**
     * return a reservation for a given amount of space.  
     * @param bytes        the amount of space to reserve
     * @param preferences  an and-ed set of bits indicating what the space will be used for.
     *                     The interpretation of the bits is implementation-specific.  The 
     *                     bits should be used to pick the optimal location for the an 
     *                     object to be put into the cache.
     * @returns Reservation   an object that provides a claim on space; its interface should be 
     *                     used to deliver the bytes into the cache when the object is available.  
     */
    public abstract Reservation reserveSpace(long bytes, int preferences) throws CacheManagementException;

    /**
     * return a reservation for a given amount of space.  This method reserves the space
     * with no user-supplied preferences.  
     * @param bytes        the amount of space to reserve
     * @returns Reservation   an object that provides a claim on space; its interface should be 
     *                     used to deliver the bytes into the cache when the object is available.  
     */
    public Reservation reserveSpace(long bytes) throws CacheManagementException {
        return this.reserveSpace(bytes, 0);
    }
}
