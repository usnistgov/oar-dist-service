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
 */
package gov.nist.oar.distrib;

import gov.nist.oar.distrib.DistributionException;

/**
 * an exception indicating the request for a non-existent resource (or particular version of 
 * an existing resource).
 */
public class ResourceNotFoundException extends DistributionException {

    private static final long serialVersionUID = 1L;

    /**
     * the identifer for the resource
     */
    public String id = null;

    /**
     * the version of the resource requested.  This should be null if the resource itself 
     * does not exist rather than a requested version.
     */
    public String version = null;

    /**
     * initialize the exception
     * @param message   the description of the problem
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * indicate that a particular version of the resource cannot be found
     * @param message   the description of the problem
     * @param id        the identifier used to find the resource (can be null)
     * @param version   the requested version of the resource (should be null if the ID
     *                  does not exist).
     */
    public ResourceNotFoundException(String message, String id, String version) {
        super(message);
        this.id = id;
        this.version = version;
    }

    /**
     * indicate that a resource with the given ID cannot be found
     * @param message   the description of the problem
     * @param id        the identifier used to find the resource (can be null)
     */
    public ResourceNotFoundException(String message, String id) {
        this(message, id, null);
    }

    /**
     * create an instance for a given identifier
     */
    public static ResourceNotFoundException forID(String id) {
        return new ResourceNotFoundException("Resource not found with ID="+id, id);
    }

    /**
     * create an instance for a given identifier and version
     */
    public static ResourceNotFoundException forID(String id, String version) {
        return new ResourceNotFoundException("Version "+version+" not found for resource with ID="+id, 
                                             id, version);
    }
}


