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
 * 
 * @author: Raymond Plante
 */
package gov.nist.oar.distrib.cachemgr.pdr;

/**
 * an interface that defines Cache preference codes used by the NIST Public Data Repository (PDR).
 * The values defined here are assigned to cache volumes used in the PDR Cache to indicated the purpose 
 * of the cache.  As preferences can be bit-wise AND-ed together, a volume can be assigned more than one 
 * code.  
 * 
 * @see gov.nist.oar.distrib.cachemgr.ConfigurableCache
 */
public interface PDRCacheRoles {

    /**
     * a preference code assigned to cache volumes that can provide general purpose or default storage
     */
    int ROLE_GENERAL_PURPOSE = 1;

    /**
     * a preference code assigned to cache volumes that provide faster access to objects
     */
    int ROLE_FAST_ACCESS = 2;

    /**
     * a preference code assigned to cache volumes intended for holding smaller objects
     */
    int ROLE_SMALL_OBJECTS = 4;

    /**
     * a preference code assigned to cache volumes intended for holding larger objects
     */
    int ROLE_LARGE_OBJECTS = 8;

    /**
     * a preference code assigned to cache volumes intended to hold data files from deprecated versions
     * of datasets
     */
    int ROLE_OLD_VERSIONS = 16;

    /**
     * a preference code assigned to cache volumes intended to hold Restricted Public Data (RPD)
     */
    int ROLE_RESTRICTED_DATA = 32;

    /**
     * a preference code assigned to cache volumes intended to hold deprecated versions of Restricted Public Data (RPD)
     */
    int ROLE_OLD_RESTRICTED_DATA = 64;

}
