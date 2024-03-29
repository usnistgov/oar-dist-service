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
package gov.nist.oar.distrib.cachemgr;

import gov.nist.oar.distrib.StorageVolumeException;

import java.net.URL;

/**
 * An interface for managing a set of data file caches.  A data cache is useful in an environment 
 * where data products are stored on slow persistent storage and where there is a limited amount of 
 * fast storage space suited for delivering that data to users/applications.  Data caches can be set 
 * on the fast storage to house the most recently or most requested data products.  A CacheManager
 * is responsible for moving data in and out of one or more {@link gov.nist.oar.distrib.cachemgr.Cache}s.
 * <p>
 * The key difference between a {@link gov.nist.oar.distrib.cachemgr.Cache} and a CacheManager knows how 
 * restore data from long term storage; this is done using {@link gov.nist.oar.distrib.cachemgr.Restorer}
 * instances, where different {@link gov.nist.oar.distrib.cachemgr.Restorer} classes might understand how to 
 * deal with different types of data files.  A CacheManager, in principle, can manage several different 
 * {@link gov.nist.oar.distrib.cachemgr.Cache}s where different caches are used for different purposes and/or
 * have different policies for cycling data (which the CacheManager understands).  The CacheManager 
 * manager may encapsulate other management activities not directly related delivering data (e.g. 
 * integrity checking, pre=caching, clean-up, etc.).  In contrast, a {@link gov.nist.oar.distrib.cachemgr.Cache} 
 * has very little knowledge of the application's specific needs: it puts data in, it streams data out.  
 */
public abstract class CacheManager {

    /**
     * return true if the data object with the given identifier is held in the cache
     * @param id   the identifier for the data object of interest.
     */
    public abstract boolean isCached(String id) throws CacheManagementException;

    /**
     * restore the data object with the given identifier into the cache.  This method is 
     * expected to operate synchronously.  
     * @param id       the identifier for the data object of interest.
     * @param prefs    an AND-ed set of preferences for determining where (or how) to 
     *                 cache the object.  Generally, the values are implementation-specific 
     *                 (see {@link gov.nist.oar.distrib.cachemgr.pdr.PDRCacheRoles} as an
     *                 example set).  A non-positive number indicates that default preferences
     *                 should be applied (see {@link #getDefaultPreferencesFor(String,long)}).
     * @param recache  if true and the object is already in the cache, that object
     *                 will be replaced with a freshly restored version.  If false
     *                 and the object already exists, the method returns without
     *                 changing anything.
     * @return boolean   true if a fresh copy of the data object was restored to disk; 
     *                 false otherwise.
     * @throws CacheManagementException  if a failure occurs that prevented caching of the 
     *                 data as requested.
     */
    public abstract boolean cache(String id, int prefs, boolean recache) throws CacheManagementException;

    /**
     * restore the data object with the given identifier into the cache.  This method is 
     * expected to operate synchronously.  
     * @param id       the identifier for the data object of interest.
     * @param recache  if true and the object is already in the cache, that object
     *                 will be replaced with a freshly restored version.  If false
     *                 and the object already exists, the method returns without
     *                 changing anything.
     * @return boolean   true if a fresh copy of the data object was restored to disk; 
     *                 false otherwise.
     * @throws CacheManagementException  if a failure occurs that prevented caching of the 
     *                 data as requested.
     */
    public boolean cache(String id, boolean recache) throws CacheManagementException {
        return cache(id, 0, recache);
    }

    /**
     * restore the data object with the given identifier into the cache.  This method is 
     * expected to operate synchronously.  
     * <p>
     * Whether the object will be recached by default is implementation-specific.  This 
     * default implementation will not recache a data object if it already exists in the 
     * cache.
     *
     * @param id       the identifier for the data object of interest.
     * @param prefs    an AND-ed set of preferences for determining where (or how) to 
     *                 cache the object.  Generally, the values are implementation-specific 
     *                 (see {@link gov.nist.oar.distrib.cachemgr.pdr.PDRCacheRoles} as an
     *                 example set).  Zero indicates no preferences.  
     * @return boolean   true if a fresh copy of the data object was restored to disk; 
     *                 false otherwise.
     * @throws CacheManagementException  if a failure occurs that prevented caching of the 
     *                 data as requested.
     */
    public boolean cache(String id, int prefs) throws CacheManagementException {
        return cache(id, prefs, false);
    }

    /**
     * restore the data object with the given identifier into the cache.  This method is 
     * expected to operate synchronously.  
     * <p>
     * Whether the object will be recached by default is implementation-specific.  This 
     * default implementation will not recache a data object if it already exists in the 
     * cache.
     *
     * @param id       the identifier for the data object of interest.
     * @return boolean   true if a fresh copy of the data object was restored to disk; 
     *                 false otherwise.
     * @throws CacheManagementException  if a failure occurs that prevented caching of the 
     *                 data as requested.
     */
    public boolean cache(String id) throws CacheManagementException {
        return this.cache(id, false);
    }

    /**
     * conditionally cache an object or objects associated with the given identifier in an 
     * implementation-specific way.  The given identifier is not required to exist; the implementation 
     * is free to interpret it as it chooses; however, it should not raise an exception if the identifier 
     * does not exist or is otherwise unrecognizable.  The implementation may choose <i>not</i> to cache any 
     * data at all, or it may choose to defer the caching; it is not possible to determine with this function
     * what the implementation ultimately chose to handle the request.
     * <p>
     * This method allows a CacheManager to strategically cache some user-requested data as well as other 
     * related data--e.g. other files in the same dataset collection.  This implementation simply interprets
     * the identifier as an object identifier, and attempts to cache it; if it already exists in the cache,
     * it is not recached.  
     * @param id       an identifier for data to be cached.  This does not have to be specifically an
     *                 object identifier; its interpretation is kindly implementation-specific.
     * @param prefs    an AND-ed set of preferences for determining where (or how) to 
     *                 cache the object.  Generally, the values are implementation-specific 
     *                 (see {@link gov.nist.oar.distrib.cachemgr.pdr.PDRCacheRoles} as an
     *                 example set).  Zero indicates no preferences.  
     * @throws CacheManagementException   if the implementation has chosen to cache something but was 
     *                 unable to due to an internal cache failure.
     */
    public void optimallyCache(String id, int prefs) throws CacheManagementException {
        try {
            cache(id, prefs, false);
        }
        catch (RestorationTargetNotFoundException ex) { /* don't care */ }
    }

    /**
     * conditionally cache an object or objects associated with the given identifier in an 
     * implementation-specific way.  The given identifier is not required to exist; the implementation 
     * is free to interpret it as it chooses; however, it should not raise an exception if the identifier 
     * does not exist or is otherwise unrecognizable.  The implementation may choose <i>not</i> to cache any 
     * data at all, or it may choose to defer the caching; it is not possible to determine with this function
     * what the implementation ultimately chose to handle the request.
     * <p>
     * This method allows a CacheManager to strategically cache some user-requested data as well as other 
     * related data--e.g. other files in the same dataset collection.  This implementation simply interprets
     * the identifier as an object identifier, and attempts to cache it; if it already exists in the cache,
     * it is not recached.  
     * @param id       an identifier for data to be cached.  This does not have to be specifically an
     *                 object identifier; its interpretation is kindly implementation-specific.
     * @throws CacheManagementException   if the implementation has chosen to cache something but was 
     *                 unable to due to an internal cache failure.
     */
    public void optimallyCache(String id) throws CacheManagementException {  optimallyCache(id, 0);  }

    /**
     * return a set of caching preferences for an object with the given identifier and size
     * to be applied by {@link #cache(String)} when preferences are not specified.  Other internal 
     * processes may alter those preferences as more is learned about the object during restoration. 
     * The default set returned here is expected to reflect the specific cache manager implementation
     * and/or the configured internal cache.  
     * <p>
     * This base implementation simply returns zero--no preferences.
     * @param id     the identifier for the object being cached
     * @param size   the size of the object in bytes; if negative, the size is not known
     * @return int -- an ANDed set of caching preferences, or zero if no preferences are applicable
     */
    public int getDefaultPreferencesFor(String id, long size) {
        return 0;
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
     * <p>
     * Note that this default implementation transparently call {@link #confirmAccessOf(CacheObject)}. 
     * @param id       the identifier for the data object of interest.
     */
    public CacheObject getObject(String id) throws CacheManagementException {
        CacheObject out;
        boolean newlycached = this.cache(id, false);
            
        out = findObject(id);
        if (! newlycached) confirmAccessOf(out);
        return out;
    }

    /**
     * return a CacheObject representation of a data object if it already exists in the cache;
     * otherwise, return null. 
     * @param id       the identifier for the data object of interest.
     */
    public abstract CacheObject findObject(String id) throws CacheManagementException;

    /**
     * indicate that a given object was actually used.  It is expected that the given object 
     * was returned by {@link #findObject(String)}; however, it is not assumed that the object
     * underlying the returned {@link CacheObject} was actually accessed, so this method provides 
     * the client a way to indicate it was actually accessed (e.g. delivered to a user)
     * <p>
     * Note that the default implementation of {@link getObject(String)} will call this method 
     * automatically.  
     * @return boolean      false if the objname is not (e.g. no longer) stored in the cache.
     */
    public abstract boolean confirmAccessOf(CacheObject obj) throws CacheManagementException;

    /**
     * return a URL that the object with the given name can be alternatively 
     * read from.  This allows for a potentially faster way to deliver a file
     * to web clients than via a Java stream copy.  Not all implementations may
     * support this.  If a URL is not available for the given identifier, null 
     * is returned.
     * <p>
     * Note that this default implementation transparently call {@link #confirmAccessOf(CacheObject)}. 
     * @param id       the identifier of the object to get
     * @return URL     the URL from which the object can be streamed 
     * @throws CacheManagementException   if there is an internal error while trying to 
     *                                    locate the object
     */
    public URL getRedirectFor(String id) throws CacheManagementException {
        CacheObject out = this.getObject(id);
        if (out.volume == null)
            throw new IllegalStateException(id + ": CacheObject is missing volume location");
        try {
            return out.volume.getRedirectFor(out.name);
        }
        catch (StorageVolumeException ex) {
            throw new CacheManagementException(id+": Unexpected error determining URL: "+ex.getMessage(),
                                               ex);
        }
        catch (UnsupportedOperationException ex) {
            return null;
        }
    }

}
