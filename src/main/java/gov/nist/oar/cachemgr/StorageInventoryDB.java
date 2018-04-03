package gov.nist.oar.cachemgr;

import javax.json.JsonObject;

/**
 * an abstract class for storing metadata about objects in the cache, including
 * their location.  It provides an inteface for update the locations of objects 
 * as they are added and removed from volumes.  
 *
 * This abstract class allows for multiple implementations for the database.  It 
 * can be implemented a
 */
public abstract class StorageInventoryDB {

    /**
     * return all the known locations of an object with a given id in the volumes
     * managed by this database.  
     */
    public abstract CacheObject[] findObject(String id);

    /**
     * record the addition of an object to a volume.  
     */
    public abstract void addObject(String id, String volId, String objname,
                                   JsonObject metadata);

}
