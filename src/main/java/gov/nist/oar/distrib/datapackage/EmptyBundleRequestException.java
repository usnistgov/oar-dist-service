/*
 * This software was developed at the National Institute of Standards and Technology by employees of
 * the Federal Government in the course of their official duties. Pursuant to title 17 Section 105
 * of the United States Code this software is not subject to copyright protection and is in the
 * public domain. This is an experimental system. NIST assumes no responsibility whatsoever for its
 * use by other parties, and makes no guarantees, expressed or implied, about its quality,
 * reliability, or any other characteristic. We would appreciate acknowledgement if the software is
 * used. This software can be redistributed and/or modified freely provided that any derivative
 * works bear some notice that they are derived from it, and any modified versions bear some notice
 * that they have been modified.
 * @author: Deoyani Nandrekar-Heinis
 */
package gov.nist.oar.distrib.datapackage;

import gov.nist.oar.distrib.DistributionException;

/**
 * An exception indicating that a request for a data package contains no actual files to include.
 */
public class EmptyBundleRequestException extends DistributionException {

    /**
     * Create an exception with an arbitrary message and an underlying cause
     */
    public EmptyBundleRequestException(String msg, Throwable cause) { super(msg, cause); }
    
    /**
     * Create an exception with an arbitrary message
     */
    public EmptyBundleRequestException(String msg) { this(msg, null); }
    
    /**
     * Create an exception with an underlying cause
     */
    public EmptyBundleRequestException(Throwable cause) {
        this("Empty bundle request ("+cause.getMessage()+")", cause); }
    
    /**
     * Create an exception with default message
     */
    public EmptyBundleRequestException() { this("Empty bundle request (request contains no files)"); }
    
}
