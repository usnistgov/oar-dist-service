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

/**
 * an exception indicating that something went wrong while accessing a cache volume.
 * <p>
 * This is a convenience exception for converting a StorageVolumeException into a 
 * CacheManagementException.  
 */
public class CacheVolumeException extends CacheManagementException {

    protected String volume = null;

    /**
     * wrap a StorageVolumeException
     */
    public CacheVolumeException(StorageVolumeException cause) {
        this(cause, cause.getVolumeName());
    }

    /**
     * wrap an arbitrary exception that was raised while interacting with a cache volume
     */
    public CacheVolumeException(Throwable cause, String volname) {
        super(cause);
        volume = volname;
    }
        
    /**
     * create an exception with a custom message
     * @param message      a custom message
     * @param cause       a Throwable that was caught as the underlying cause 
     *                    of the error
     * @param volname  the name of the storage volume where the error 
     *                    occurred 
     */
    public CacheVolumeException(String message, Throwable cause, String volname) {
        super(message, cause);
        volume = volname;
    }

    /**
     * create an exception with a custom message
     * @param message      a custom message
     * @param volname  the name of the storage volume where the error 
     *                  occurred 
     */
    public CacheVolumeException(String message, String volname) {
        this(message, null, volname);
    }

    /**
     * return the name of the cache storage volume where the exception
     * occurred, or null if the name is not known or applicable.
     */
    public String getVolumeName() {
        return volume;
    }

    protected static String messageFor(Throwable cause, String volname) {
        StringBuilder sb = new StringBuilder(getMessagePrefix());
        if (cause instanceof StorageVolumeException) {
            sb.append(": ").append(cause.getMessage());
            return sb.toString();
        }
        
        if (volname != null)
            sb.append('(').append(volname).append(')');
        sb.append(": ");
        
        sb.append(cause.getMessage());
        return sb.toString();
    }

    protected static String messageFor(Throwable cause) {
        return messageFor(cause, null);
    }

    /**
     * return a message prefix that can introduce a more specific message
     */
    public static String getMessagePrefix() {
        return "Cache volume error";
    }
}

