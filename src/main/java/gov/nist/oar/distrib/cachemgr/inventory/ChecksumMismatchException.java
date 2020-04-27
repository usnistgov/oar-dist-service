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
package gov.nist.oar.distrib.cachemgr.inventory;

import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.IntegrityException;

/**
 * an exception indicating a cache object's current checksum (and possibly size as well) was found 
 * not to match that which is recorded in the cache's inventory database.  
 */
public class ChecksumMismatchException extends IntegrityException {

    /**
     * the current size of the object in a cache volume.  If less than zero, then the size was not
     * calculated is otherwise not known.  
     */
    public long size = -1L;

    /**
     * the hash calculated for the object currently in the cache volume.  If null, the calculated
     * hash is not available. 
     */
    public String calculatedHash = null;

    /**
     * create an exception with a custom message
     * @param msg    a custom message
     * @param ex     a Throwable that was caught as the underlying cause 
     *                  of the error
     */
    public ChecksumMismatchException(String msg, Throwable ex) {
        super(msg, ex);
    }

    /**
     * create an exception with a custom message
     * @param msg      a custom message
     * @param obj      the cache object found to be corrupted in some way
     * @param ex       a Throwable that was caught as the underlying cause 
     *                    of the error
     */
    public ChecksumMismatchException(String msg, CacheObject obj, Throwable ex) {
        super(msg, obj, ex);
    }

    /**
     * create an exception with a custom message
     * @param msg    a custom message
     */
    public ChecksumMismatchException(String msg) {
        this(msg, null, null);
    }

    /**
     * create an exception with a custom message
     * @param msg      a custom message
     * @param obj      the cache object found to be corrupted in some way
     * @param hash     the hash that was calculated for the object
     * @param size     the current size of the object in its volume
     */
    public ChecksumMismatchException(String msg, CacheObject obj, String hash, long size) {
        super(msg, obj);
        this.calculatedHash = hash;
        this.size = size;
    }

    /**
     * create an exception with a message appropriate for the caclculated hash and size.  If 
     * the given size is non-negative and less than the size attached to the {@link CacheObject},
     * the message will highlight that the file is too small; otherwise, it will say that the 
     * hashes do not match.
     * @param obj      the cache object found to be corrupted in some way
     * @param hash     the hash that was calculated for the object
     * @param size     the current size of the object in its volume
     */
    public ChecksumMismatchException(CacheObject obj, String hash, long size) {
        this(messageFor(obj, hash, size), obj, hash, size);
    }

    /**
     * create an exception with a default message.  
     * @param obj      the cache object found to be corrupted in some way
     * @param hash     the hash that was calculated for the object
     */
    public ChecksumMismatchException(CacheObject obj, String hash) {
        this(messageFor(obj, hash, -1L), obj, hash, -1L);
    }

    protected static String messageFor(CacheObject co, String hash, long size) {
        StringBuilder sb = new StringBuilder(makeObjectName(co)).append(": ");

        long calcsz = co.getSize();
        if (size >= 0 && calcsz >= 0 && size != calcsz)
            sb.append("Cache object has wrong size: ").append(size).append(" != ").append(calcsz);

        else
            sb.append("Checksum hash does not match recorded value");
            
        return sb.toString();
    }
}
