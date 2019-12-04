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
 * an interface for determining deletion plans that clear up space in volumes tracked by a storage
 * inventory database.  The implementation has a specific algorithm embedded in it for selecting 
 * the files to delete and giving the plan a score indicating how relatively desirable it is 
 * compared to other plans.  
 *
 * Creating a deletion plan for a volume involves calculating a deletability score for each object 
 * in the volume and then sorting the list of deletable objects based on the assigned score.  The 
 * calculation might have built into it a preference for deleting, say, older and/or larger files.  
 * A plan is then assigned an overall score for how desirable the plan is; a more desirable plan 
 * may favor, say, deleting older files or deleting fewer files (for faster plan execution).  
 *
 * In general, a deletion planner will be dependent on the data model built into the storage 
 * inventory database used.  (For this reason, implementations included in this package can be 
 * found in {@link gov.nist.oar.cachemgr.inventory}.)  Some planner implementations may be built 
 * directly into {@link gov.nist.oar.cachemgr.StorageInventoryDB} implementation if the sorting 
 * and scoring of plans are implemented via database functions and queries.
 */
public interface DeletionPlanner {

    /**
     * return a deletion plan for a particular volume
     */
    DeletionPlan createDeletionPlanFor(String volname, long size) throws InventoryException;

    /**
     * return a list of deletion plans that can free up space of a requested size, ordered
     * from most-favorable to least favorable.  
     *
     * Typically, the caller would execute the first plan in the set; if that failed, the caller 
     * could try the next one in the list.  Generally, this method should not return plans
     * for volumes whose status does not permit deletions.  
     */
    List<DeletionPlan> orderDeletionPlans(long size) throws InventoryException;
}
