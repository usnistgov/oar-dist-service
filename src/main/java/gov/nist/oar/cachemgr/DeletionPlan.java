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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * an ordered list of objects to be removed from a cache volume in order to free up a 
 * specified amount of space in that volume.
 * 
 * Associated with the plan is a score that can be used to used to compare this plan with others
 * for other {@link gov.nist.oar.cachemgr.CacheVolume}s.  The lower the score, the more favorable 
 * it is.  The score is typically most strongly influenced by the number of files required to be 
 * removed (the fewer, the lower the score and the more favorable the plan); however, the 
 * implementation of the planner that produced the plan can fold in consideration of the 
 * "deletability" of files.  A "perfect" score is zero, generally given to a plan in which no files
 * need to be deleted.  Negative scores are allowed, and handlers of this instance can tweak the 
 * score as desired to affect its desirability.  
 *
 * Note that the number of bytes removed when {@link #execute()} is called is typically larger than the 
 * value of {@link #spaceNeeded}, as the latter represents what is needed.  The amount removed will be 
 * less if the volume has some unused space in it already.  The amount removed can be much more than 
 * is needed if the planner found the volume's used space exceeds its registered capacity.  
 */
public class DeletionPlan {

    /**
     * the CacheVolume instance that this plan is designed for.  This may be null if not known.
     */
    public CacheVolume volume = null;

    private String volname = null;

    /**
     * the amount of space needed by requester, in bytes.  
     */
    protected long spaceNeeded = 0L;

    /**
     * the amount of space this plan will free up, in bytes.  In other words, this is equal to the 
     * sum of the sizes of the objects identified to be deleted.  This value may be significantly 
     * different from {@link #spaceNeeded}.  It may be much less if there is already unused space 
     * available in the volume.  It may be much more if the total space used in the volume currently 
     * exceeds the registered capacity.
     */
    public long toBeRemoved = 0L;

    /**
     * the inventory database that this plan is based on.  This database will be updated when the 
     * plan is executed.
     */
    protected StorageInventoryDB inventory = null;

    /**
     * the list of data objects to remove.  This list should be sorted from most "deletable" to least,
     * may have more objects in than theorectically needed; this allows extra files to be deleted if 
     * there are failures deleting some of the files (other than if the are missing from the volume).
     */
    protected List<CacheObject> doomed = null;

    protected Logger log = null;

    /**
     * the score associated with this plan where a lower score is more desirable.  A "perfect"
     * score is 0 and should be assigned to plan in which no objects need to be deleted to 
     * free the specific space (given by {@link #spaceNeeded}).  Negative scores are allowed.  
     */
    public double score = 0.0;

    /**
     * initialize a deletion plan.  The provided list does not have to be complete: the caller 
     * can fill it later.  
     *
     * @param volname     the name of the cache volume this plan applies to
     * @param db          the inventory database that tracks the contents of the volume
     * @param objlist     the list that can/will hold the list of objects to be deleted.
     * @param target      the number of bytes that this plan plans to free up by remving the files
     *                       in objlist.
     * @param need        the number of bytes needed to be free (for a new object to be added).
     */
    public DeletionPlan(String volname, StorageInventoryDB db, List<CacheObject> objlist,
                        long target, long need)
    {
        this(volname, db, objlist, target, need, null);
    }

    /**
     * initialize a deletion plan.  The provided list does not have to be complete: the caller 
     * can fill it later.  
     *
     * @param volname     the name of the cache volume this plan applies to
     * @param db          the inventory database that tracks the contents of the volume
     * @param objlist     the list that can/will hold the list of objects to be deleted.
     * @param target      the number of bytes that this plan plans to free up by removing the files
     *                       in objlist.
     * @param need        the number of bytes needed to be free (for the new object to be added).
     * @param log         a Logger that this instance should use for messages
     */
    public DeletionPlan(String volname, StorageInventoryDB db, List<CacheObject> objlist,
                        long target, long need, Logger log)
    {
        this.volname = volname;
        inventory = db;
        doomed = objlist;
        toBeRemoved = target;
        spaceNeeded = need;

        if (log == null) log = LoggerFactory.getLogger(this.getClass());
        this.log = log;
    }

    /**
     * initialize a deletion plan.  The provided list does not have to be complete: the caller 
     * can fill it later.  
     *
     * @param vol         the cache volume this plan applies to
     * @param db          the inventory database that tracks the contents of the volume
     * @param objlist     the list that can/will hold the list of objects to be deleted.
     * @param target      the number of bytes that this plan plans to free up by removing the files
     *                       in objlist.
     * @param need        the number of bytes requested to be free (for the new object to be added).
     */
    public DeletionPlan(CacheVolume vol, StorageInventoryDB db, List<CacheObject> objlist,
                        long target, long need)
    {
        this(vol, db, objlist, target, need, null);
    }

    /**
     * initialize a deletion plan.  The provided list does not have to be complete: the caller 
     * can fill it later.  
     *
     * @param vol         the cache volume this plan applies to
     * @param db          the inventory database that tracks the contents of the volume
     * @param objlist     the list that can/will hold the list of objects to be deleted.
     * @param target      the number of bytes that this plan plans to free up by removing the files
     *                       in objlist.
     * @param need        the number of bytes requested to be free (for the new object to be added).
     * @param log         a Logger that this instance should use for messages
     */
    public DeletionPlan(CacheVolume vol, StorageInventoryDB db, List<CacheObject> objlist,
                        long target, long need, Logger log)
    {
        this(vol.getName(), db, objlist, target, need, log);
        volume = vol;
    }

    /**
     * return the name of the volume that this plan is to be applied to.
     */
    public String getVolumeName() {
        if (volume != null)
            return volume.getName();
        return volname;
    }

    /**
     * return the number of bytes that this plan expects to remove when the plan is executed.
     */
    public long getByteCountToBeRemoved() {
        return toBeRemoved;
    }

    /**
     * return the number of bytes that this plan expects to remove when the plan is executed.
     */
    public long getByteCountNeeded() {
        return spaceNeeded;
    }

    /**
     * return the list of CacheObjects that can be deleted as part of this plan.  The list may 
     * contain more objects than will actually be deleted when the plan is executed; some extras 
     * may be included at the end in case there is a problem removing earlier objects.  Note that 
     * manipulating this list will effectively change the plan!
     */
    public List<CacheObject> getDeletableObjects() { return doomed; }

    /**
     * synchronously execute the plan.  This will cause updates to be made to the inventory database.
     * This method will silently skip over files it fails to delete.  Generally, this method will not 
     * fail if it fails to delete an Object; it will just move on.  
     * @return long    the number of bytes actually freed as a result of execution of the plan
     * @throws IllegalStateException  if the volume field is null 
     * @throws StorageVolumeException   if there is a non-recoverable error with attempting to delete objects
     */
    public long execute() throws InventoryException, DeletionFailureException {
        if (volume == null)
            throw new IllegalStateException("No CacheVolume instance attached to this plan");

        synchronized (inventory) {
            if (inventory.getVolumeStatus(volume.getName()) < inventory.VOL_FOR_UPDATE)
                throw new IllegalStateException("CacheVolume "+volume.getName()+
                                                " not available for updates");

            // lock this volume out from having another plan applied to or created for it 
            inventory.setVolumeStatus(volume.getName(), inventory.VOL_FOR_GET);
        }

        synchronized (volume) {
            try {
                return _execute();
            }
            finally {
                // unlock this volume
                inventory.setVolumeStatus(volume.getName(), inventory.VOL_FOR_UPDATE);
            }
        }
    }

    /*
     * this implementation does not include locking
     */
    private long _execute() throws DeletionFailureException, InventoryException {
        long removed = 0L;
        log.info("Removing {} bytes via deletion plan on {}", getByteCountToBeRemoved(),
                 getVolumeName());

        // remove doomed objects:  go through list until enough space freed or until
        // exhausted.
        int fails = 0;
        for(CacheObject co : doomed) {
            try {
                if (removed > toBeRemoved)
                    break;
                try {
                    volume.remove(co.name);
                    removed += co.getSize();
                    fails = 0;
                } catch (ObjectNotFoundException ex) {
                    // we will assume that the inventory is out of sync; we'll let this slide
                }
                inventory.removeObject(volume.getName(), co.name);
            } catch (StorageVolumeException ex) {
                fails++;
                log.error("Problem executing deletion plan on volume, "+getVolumeName());
                if (fails > 10) {
                    log.error("Aborting plan after 10 consecutive failures");
                    throw new DeletionFailureException("Deletion plan got 10 failures in a row: "
                                                       + ex.getMessage(), ex);
                }
            }
        }
        
        return removed;
    }

    /**
     * execute the plan and return a reservation for the freed space.
     * @throws IllegalStateException  if the volume field is null 
     * @throws DeletionFailureException  if the plan execution was not successful in freeing enough space
     * @throws InventoryException     if a failure occurs while trying to create the reservation
     */
    public Reservation executeAndReserve() throws DeletionFailureException, InventoryException {
        if (volume == null)
            throw new IllegalStateException("No CacheVolume instance attached to this plan");

        synchronized (inventory) {
            if (inventory.getVolumeStatus(volume.getName()) < inventory.VOL_FOR_UPDATE)
                throw new IllegalStateException("CacheVolume "+volume.getName()+
                                                " not available for updates");

            // lock this volume out from having another plan applied to or created for it 
            inventory.setVolumeStatus(volume.getName(), inventory.VOL_FOR_GET);
        }

        synchronized (volume) {
            try {
                long removed = _execute();
                if (removed < toBeRemoved)
                    throw new DeletionFailureException("Deletion plan for "+volname+" proved insufficient: " +
                                                 Long.toString(toBeRemoved) + " bytes needed; removed only " +
                                                 Long.toString(removed));

                removed = inventory.getAvailableSpaceIn(volume.getName());
                if (removed < spaceNeeded)
                    throw new DeletionFailureException("After deleting, volume "+volname+
                                                       " still does not have enough space: "+
                                                 Long.toString(spaceNeeded) + " bytes needed; have only " +
                                                 Long.toString(removed));
                
                return Reservation.reservationFor(volume, inventory, spaceNeeded);
            }
            finally {
                // unlock this volume
                inventory.setVolumeStatus(volume.getName(), inventory.VOL_FOR_UPDATE);
            }
        }
    }

    /**
     * set the logger that should be used with this class (when reporting on execution).
     * This allows the client to choose the name associate with the log messages.
     * The input logger is ignored if it is null.
     */
    public void setLogger(Logger log) {
        if (log != null) this.log = log;
    }

}
