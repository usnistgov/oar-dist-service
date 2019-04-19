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
 * Note that the number of bytes removed when {@link execute} is called is typically larger than the 
 * value of {@link byteCount}, as the latter represents what is needed.  The amount removed will be 
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
     * different from {@link spaceNeeded}.  It may be much less if there is already unused space 
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
    List<CacheObject> doomed = null;

    /**
     * the score associated with this plan where a lower score is more desirable.  A "perfect"
     * score is 0 and should be assigned to plan in which no objects need to be deleted to 
     * free the specific space (given by {@link byteCount}).  Negative scores are allowed.  
     */
    public double score = 0.0;

    /**
     * return the name of the volume that this plan is to be applied to.
     */
    public String getVolumeName() {
        if (volume != null)
            return volume.getName();
        return volname;
    }

    /**
     * synchronously execute the plan.  This will cause updates to be made to the inventory database.
     * This method will silently skip over files it fails to delete.  Generally, this method will not 
     * fail if it fails to delete an Object; it will just move on.  
     * @return long    the number of bytes actually freed as a result of execution of the plan
     * @throws IllegalStateException  if the volume field is null 
     * @throws CacheVolumeException   if there is a non-recoverable error with attempting to delete objects
     */
    public long execute() throws InventoryException, DeletionFailureException {
        if (volume == null)
            throw new IllegalStateException("No CacheVolume instance attached to this plan");

        long removed = 0L;
        synchronized (inventory) {
            if (inventory.getVolumeStatus(volume.getName()) >= inventory.VOL_FOR_UPDATE)
                throw new IllegalStateException("CacheVolume "+volume.getName()+
                                                " not available for updates");

            // remove doomed objects:  go through list until enough space freed or until
            // exhausted.  
            for(CacheObject co : doomed) {
                try {
                    if (removed > spaceNeeded)
                        break;
                    volume.remove(co.name);
                    inventory.removeObject(volume.getName(), co.name);
                    removed -= co.getSize();
                } catch (CacheVolumeException ex) {
                    // log failure?
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
        synchronized (inventory) {
            long removed = execute();
            if (removed < toBeRemoved)
                throw new DeletionFailureException("Deletion plan for "+volname+" proved insufficient: " +
                                             Long.toString(toBeRemoved) + " bytes needed; removed only " +
                                             Long.toString(removed));
            removed = inventory.getAvailableSpace().get(volume.getName());
            if (removed < spaceNeeded)
                throw new DeletionFailureException("After deleting, volume "+volname+
                                                   " still does not have enough space: "+
                                             Long.toString(spaceNeeded) + " bytes needed; have only " +
                                             Long.toString(removed));
            return Reservation.reservationFor(volume, inventory, spaceNeeded);
        }
    }

}
