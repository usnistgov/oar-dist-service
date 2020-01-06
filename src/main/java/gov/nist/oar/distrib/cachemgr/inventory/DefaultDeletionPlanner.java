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
package gov.nist.oar.distrib.cachemgr.inventory;

import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.DeletionPlanner;
import gov.nist.oar.distrib.cachemgr.DeletionPlan;
import gov.nist.oar.distrib.cachemgr.SelectionStrategy;
import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.VolumeNotFoundException;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
 * a fully functional implementation of the {@link gov.nist.oar.distrib.cachemgr.DeletionPlanner} interface.
 * <p>
 * This class implements the creation of {@link gov.nist.oar.distrib.cachemgr.DeletionPlan}s and scoring them
 * in an order expected to be most desirable and performant.  It assumes a formula for scoring a plans 
 * over all fitness that is the inverse of the average score applied to each of the files in the plan 
 * (more specifically, the subset of files in the plan necessary to create the desired space).  This is 
 * intended to favor plans with the fewest number of files to remove.  This class does not, on the 
 * other hand, assume the formula for scoring individual files to be deleted.  Instead, this class is 
 * configured with {@link gov.nist.oar.distrib.cachemgr.SelectionStrategy} instances, (on a per cache volume 
 * basis, if desired) that is used to score individual files.  
 * <p>
 * This implementation can be subclassed to adjust its behavior.  In particular, the 
 * {@link #calculatePlanScore(Collection,double,double) calculatePlanScore()} method can be 
 * overridden to customize the overall scoring of plans.  
 */
public class DefaultDeletionPlanner implements DeletionPlanner {

    protected StorageInventoryDB invdb = null;
    protected Map<String, CacheVolume> volumes = null;
    protected Map<String, SizeLimitedSelectionStrategy> strategies = null;

    /**
     * a fractional overhead to apply to the total size requested in queries 
     * to the inventory when obtaining a list of viable files to delete.  
     * Asking the inventory for more files than are necessary provides some 
     * extra files to delete unless there are problem removing some of the 
     * files when the plan is executed.  
     * <p>
     * The default value is 20%.   
     */
    protected double selheadroom = 0.2;

    /**
     * a fractional overhead to apply to requested space to be freed.  This overhead has two 
     * benefits.  First, it allows for some inaccuracies in the size of the files registered 
     * in the inventory.  Second, it helps maintain at least a small amount of empty space 
     * available in the cache.  If a volume is packed to maximum capacity, it is more vulnerable 
     * to errors like race conditions, performance problems, etc.  A little empty space can 
     * eliviate that risk.
     * <p>
     * The default value is 2%.   
     */
    protected double delheadroom = 0.02;

    protected Logger log = null;

    /**
     * create the planner
     * @param db         the inventory database that contains information on the contents of the cache 
     *                     volumes
     * @param vols       a list of the volumes that should be examined for available space.
     * @param strategy   the selection strategy to use to select deletable files
     */
    public DefaultDeletionPlanner(StorageInventoryDB db, Collection<CacheVolume> vols,
                                  SizeLimitedSelectionStrategy strategy, Logger log)
    {
        invdb = db;
        volumes = new HashMap<String, CacheVolume>(vols.size());
        strategies = new HashMap<String, SizeLimitedSelectionStrategy>(vols.size());
        for (CacheVolume cv : vols) {
            volumes.put(cv.getName(), cv);
            strategies.put(cv.getName(), strategy);
        }

        if (log == null) log = LoggerFactory.getLogger(this.getClass());
        this.log = log;
    }

    public DefaultDeletionPlanner(StorageInventoryDB db, Collection<CacheVolume> vols,
                                  SizeLimitedSelectionStrategy strategy)
    {
        this(db, vols, strategy, null);
    }
    
    public DefaultDeletionPlanner(StorageInventoryDB db, Collection<CacheVolume> vols,
                                  Map<String, SizeLimitedSelectionStrategy> strats,
                                  SizeLimitedSelectionStrategy defStrategy)
    {
        this(db, vols, strats, defStrategy, null);
    }

    public DefaultDeletionPlanner(StorageInventoryDB db, Collection<CacheVolume> vols,
                                  Map<String, SizeLimitedSelectionStrategy> strats,
                                  SizeLimitedSelectionStrategy defStrategy, Logger log)
    {
        this(db, vols, defStrategy, log);
        for (String name : strats.keySet())
            strategies.put(name, strats.get(name));
    }

    /**
     * return a deletion plan for a particular volume.  If null is returned, a viable plan--i.e.
     * one that can provide the requested space--was not possible.
     */
    public DeletionPlan createDeletionPlanFor(CacheVolume cv, long size) 
        throws InventoryException
    {
        String volname = cv.getName();
        DeletionPlan out = null;
        if (! invdb.volumes().contains(cv.getName()))
            throw new InventoryException(cv.getName() + ": Not a registered volume");
        
        // first determine if the volume already has the space available within it
        long avail = invdb.getAvailableSpaceIn(volname);
        if (avail > (1 + delheadroom) * size) {
            out = new DeletionPlan(cv, invdb, new ArrayList<CacheObject>(), 0L, size);
            out.score = 0.0;
            return out;
        }
        long removeBytes = Math.round((1 + delheadroom) * size) - avail;

        // There's not enough free space, we need to make some.
        List<CacheObject> selected = new ArrayList<CacheObject>();
        SizeLimitedSelectionStrategy strat =
            getStrategyFor(volname, Math.round((1 + selheadroom) * (size - avail)));
        List<CacheObject> selobjs = invdb.selectObjectsFrom(volname, strat);
        for (CacheObject co : selobjs) {
            if (strat.score(co) > 0.0)
                selected.add(co);
        }

        if (strat.getTotalSize() < removeBytes)
            // Can't create a viable plan with this volume
            // (size is probably too big or there's not enough removable stuff in it)
            return null;

        // Prep the plan for output
        strat.sort(selected);
        out = new DeletionPlan(volname, invdb, selected, removeBytes, size);

        // calculate a score
        out.score = calculatePlanScore(selected, size, avail);

        return out;
    }

    /**
     * calculate an overall score for the selected cache objects as a plan for creating 
     * the given space.  Better plans have lower values, where 0 is perfect.  
     */
    protected double calculatePlanScore(Collection<CacheObject> selected, long size, long avail) {
        long sumsz = 0L;
        double sumscr = 0.0;
        long n = 0;
        long removeBytes = Math.round((1 + delheadroom) * size) - avail;
        for (CacheObject co : selected) {
            sumsz += co.getSize();
            sumscr += co.score;
            n++;
            if (sumsz > removeBytes)
                break;
        }
        
        return n / sumscr;  // inverse of the average score
    }

    /**
     * return a SelectionStrategy for the cache volume with the given configured for 
     * a particular size limit.  
     * <p>
     * The size provided here is not how much additional space is required to hold a file but rather 
     * how much capacity to select for.  This size is usually larger to allow for slop in case either
     * there are errors in recorded size values or if a file cannot be removed when it comes time to 
     * do so.  
     */
    protected SizeLimitedSelectionStrategy getStrategyFor(String volname, long size) {
        SizeLimitedSelectionStrategy out = strategies.get(volname);
        if (out != null)
            out = out.newForSize(size);
        return out;
    }

    /*
     * return a list of deletion plans that can free up space of a requested size, ordered
     * from most-favorable to least favorable.  
     * <p>
     * Typically, the caller would execute the first plan in the set; if that failed, the caller 
     * could try the next one in the list.  Generally, this method should not return plans
     * for volumes whose status does not permit deletions.  
     *
     * @param size    the amount of space desired (in bytes)
     * @deprecated
    public List<DeletionPlan> orderDeletionPlans(long size)
        throws CacheManagementException, InventoryException
    {
        return this._orderDeletionPlans(size, volumes.values(), false);
    }
     */

    /**
     * return a list of deletion plans that can free up space of a requested size, ordered
     * from most-favorable to least favorable.  
     * <p>
     * Typically, the caller would execute the first plan in the set; if that failed, the caller 
     * could try the next one in the list.  Generally, this method should not return plans
     * for volumes whose status does not permit deletions.  
     *
     * @param size    the amount of space desired (in bytes)
     * @param vols    the set of cache volumes to create plans for.  Some volumes will be not 
     *                  be represented in the output plans if, for example, they are not known 
     *                  to the inventory database or are marked as not available for clean-up.
     */
    public List<DeletionPlan> orderDeletionPlans(long size, Collection<CacheVolume> vols)
        throws CacheManagementException, InventoryException
    {
        return this._orderDeletionPlans(size, vols, true);
    }
    
    protected List<DeletionPlan> _orderDeletionPlans(long size, Collection<CacheVolume> vols, boolean unavailwarn)
        throws CacheManagementException, InventoryException
    {
        List<DeletionPlan> out = new ArrayList<DeletionPlan>(vols.size());
        List<Exception> errs = new ArrayList<Exception>(2);

        // create a plan for each CacheVolume
        DeletionPlan dp = null;
        for (CacheVolume cvn: vols) {
            try {
                if (invdb.getVolumeStatus(cvn.getName()) < invdb.VOL_FOR_UPDATE) {
                    if (unavailwarn)
                        log.warn("Cache volume {} is not available for updates; skipping.", cvn.getName());
                    continue;
                }
            } catch (VolumeNotFoundException ex) {
                log.warn("Cache volume {} is not tracked by inventory; skipping.", cvn.getName());
                continue;
            }

            try {
                dp = createDeletionPlanFor(cvn, size);
                if (dp != null) out.add(dp);
            }
            catch (InventoryException ex) {
                throw ex;
            }
            catch (Exception ex) {
                log.warn("Trouble creating plan for CacheVolume, "+cvn.getName()+": "+ex.getMessage());
                errs.add(ex);
            }
        }
        if (out.size() == 0) {
            String msg = "Failed to generate any deletion plans for size="+Long.toString(size);
            if (errs.size() > 0) 
                msg += ", possibly due to unexpected errors (see log)";
            log.error(msg);
            throw new CacheManagementException(msg);
        }

        // order that list by the plan score, lowest to highest.  Note that with plan scores, 
        // lower is better (which is opposite for file scores)
        Collections.sort(out, (p1, p2) -> {
            return (int) Math.signum(p1.score - p2.score);
        });

        return out;
    }
}
