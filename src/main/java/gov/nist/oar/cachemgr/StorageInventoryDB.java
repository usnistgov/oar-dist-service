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
