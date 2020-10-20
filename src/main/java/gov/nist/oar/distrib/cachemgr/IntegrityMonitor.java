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

import gov.nist.oar.distrib.StorageVolumeException;

import java.util.List;

/**
 * a class that monitors objects in a cache to ensure that they conform to configured integrity 
 * checks.  
 * <p>
 * The intent of this interface is that it is used by a {@link CacheManager} to monitor the integrity of 
 * files in the {@link Cache} (or {@link Cache}s) that it manages.  Thus, the monitor is expected to be 
 * connected to a cache (usually a single {@link Cache}, but could a collection of {@link Cache}s or part 
 * of a {@link Cache}) and has access to both its storage inventory database and the {@link CacheVolume} 
 * that make up the cache.  (The strong connection to a {@link Cache} is reflected the {@link BasicCache} 
 * method, {@link BasicCache#createIntegrityMonitor(List)}.)  It uses the database 
 * (typically through its {@link StorageInventoryDB} interface) to find objects that are in need of checking.  
 * Once it finds them, it can run configured checks on them.  When an object passes all its checks, this 
 * monitor will update its record accordingly (with a date of success) in the database; thus, the object 
 * will not need further checking for some period of time.  
 * <p>
 * This whole process can be accomplished via calls to 
 * {@link #findCorruptedObjects(int,List,boolean)}; 
 * however, if cache manager would like to take tighter control over the process--i.e. to delete and restore 
 * individual {@link CacheObject}s as they are found to be corrupted--the manager can separately find the 
 * objects to be checked via {@link #selectObjectsToBeChecked(int)} and check them individually via 
 * {@link #check(CacheObject)}.
 * 
 * @see StorageInventoryDB
 * @see CacheVolume
 * @see BasicCache
 */
public interface IntegrityMonitor {

    /**
     * return the name of the cache that this instance monitors.  Typically, this equals the value 
     * returned by the {@link Cache}'s {@link Cache#getName() getName()} method.
     */
    public String getCacheName();

    /**
     * Check the given object from the cache by applying all configured integrity checks.  If all 
     * checks pass, the object's entry in the inventory database will be updated accordingly.
     * <p> 
     * The given object must have its {@link CacheObject#volume} field specified and that volume must be
     * registered in the storage inventory database.  
     * @param co    the object to check
     * @throws IntegrityException       if the check was executed successfully but found problem with the 
     *                                  object.
     * @throws ObjectNotFoundException  if the object is not found (perhaps because it was removed) in 
     *                                  volume indicated in the CacheObject
     * @throws StorageVolumeException   if some other error occurs while trying to access the object within 
     *                                  the storage volume
     * @throws CacheManagementException if the check could not be run successfully due to some error other 
     *                                  than a problem access the object from storage.
     * @throws IllegalArgumentException if the given {@link CacheObject}'s {@link CacheObject#volume volume}
     *                                  field is null.
     */
    public void check(CacheObject co)
        throws IntegrityException, StorageVolumeException, CacheManagementException;

    /**
     * Return a list of CacheObjects that are most in need of being checked.  The list will be ordered 
     * from most in need to less than in need.  (Ordering is controlled by the {@link SelectionStrategy} 
     * used, but typically the list will be ordered based on the time the object was last checked, oldest
     * to youngest.)  This implementation will use the default {@link SelectionStrategy} set at construction 
     * time.
     * @param max   the maximum number of objects to return.  
     */
    public List<CacheObject> selectObjectsToBeChecked(int max) throws InventoryException;

    /**
     * Return a list of CacheObjects that are most in need of being checked.  The list will be ordered 
     * according to the given {@link SelectionStrategy}.
     */
    public List<CacheObject> selectObjectsToBeChecked(SelectionStrategy strat) throws InventoryException;

    /**
     * Check the given list of objects in the cache and return a list of those that fail the tests.
     * An object is included in the {@code failed} list if any of the checks applied to it throw an 
     * {@link IntegrityException}.  Once an object fails a check, no further checking is done on it.  
     * All other exceptions are generallly ignored except when there are 
     * an excessive number of {@link StorageVolumeException} exceptions (although these other exceptions
     * may cause error messages to be logged). 
     * <p>
     * This method basically applies {@link #check(CacheObject)} to a list of objects.
     * @param cos           the list of objects to check.
     * @param failed        an editable list to which this method can add the {@link CacheObject}s that fail 
     *                      any integrity check. 
     * @param deleteOnFail  if true, then an object that fails its checks should be immediately removed from
     *                      it cache volume.  
     * @return int -- the number of successfully check objects from the input list, {@code cos}
     */
    public int selectCorruptedObjects(List<CacheObject> cos, List<CacheObject> failed, boolean deleteOnFail)
        throws StorageVolumeException, CacheManagementException;

    /**
     * Look for objects in this cache that require an integrity check and check them to find corrupted 
     * objects.  This method will update the checked status of each object that successfully passes all 
     * integrity checks; thus, with each call to this method, a different set of objects will be checked
     * (except when the number of objects in the cache is small compared to {@code max}.  
     * <p>
     * An object is included in the {@code failed} list if any of the checks applied to it throw an 
     * {@link IntegrityException}.  Once an object fails a check, no further checking is done on it.  
     * All other exceptions are generallly ignored except when there are 
     * an excessive number of {@link StorageVolumeException} exceptions (although these other exceptions
     * may cause error messages to be logged). 
     * <p>
     * This method basically combines {@link #selectObjectsToBeChecked(int)} and 
     * {@link #selectCorruptedObjects(List,List,boolean)} into one call.  
     * @param max     the maximum number of objects to check
     * @param failed  an editable list to which this method can add the {@link CacheObject}s that fail 
     *                any integrity check. 
     * @param deleteOnFail  if true, then an object that fails its checks should be immediately removed from
     *                      it cache volume.  
     * @return int -- the number of successfully check objects from the input list, {@code cos}
     */
    public int findCorruptedObjects(int max, List<CacheObject> failed, boolean deleteOnFail)
        throws InventoryException, StorageVolumeException, CacheManagementException;
}
