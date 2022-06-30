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

import gov.nist.oar.distrib.cachemgr.inventory.OldSelectionStrategy;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;

import org.slf4j.Logger;

import org.json.JSONObject;
import org.json.JSONException;

/**
 * a Cache implementation that supports flexible configuration of its volumes.
 * <p>
 * A cache is built up after instantiation through any number of calls to 
 * {@link #addCacheVolume(CacheVolume,int,JSONObject,VolumeConfig,boolean) addCacheVolume()}, 
 * allowing one to:
 * <ul>
 *   <li> assign roles to volumes that will cause them to be selected via the <code>preferences</code>
 *        parameter in the {@link #reserveSpace(long, int) reserveSpace()} function. </li>
 *   <li> associate a particular {@link SelectionStrategy} to use when clearing space in a volume </li>
 *   <li> the status of the volume (e.g. whether it is disabled) </li>
 * </ul>
 */
public class ConfigurableCache extends BasicCache {

    protected DeletionPlanner defdp = new myDeletionPlanner();

    /**
     * the assignment of deletion strategies to volumes
     */
    protected HashMap<String, DeletionStrategy> strategies = null;

    /**
     * the default deletion strategy to use if one is not configured for a particular volume.
     * This defaults to using {@link gov.nist.oar.distrib.cachemgr.inventory.OldSelectionStrategy}.  
     */
    protected DeletionStrategy defStrategy = new OldSelectionStrategy(1000);

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

    /**
     * create a ConfigurableCache containing no volumes
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb    the (empty) inventory database to use
     */
    public ConfigurableCache(String name, StorageInventoryDB idb) {
        this(name, idb, null);
    }

    /**
     * create a ConfigurableCache containing no volumes
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb    the (empty) inventory database to use
     * @param log    a particular Logger instance that should be used.  If null, a default one
     *                 will be created.  
     */
    public ConfigurableCache(String name, StorageInventoryDB idb, Logger log) {
        this(name, idb, 2, log);
    }

    /**
     * create a ConfigurableCache containing no volumes
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb    the (empty) inventory database to use
     * @param volcount  the expected number of CacheVolumes that will be attached via addVolume()
     * @param log       a particular Logger instance that should be used.  If null, a default one
     *                    will be created.  
     */
    public ConfigurableCache(String name, StorageInventoryDB idb, int volcount, Logger log) {
        super(name, idb, volcount, log);
        strategies = new HashMap<String, DeletionStrategy>(volcount);
    }

    /**
     * make the cache volume part of this cache.  The cache is expected either to be empty or 
     * to already be known to the Cache's inventory database.
     * @param vol       the CacheVolume to add to this Cache
     * @param capacity  the limit on the amount of space available for data objects
     * @param metadata  additional metadata about the volume.  Can be null.
     * @param config    the configuration to associate with the volume that further controls its use.
     * @param updmd     if false, the metadata (including the capacity and configuration) is not 
     *                    updated if the volume's name is already registered in the inventory.
     *                    If true, the metadata is update regardless, except that the status will 
     *                    not be upgraded.  
     * @throws InventoryException  if a problem occurs while registering the volume with 
     *                  the inventory database.
     */
    public void addCacheVolume(CacheVolume vol, long capacity, JSONObject metadata, VolumeConfig config,
                               boolean updmd)
        throws CacheManagementException
    {
        if (metadata == null)
            metadata = new JSONObject();
        else
            metadata = new JSONObject(metadata, JSONObject.getNames(metadata));
        if (config.getRoles() > 0) 
            metadata.put("roles", config.getRoles());
        Integer status = config.getStatus();
        if (status != null)
            metadata.put("status", status);

        // set deletion strategy
        DeletionStrategy ss = config.getDeletionStrategy();
        if (ss != null)
            strategies.put(vol.getName(), ss);

        super.addCacheVolume(vol, capacity, metadata, updmd);
    }

    /**
     * return a deletion planner for a particular use.  As this implementation uses only a single
     * deletion planner at a time, the preferences argument is ignored.
     * @param preferences  an and-ed set of bits indicating what the space will be used for.  In 
     *                     this implementation, this argument is ignored; the currently set planner
     *                     is always returned.  
     */
    @Override
    protected DeletionPlanner getDeletionPlanner(int preferences) {
        return defdp;
    }

    /**
     * return a reservation for a given amount of space.  
     * <p>
     * This method will cycle through the available caches looking for space for adding a new
     * data object.  When it finds (or creates some) it returns a Reservation for the space.  
     * @param bytes        the amount of space to reserve
     * @param preferences  an and-ed set of bits indicating what the space will be used for.
     * @return Reservation   an object that provides a claim on space; its interface should be 
     *                     used to deliver the bytes into the cache when the object is available.  
     * @throws NoMatchingVolumesException -- if there are no volumes available matching the requested 
     *                     preferences
     * @throws DeletionFailureException -- if no deletion plans can be generated due to some other problem.
     */
    @Override
    public Reservation reserveSpace(long bytes, int preferences) throws CacheManagementException {
        Collection<CacheVolume> vols = selectVolumes(preferences);
        if (vols.size() == 0)
            throw new NoMatchingVolumesException(preferences);

        CacheVolume cv = null;
        for (Iterator<CacheVolume> vi = vols.iterator(); vi.hasNext();) {
            cv = vi.next();
            if (! volumes.containsKey(cv.getName()))
                vi.remove();
        }

        DeletionPlanner planner = getDeletionPlanner(preferences);
        List<DeletionPlan> plans = planner.orderDeletionPlans(bytes, vols);
        return reserveSpace(plans);
    }

    class myDeletionPlanner implements DeletionPlanner {

        /**
         * return a deletion plan for a particular volume.  If null is returned, a viable plan--i.e.
         * one that can provide the requested space--was not possible.
         */
        public DeletionPlan createDeletionPlanFor(CacheVolume cv, long size) 
            throws InventoryException
        {
            String volname = cv.getName();
            DeletionPlan out = null;
            
            // first determine if the volume already has the space available within it
            long avail = db.getAvailableSpaceIn(volname);
            if (avail > (1 + delheadroom) * size) {
                out = new DeletionPlan(cv, db, new ArrayList<CacheObject>(), 0L, size);
                out.score = 0.0;
                return out;
            }
            long removeBytes = Math.round((1 + delheadroom) * size) - avail;

            // There's not enough free space, we need to make some.
            DeletionStrategy strat =
                getStrategyFor(volname, removeBytes, Math.round((1 + selheadroom) * (size - avail)));
            List<CacheObject> selected = db.selectObjectsFrom(volname, strat);

            if (strat.getSufficientSize() < removeBytes)
                // Can't create a viable plan with this volume
                // (size is probably too big or there's not enough removable stuff in it)
                return null;

            // Prep the plan for output
            strat.sort(selected);
            out = new DeletionPlan(cv, db, selected, strat.getSufficientSize(), size);

            // calculate a score
            out.score = calculatePlanScore(selected, size, avail);

            return out;
        }

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
            List<DeletionPlan> out = new ArrayList<DeletionPlan>(vols.size());
            List<Exception> errs = new ArrayList<Exception>(2);

            // create a plan for each CacheVolume
            DeletionPlan dp = null;
            for (CacheVolume cvn: vols) {
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
                throw new DeletionFailureException(msg);
            }

            // order that list by the plan score, lowest to highest.  Note that with plan scores, 
            // lower is better (which is opposite for file scores)
            Collections.sort(out, (p1, p2) -> {
                return (int) Math.signum(p1.score - p2.score);
            });

            return out;
        }
    }

    /**
     * calculate an overall score for the selected cache objects as a plan for creating 
     * the given space.  Better plans have lower values, where 0 is perfect.  
     *
     * This method can be over-ridden to better balance scores for plans created with different 
     * strategies.
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
    protected DeletionStrategy getStrategyFor(String volname, long need, long lim) {
        DeletionStrategy out = strategies.get(volname);
        if (out == null)
            out = defStrategy;
        out = out.newForSize(need, lim);
        return out;
    }

    /**
     * select volumes from the cache to create deletion plans based on the client's preferences.
     * <p>
     * In this implementation, the preferences are interpreted as a set of preferred (bit-wise AND-ed) 
     * roles.  Only those volumes where one of their assigned role code is included in the given 
     * preferred roles will be returned.
     * <p>
     * This implementation also ensures that the returned volumes are currently configured for updates.  
     *
     * @param preferences   roles that returned volumes must match.  If 0, all available volumes are 
     *                      returned.  
     */
    protected Collection<CacheVolume> selectVolumes(int preferences) throws InventoryException {
        ArrayList<CacheVolume> out = new ArrayList<CacheVolume>(recent.size());
        Collection<String> volnames = db.volumes();
        log.info("preferences="+preferences);
        // Note: recent sets the order that the volumes are analyzed. 
        for (String volnm : recent) {
            try {
                if (db.getVolumeStatus(volnm) < VolumeStatus.VOL_FOR_UPDATE) {
                    log.debug("Cache volume {} is not available for updates; skipping", volnm);
                    continue;
                }
            }
            catch (VolumeNotFoundException ex) { continue; }


            if (preferences > 0) {
                JSONObject vmd = db.getVolumeInfo(volnm);
                int roles = 0;
                try {
                    roles = vmd.getInt("roles");
                } catch (JSONException ex) {  }
                if ((roles & preferences) == 0)
                    continue;
            }

            if (volumes.containsKey(volnm))
                out.add(volumes.get(volnm));
                log.info("volnm="+volnm);
        }

        return out;
    }
}
