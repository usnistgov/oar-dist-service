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

import gov.nist.oar.distrib.StorageVolumeException;

/**
 * an exception indicating a failure while trying to execute a {@link gov.nist.oar.cachemgr.DeletionPlan}.
 * When thrown, the plan should not be considered as having completed nor is the plan viable anymore. 
 */
public class DeletionFailureException extends CacheManagementException {

    StorageVolumeException svcause = null;
    String volume = null;

    /**
     * convert a StorageVolumeException to a DeletionFailureException
     */
    public DeletionFailureException(String message, StorageVolumeException cause) {
        super(message, cause);
        svcause = cause;
        volume = svcause.getVolumeName();
    }

    /**
     * convert a StorageVolumeException to a DeletionFailureException
     */
    public DeletionFailureException(StorageVolumeException cause) {
        this("Deletion plan execution failed on volume, " + cause.getVolumeName() + 
             ": " + cause.getMessage(), cause);
    }

    /**
     * create the exception with a custom message where the volume is unknown or irrelevent.
     */
    public DeletionFailureException(String message) {
        super(message, null);
    }

    /**
     * return the name of the cache storage volume where the exception
     * occurred, or null if the name is not known or applicable.
     */
    public String getVolumeName() {
        return volume;
    }

    /**
     * return the underlying StorageVolumeException that signaled this deletion error
     * or null if no such exception was raised.  
     */
    public StorageVolumeException getStorageVolumeException() { return svcause; }
}    
