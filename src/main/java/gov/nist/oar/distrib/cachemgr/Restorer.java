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

import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.StorageVolumeException;

import org.json.JSONObject;
import org.json.JSONException;

/**
 * an interface that can locate a file in long-term storage and copy it to a CacheVolume.
 * <p>
 * Restoring an object from long-term storage can involve substantial special knowledge about the 
 * storage system (e.g. tape, NFS-mounted drives, AWS S3 or Glacier), APIs (e.g. AWS, webdav), 
 * and packaging formats (e.g. zip, tarballs, BagIt bags).  This interface is intended to hide 
 * those details through a common abstract interface.  
 * <p>
 * The object is expected to be located via an ID that is typically independent of the long-term 
 * storage system (as well as any particular CacheVolume).  The Restorer is responsible for 
 * mapping the identifier to a filename (or names) in the storage.  
 */
public interface Restorer {

    /**
     * return true if an object does <i>not</i> exist in the long term storage system.  Returning 
     * true indicates that the object <i>may</i> exist, but it is not guaranteed.  These semantics
     * are intended to allow the implementation to be fast and without large overhead. 
     * @param id   the storage-independent identifier for the data object
     */
    public boolean doesNotExist(String id) throws StorageVolumeException, CacheManagementException;

    /**
     * return the size of the object with the given identifier in bytes or -1L if unknown
     * @param id   the storage-independent identifier for the data object
     * @throws ObjectNotFoundException    if the object can not be found in the underlying storage
     * @throws UnsupportedOperationException   if due to implementation limitations, this Restorer is 
     *             unable to return sizes for any objects it knows about.  
     */
    public long getSizeOf(String id)
        throws StorageVolumeException, CacheManagementException, UnsupportedOperationException;

    /**
     * return the checksum hash of the object with the given identifier or null if unknown.  
     * @param id   the storage-independent identifier for the data object
     * @throws ObjectNotFoundException    if the object can not be found in the underlying storage
     * @throws UnsupportedOperationException   if due to implementation limitations, this Restorer is 
     *             unable to return sizes for any objects it knows about.  
     */
    public Checksum getChecksum(String id)
        throws StorageVolumeException, CacheManagementException, UnsupportedOperationException;

    /**
     * restore the identified object to the CacheVolume associated with the given Reservation
     * @param id        the storage-independent identifier for the data object
     * @param resv      the reservation for space in a CacheVolume where the object should be restored to.
     * @param name      the name to assign to the object within the volume.  
     * @param metadata  the metadata to associate with this restored object.  This will get merged with 
     *                    metadata determined as part of the restoration.  Can be null.
     * @throws RestorationException  if there is an error while accessing the source data for the object
     * @throws StorageVolumeException  if there is an error while accessing the cache volume while writing
     * @throws ObjectNotFoundException    if the object can not be found in the underlying storage
     * @throws JSONException         if there is a problem accessing the information in the provided metadata
     */
    public void restoreObject(String id, Reservation resv, String name, JSONObject metadata)
        throws RestorationException, StorageVolumeException, JSONException;

    /**
     * return a recommended name for the object with the given id that can be used as its name in a 
     * cache volume.  
     * @throws StorageVolumeException -- if an exception occurs while consulting the underlying storage system
     * @throws RestorationException -- if some other error occurs while (e.g. the ID is not valid)
     */
    public String nameForObject(String id) throws RestorationException, StorageVolumeException;
}
