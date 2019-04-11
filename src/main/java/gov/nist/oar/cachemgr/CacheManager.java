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
package gov.nist.oar.cachemgr;

import java.net.URL;

/**
 * An interface for managing a set of data file caches.  A data cache is useful in an environment 
 * where data products are stored on slow persistent storage and where there is a limited amount of 
 * fast storage space suited for delivering that data to users/applications.  Data caches can be set 
 * on the fast storage to house the most recently or most requested data products.  A CacheManager
 * is responsible for moving data in and out of one or more {@list gov.nist.oar.cachemgr.Cache}s.
 *
 * The key difference between a {@list gov.nist.oar.cachemgr.Cache} and a CacheManager knows how 
 * restore data from long term storage; this is done using {@link gov.nist.oar.cachemgr.Restorer}
 * instances, where different {@link gov.nist.oar.cachemgr.Restorer} classes might understand how to 
 * deal with different types of data files.  A CacheManager, in principle, can manage several different 
 * {@list gov.nist.oar.cachemgr.Cache}s where different caches are used for different purposes and/or
 * have different policies for cycling data (which the CacheManager understands).  The CacheManager 
 * manager may encapsulate other management activities not directly related delivering data (e.g. 
 * integrity checking, pre=caching, clean-up, etc.).  In contrast, a {@list gov.nist.oar.cachemgr.Cache} 
 * has very little knowledge of the application's specific needs: it puts data in, it streams data out.  
 */
public abstract class CacheManager {

    /**
     * return true if the data object with the given identifier is held in the cache
     * @param id   the identifier for the data object of interest.
     */
    public abstract boolean isCached(String id);

    /**
     * restore the data object with the given identifier into the cache.  This method is 
     * expected to operate synchronously.  
     * @param id       the identifier for the data object of interest.
     * @param recache  if true and the object is already in the cache, that object
     *                 will be replaced with a freshly restored version.  If false
     *                 and the object already exists, the method returns without
     *                 changing anything.
     * @returns boolean   true if a fresh copy of the data object was restored to disk; 
     *                 false otherwise.
     * @throws CacheManagementException  if a failure occurs that prevented caching of the 
     *                 data as requested.
     */
    public abstract boolean cache(String id, boolean recache) throws CacheManagementException;

    /**
     * restore the data object with the given identifier into the cache.  This method is 
     * expected to operate synchronously.  
     *
     * Whether the object will be recached by default is implementation-specific.  This 
     * default implementation will not recache a data object if it already exists in the 
     * cache.
     *
     * @param id       the identifier for the data object of interest.
     * @returns boolean   true if a fresh copy of the data object was restored to disk; 
     *                 false otherwise.
     * @throws CacheManagementException  if a failure occurs that prevented caching of the 
     *                 data as requested.
     */
    public boolean cache(String id) throws CacheManagementException {
        return this.cache(id, false);
    }

    /**
     * remove all copies of the data object with the given ID from the cache
     * @param id       the identifier for the data object of interest.
     */
    public abstract void uncache(String id) throws CacheManagementException;

    /**
     * return a CacheObject representation of a data object having a given identifier.
     * This method will restore the object into the cache if it does not already exist there.  
     * The CacheObject will include a reference to the CacheVolume the object is contained in.
     * @param id       the identifier for the data object of interest.
     */
    public CacheObject getObject(String id) throws CacheManagementException {
        this.cache(id, false);
        return findObject(id);
    }

    /**
     * return a CacheObject representation of a data object if it already exists in the cache;
     * otherwise, return null. 
     * @param id       the identifier for the data object of interest.
     */
    public abstract CacheObject findObject(String id);

    /**
     * return a URL that the object with the given name can be alternatively 
     * read from.  This allows for a potentially faster way to deliver a file
     * to web clients than via a Java stream copy.  Not all implementations may
     * support this.  If a URL is not available for the given identifier, null 
     * is returned
     * @param name       the name of the object to get
     * @returns boolean  True if the file existed in the volume; false if it was 
     *                       not found in this volume
     * @throws CacheVolumeException     if there is an internal error while trying to 
     *                                     remove the Object
     */
    public URL getRedirectFor(String id) throws CacheManagementException {
        CacheObject out = this.getObject(id);
        if (out.volume == null)
            throw new IllegalStateException(id + ": CacheObject is missing volume location");
        try {
            return out.volume.getRedirectFor(out.name);
        }
        catch (CacheVolumeException ex) {
            throw new CacheManagementException(id+": Unexpected error determining URL: "+ex.getMessage(),
                                               ex);
        }
        catch (UnsupportedOperationException ex) {
            return null;
        }
    }

}
