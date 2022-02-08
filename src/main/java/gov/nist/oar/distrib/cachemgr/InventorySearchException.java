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
 * an exception indicating that something went wrong while executing a search on the storage inventory.
 */
public class InventorySearchException extends InventoryException {

    static final String msgPrefix = "Problem searching the storage inventory";

    /**
     * create an exception with an unknown cause
     */
    public InventorySearchException() { super(msgPrefix); }

    /**
     * create an exception that wraps another exception
     */
    public InventorySearchException(Throwable ex) {
        super(msgPrefix + ": " + ex.getMessage(), ex);
    }

    /**
     * create an exception with a custom message
     */
    public InventorySearchException(String msg, Throwable ex) {
        super(msg, ex);
    }

    /**
     * create an exception with a custom message
     */
    public InventorySearchException(String msg) {
        this(msg, null);
    }

}

