package gov.nist.oar.cachemgr;

import javax.json.JsonObject;

/**
 * an interface for storing metadata about objects in the cache, including
 * their location.  It provides an inteface for update the locations of objects 
 * as they are added and removed from volumes.  
 *
 * This interface allows for multiple implementations for the database.  
 */
public interface StorageInventoryDB {

    /**
     * return all the known locations of an object with a given id in the volumes
     * managed by this database.  
     */
    public CacheObject[] findObject(String id);

    /**
     * record the addition of an object to a volume.  
     */
    public void addObject(String id, String volId, String objname,
                                   JsonObject metadata);

    /**
     * record the removal of the object with the given name from the given volume
     */
    public void removeObject(String volId, String objname);

    // need to add a method that returns records that can be used to create
    // deletion plans.

    /**
     * return the names of checksumAlgorithms known to the database
     */
    public String[] checksumAlgorithms();

}
