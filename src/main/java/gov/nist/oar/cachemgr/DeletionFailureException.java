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

/**
 * an exception indicating a failure while trying to execute a {@list gov.nist.oar.cachemgr.DeletionPlan}.
 * When thrown, the plan should not be considered as having completed nor is the plan viable anymore. 
 */
public class DeletionFailureException extends CacheVolumeException {

    /**
     * create the exception for particular volume with a custom message
     */
    public DeletionFailureException(String message, String volname) {
        super(message, volname);
    }

    /**
     * create the exception for particular volume with a custom message
     */
    public DeletionFailureException(String message, String volname, Throwable cause) {
        super(message, cause, volname);
    }

    /**
     * create the exception wrapping a throwable.  Use this when an unexpected exception 
     * is thrown while execute a deletion plan
     */
    public DeletionFailureException(String volname, Throwable cause) {
        super("Deletion plan execution failed on volume, " + volname + 
              ": " + cause.getMessage(), cause, volname);
    }

    /**
     * create the exception with a custom message where the volume is unknown or irrelevent.
     */
    public DeletionFailureException(String message) {
        super(message, null, null);
    }

}    
