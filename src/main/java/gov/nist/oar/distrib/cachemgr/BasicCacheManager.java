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
import gov.nist.oar.distrib.ObjectNotFoundException;

import org.json.JSONObject;
import org.json.JSONException;

import java.net.URL;

/**
 * a basic implementation of the base {@link gov.nist.oar.distrib.cachemgr.CacheManager} class.
 * <p>
 * This implementation assembles a <code>CacheManager</code> at construction time when provided with 
 * a {@link gov.nist.oar.distrib.cachemgr.Restorer} and a {@link gov.nist.oar.distrib.cachemgr.Cache}.  
 */
public abstract class BasicCacheManager extends CacheManager {

    /**
     * The set of cache volumes for temporary storage of data objects
     */
    protected Cache theCache = null;

    /**
     * The {@link Restorer} to use to restor data objects from long-term storage.  (The 
     * {@link LongTermStorage} instance is expected to be embedded in this restorer.)
     */
    protected Restorer restorer = null;

    /**
     * create the CacheManager by wrapping a Cache and Restorer.
     */
    public BasicCacheManager(Cache cache, Restorer rest) {
        theCache = cache;
        restorer = rest;
    }

    /**
     * return true if the data object with the given identifier is held in the cache
     * @param id   the identifier for the data object of interest.
     */
    @Override
    public boolean isCached(String id) throws CacheManagementException {
        return theCache.isCached(id);
    }

    /**
     * return a CacheObject representation of a data object if it already exists in the cache;
     * otherwise, return null. 
     * @param id       the identifier for the data object of interest.
     */
    @Override
    public CacheObject findObject(String id) throws CacheManagementException {
        return theCache.findObject(id);
    }

    /**
     * remove all copies of the data object with the given ID from the cache
     * @param id       the identifier for the data object of interest.
     */
    @Override
    public void uncache(String id) throws CacheManagementException {
        theCache.uncache(id);
    }

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
     * @throws ObjectNotFoundException   if the idenifier, <code>id</code> does not exist.
     */
    @Override
    public boolean cache(String id, boolean recache) throws CacheManagementException {
        if (! recache && theCache.isCached(id))
            return false;

        try {
            long sz = restorer.getSizeOf(id);       // may throw ObjectNotFoundException
            Reservation resv = theCache.reserveSpace(sz);
            String cvname = determineCacheObjectName(resv.getVolumeName(), id);
            try {
                // restorer would add size if its not here, but we'll add it here in case it's
                // expensive to obtain.  (We'll let it add the checksum on its own.)
                JSONObject md = new JSONObject();
                md.put("size", sz);   

                restorer.restoreObject(id, resv, cvname, md);
            }
            finally {
                if (resv != null && resv.getSize() > 0) resv.drop();
            }
        }
        catch (JSONException ex) {
            throw new RestorationException("Unexpected failure saving metadata for id="+id+": "+
                                           ex.getMessage(), ex);
        }
        catch (ObjectNotFoundException ex) {
            throw new RestorationTargetNotFoundException(ex, id);
        }
        catch (StorageVolumeException ex) {
            throw new CacheVolumeException(ex);
        }
        
        return true;
    }

    /**
     * create a name for a data object within a particular {@link CacheVolume}.  
     */
    protected abstract String determineCacheObjectName(String volname, String id)
        throws CacheManagementException;
}

