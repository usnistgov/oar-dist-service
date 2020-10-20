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
 * an exception indicating that a set of deletion plans could not be generated because there are no
 * no available cache volumes matching the requested preferences.  A client that catches this exception
 * can try the request again with different preferences.  
 */
public class NoMatchingVolumesException extends DeletionFailureException {

    /**
     * the requested preferences.  If less than 0, the requested preferences are unknown.
     */
    public int preferences = -1;

    /**
     * create the exception
     * @param preferences   the requested preferences that could not be matched
     */
    public NoMatchingVolumesException(int preferences) {
        super("No cache volumes available matching requested preferences: "+Integer.toString(preferences));
        this.preferences = preferences;
    }

    /**
     * create the exception
     */
    public NoMatchingVolumesException() {
        super("No cache volumes available matching requested preferences");
    }
}    
