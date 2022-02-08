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

/**
 * an exception indicating that a request was made for a cache volume with a name that is not 
 * registered in the storage inventory.  
 */
public class VolumeNotFoundException extends InventoryException {

    static final String msgPrefix = "Cache volume name is not registered";

    /**
     * the name of the volume that was requested (or null if not known)
     */
    public String volumeName = null;

    /**
     * create an exception with an unknown cause
     */
    public VolumeNotFoundException() { super(msgPrefix); }

    /**
     * create an exception indicating that the given volume name was not found 
     * in the inventory
     * @param volname   the volume name that was not recognized (can be null if unknown)
     */
    public VolumeNotFoundException(String volname) {
        this(msgPrefix + ": " + volname, volname, null);
    }

    /**
     * create an exception that wraps another exception
     */
    public VolumeNotFoundException(Throwable ex) {
        super(msgPrefix + " (" + ex.getMessage() + ")", ex);
    }

    /**
     * create an exception with a custom message
     * @param msg       a custom message for this failure
     * @param volname   the volume name that was not recognized (can be null if unknown)
     */
    public VolumeNotFoundException(String msg, String volname) {
        this(msg, volname, null);
    }

    /**
     * create an exception with a custom message
     * @param msg       a custom message for this failure
     * @param volname   the volume name that was not recognized (can be null if unknown)
     * @param ex        an underlying exception resulting from this problem (can be null)
     */
    public VolumeNotFoundException(String msg, String volname, Throwable ex) {
        super(msg, ex);
        volumeName = volname;
    }
}

