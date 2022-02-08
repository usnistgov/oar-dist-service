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

/**
 * an interface that will check to ensure that an object saved in a cache volume has the characteristics 
 * described by its metadata.  This is used to check that the object has not been corrupted since it was 
 * first written to its volume.  
 * <p>
 * This interface allows for the creation of long-lived checker objects that can be created during the 
 * configuration stage of an application; afterward, many CacheObjects can be passed to it in succession or
 * in parallel.
 */
public interface CacheObjectCheck {

    /**
     * run the integrity check, throwing an exception if the given object fails the check.  
     * <p>
     * Implementations should prefer thread-independent implementations (i.e. not require synchronization)
     * to allow for parallel execution.  
     * @param object    The object in the cache to check.  This instance must have its volume field set.
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
    public void check(CacheObject object)
        throws IntegrityException, StorageVolumeException, CacheManagementException;
}
