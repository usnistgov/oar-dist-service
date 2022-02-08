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
 * an exception indicating that the system encountered an invalid object metadatum.  This can include 
 * the absence of a required metadatum or a metadatum of the wrong type.  
 */
public class InventoryMetadataException extends InventoryException {

    static final String msgPrefix = "Problem searching the storage inventory: ";

    /**
     * the name of the metadata property or null if not known
     */
    public String metadatumName = null;

    /**
     * create an exception with an unknown cause
     */
    public InventoryMetadataException() { super("Unknown metadatum error occurred"); }

    /**
     * create an exception with a custom message
     */
    public InventoryMetadataException(String msg, Throwable ex) {
        super(msg, ex);
    }

    /**
     * create an exception with a custom message
     */
    public InventoryMetadataException(String msg, String mdname, Throwable ex) {
        super(msg, ex);
        metadatumName = mdname;
    }

    /**
     * create an exception with a custom message
     */
    public InventoryMetadataException(String msg) {
        this(msg, null);
    }

}

