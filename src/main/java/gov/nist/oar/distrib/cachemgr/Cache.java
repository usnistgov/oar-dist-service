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

import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;

/**
 * a set of {@link gov.nist.oar.distrib.cachemgr.CacheVolume}s that can be used to (temporarily) store 
 * data objects.
 *
 * A data cache is useful in an environment where data products are stored on slow persistent
 * storage and where there is a limited amount of fast storage space suited for delivering that
 * data to users/applications.  Data caches can be set on the fast storage to house the most
 * recently or most requested data products.  A Cache class provides a means for moving data
 * objects in and out of the cache.
 * 
 * A Cache instance has at its disposal a set of {@link gov.nist.oar.distrib.cachemgr.CacheVolume}s.  
 * Encapsulated in the cache is a {@link gov.nist.oar.distrib.cachemgr.StorageInventoryDB} which tracks
 * which volume(s) an object is stored in (along with other metadata like size and checksums).  
 * Also encapsulated is a strategy for determining the best volume to place new objects; a big 
 * part of this strategy is figuring out what objects to get rid of to make room for a new
 * object.  The {@link #reserveSpace(long)} functions and the {@link gov.nist.oar.distrib.cachemgr.Reservation} 
 * they return are used to add new objects into the cache.  
 */
public abstract class Cache {

    protected LinkedList<ReservationListener> resvListeners = new LinkedList<ReservationListener>();
    protected LinkedList<SaveListener> saveListeners = new LinkedList<SaveListener>();
    protected LinkedList<DeletionListener> delListeners = new LinkedList<DeletionListener>();

    private String name = null;

    /**
     * create the base Cache
     * @param name   a name to give this cache.  In a system with multiple caches with different roles
     *               this provides a way to distinguish between the different caches in messages.
     */
    protected Cache(String name) { this.name = name; }

    /**
     * return the name given to this cache.  In a system with multiple caches with different roles
     * this provides a way to distinguish between the different caches in messages.
     */
    public String getName() { return name; }

    /**
     * return true if the data object with the given identifier is held in the cache
     * @param id   the identifier for the data object of interest.
     */
    public abstract boolean isCached(String id) throws CacheManagementException;

    /**
     * remove all copies of the data object with the given ID from the cache
     * @param id       the identifier for the data object of interest.
     */
    public abstract void uncache(String id) throws CacheManagementException;

    /**
     * return a CacheObject representation of a data object if it already exists in the cache;
     * otherwise, return null. 
     * @param id       the identifier for the data object of interest.
     */
    public abstract CacheObject findObject(String id) throws CacheManagementException;

    /**
     * return the CacheVolume with the given name or null if name does not exist.  Note that 
     * this assumes that volume names are unique; however, this interface does not guarantee 
     * it.  Implementations must either guarantee it or have a way of selecting a single 
     * volume by name.
     */
    protected abstract CacheVolume getVolume(String name) throws CacheManagementException;

    /**
     * return a reservation for a given amount of space.  
     * @param bytes        the amount of space to reserve
     * @param preferences  an and-ed set of bits indicating what the space will be used for.
     *                     The interpretation of the bits is implementation-specific.  The 
     *                     bits should be used to pick the optimal location for the an 
     *                     object to be put into the cache.
     * @return Reservation   an object that provides a claim on space; its interface should be 
     *                     used to deliver the bytes into the cache when the object is available.  
     */
    public abstract Reservation reserveSpace(long bytes, int preferences) throws CacheManagementException;

    /**
     * return a reservation for a given amount of space.  This method reserves the space
     * with no user-supplied preferences.  
     * @param bytes        the amount of space to reserve
     * @return Reservation   an object that provides a claim on space; its interface should be 
     *                     used to deliver the bytes into the cache when the object is available.  
     */
    public Reservation reserveSpace(long bytes) throws CacheManagementException {
        return this.reserveSpace(bytes, 0);
    }

    /**
     * add a Cache listener that will be notified when a reservation for space has been made on the cache
     */
    public void addReservationListener(ReservationListener listener) {
        addListenerTo(listener, resvListeners);
    }

    private <T> void addListenerTo(T listener, LinkedList<T> listeners) {
        synchronized (listeners) {
            if (listeners.contains(listener)) 
                // bop it to the front of the queue
                listeners.remove(listener);
            listeners.addFirst(listener);
        }
    }

    /**
     * add a Cache listener that will be notified when an object has been added to the cache
     */
    public void addSaveListener(SaveListener listener) {
        addListenerTo(listener, saveListeners);
    }

    /**
     * add a Cache listener that will be notified when an objects have been removed from the cache.
     * The notification is tied to the completion of a deletion plan and the listener is sent the full 
     * list of objects that were removed.  
     */
    public void addDeletionListener(DeletionListener listener) {
        addListenerTo(listener, delListeners);
    }

    /**
     * the interface for accepting notification about reservations for space in the cache.  This will 
     * get called after a reservation has been successfully made, just before it is returned to the 
     * requester.
     */
    public interface ReservationListener {
        /**
         * notify this listener of a reservation.  
         * @param cache     the Cache object where the reservation was requested
         * @param volume    the volume where space was reserved.  This may be null if the volume 
         *                    is unknown; however, it should not be. 
         * @param size      the amount of space reserved, in bytes
         * @return boolean  returning true means that the listener should be removed from the 
         *                  listeners list, preventing future notifications; if false is returned, 
         *                  the listener will continue to receive notifications.
         */
        public boolean reservationMade(Cache cache, CacheVolume volume, long size);
    }

    /**
     * the interface for accepting notifications about objects newly saved to the cache.  It is 
     * called after the {@link Reservation#saveAs(InputStream,String,String,JSONObject) Reservation.saveAs()} 
     * operation is complete.
     */
    public interface SaveListener {
        /**
         * notify this listener of an object being saved to the cache as a result of a reservation
         * @param cache     the Cache object where the reservation was requested
         * @param object    the object that was saved.  This {@link CacheObject}'s <code>volume</code> 
         *                  field will be non-null and correspond to the volume where it was saved.  
         * @return boolean  returning true means that the listener should be removed from the 
         *                  listeners list, preventing future notifications; if false is returned, 
         *                  the listener will continue to receive notifications.
         */
        public boolean objectSaved(Cache cache, CacheObject object);
    }

    /**
     * the interface for accepting notifications about deletions made to the cache.  
     */
    public interface DeletionListener {
        /**
         * notify this listener when objects have been removed from the cache as part of a request 
         * for space. 
         * @param cache    the Cache object where the reservation was requested
         * @param volume   the volume from which objects were deleted
         * @param deleted  a list of the identifiers for the objects deleted
         * @param freed    the total amount of space freed from all of the deletions
         * @return boolean  returning true means that the listener should be removed from the 
         *                  listeners list, preventing future notifications; if false is returned, 
         *                  the listener will continue to receive notifications.
         */
        public boolean objectsDeleted(Cache cache, CacheVolume volume, List<String> deleted, long freed);
    }

    /**
     * notify listeners that a reservation has been made against this Cache
     */
    protected void notifyReservationMade(CacheVolume volume, long size) {
        synchronized (resvListeners) {
            for(Iterator<ReservationListener> it = resvListeners.iterator();
                it.hasNext();)
            {
                try {
                    if (it.next().reservationMade(this, volume, size))
                        it.remove();
                } catch (RuntimeException ex) {
                    // log problem
                    warn("Reservation listener threw an error", ex);
                }
            }
        }
    }

    /**
     * warn about an error thrown by a listener
     */
    protected abstract void warn(String message, Exception exc);
        
    /**
     * notify listeners that objects have been deleted from this Cache
     */
    void notifyObjectsDeleted(CacheVolume volume, List<String> deleted, long freed) {
        synchronized (delListeners) {
            for(Iterator<DeletionListener> it = delListeners.iterator();
                it.hasNext();)
            {
                try { 
                    if (it.next().objectsDeleted(this, volume, deleted, freed))
                        it.remove();
                } catch (RuntimeException ex) {
                    // log problem
                    warn("Deletion listener threw an error", ex);
                }
            }
        }
    }

    /**
     * notify listeners that objects have been added from this Cache.  
     */
    void notifyObjectSaved(CacheObject object) {
        // this gets called from within the Reservation saveAs()
        synchronized (saveListeners) {
            for(Iterator<SaveListener> it = saveListeners.iterator();
                it.hasNext();)
            {
                try {
                    if (it.next().objectSaved(this, object))
                        it.remove();
                } catch (RuntimeException ex) {
                    // log problem
                    warn("Object-save listener threw an error", ex);
                }
            }
        }
    }
}
