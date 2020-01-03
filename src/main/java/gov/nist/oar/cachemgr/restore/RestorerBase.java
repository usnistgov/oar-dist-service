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
package gov.nist.oar.cachemgr.restore;

import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.StorageStateException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.cachemgr.CacheManagementException;
import gov.nist.oar.cachemgr.RestorationException;
import gov.nist.oar.cachemgr.Restorer;
import gov.nist.oar.cachemgr.Reservation;

import java.io.InputStream;
import java.io.IOException;

import org.json.JSONObject;
import org.json.JSONException;

/**
 * an abstract base class for implementations of the Restorer interface that pulls objects through a 
 * LongTermStorage object.
 * <p>
 * Restoring an object from long-term storage can involve substantial special knowledge about the 
 * storage system (e.g. tape, NFS-mounted drives, AWS S3 or Glacier), APIs (e.g. AWS, webdav), 
 * and packaging formats (e.g. zip, tarballs, BagIt bags).  This interface is intended to hide 
 * those details through a common abstract interface.  
 */
public abstract class RestorerBase implements Restorer {

    /**
     * the long-term storage system holding data objects to be restored.
     */
    protected LongTermStorage store = null;

    /**
     * wrap a LongTermStorage object
     */
    protected RestorerBase(LongTermStorage lts) {
        store = lts;
    }

    /**
     * return true if an object does <i>not</i> exist in the long term storage system.  Returning 
     * true indicates that the object <i>may</i> exist, but it is not guaranteed.
     */
    @Override
    public abstract boolean doesNotExist(String name) throws StorageVolumeException, CacheManagementException;

    /**
     * return the size of the object with the given identifier in bytes or -1L if unknown
     * @throws UnsupportedOperationException   if due to implementation limitations, this Restorer is 
     *             unable to return sizes for any objects it knows about.  
     * @throws ObjectNotFoundException    if the object can not be found in the underlying storage
     */
    @Override
    public abstract long getSizeOf(String id)
        throws StorageVolumeException, CacheManagementException, UnsupportedOperationException;

    /**
     * return the checksum hash of the object with the given identifier or null if unknown.  
     * @throws UnsupportedOperationException   if due to implementation limitations, this Restorer is 
     *             unable to return sizes for any objects it knows about.  
     * @throws ObjectNotFoundException    if the object can not be found in the underlying storage
     */
    @Override
    public abstract Checksum getChecksum(String id)
        throws StorageVolumeException, CacheManagementException, UnsupportedOperationException;

    /**
     * restore the identified object to the CacheVolume associated with the given Reservation
     */
    @Override
    public void restoreObject(String id, Reservation resv, String name, JSONObject metadata)
        throws StorageVolumeException, RestorationException, StorageVolumeException, JSONException
    {
        if (metadata == null) 
            metadata = new JSONObject();
        else 
            metadata = new JSONObject(metadata, JSONObject.getNames(metadata));

        if (! metadata.has("size")) {
            try {
                metadata.put("size", getSizeOf(id));
            }
            catch (CacheManagementException ex) {
                throw new RestorationException("Failed to retrieve size: "+ex.getMessage(), ex, id);
            }
        }
        if (! metadata.has("checksum")) {
            try {
                Checksum cs = getChecksum(id);
                metadata.put("checksum", cs.hash);
                metadata.put("checksumAlgorithhm", cs.algorithm);
            }
            catch (CacheManagementException ex) {
                throw new RestorationException("Failed to retrieve checksum: "+ex.getMessage(), ex, id);
            }
        }

        try {
            resv.saveAs(openDataObject(id), id, name, metadata);
        } catch (StorageVolumeException ex) {
            throw ex;
        } catch (CacheManagementException ex) {
            throw new RestorationException(id + ": Unexpected error while copying to cache: "+ex.getMessage(),
                                           ex, id);
        }
    }

    /**
     * open the data object having the given identifier
     */
    public abstract InputStream openDataObject(String id) throws StorageVolumeException;
}
