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
package gov.nist.oar.distrib.cachemgr.pdr;

import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.cachemgr.Restorer;
import gov.nist.oar.distrib.cachemgr.BasicCache;
import gov.nist.oar.distrib.cachemgr.BasicCacheManager;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.RestorationException;
import gov.nist.oar.distrib.cachemgr.VolumeNotFoundException;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.InventorySearchException;
import gov.nist.oar.distrib.cachemgr.IntegrityMonitor;
import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.VolumeStatus;
import gov.nist.oar.distrib.cachemgr.inventory.JDBCStorageInventoryDB;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Collection;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.json.JSONObject;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A CacheManager that is specialized for use with the NIST Public Data Repository (PDR) and its 
 * distribution service.  It provides additional service methods help control caching and integrity 
 * checking as well as provide a view into the cache's contents.  These controls can be connected 
 * to a web service interface.  
 * 
 * <h3> Integrity checking </h3>
 * 
 * This uses an {@link gov.nist.oar.distrib.cachemgr.IntegrityMonitor} instance (provided via the 
 * {@link gov.nist.oar.distrib.cachemgr.BasicCache} set at construction time) to periodically check the 
 * integrity of the files; an object that fails at least one configured check (for the PDR, this is just a 
 * checksum check) is deleted from the cache.  Normally, this happens continuously via a dedicated thread
 * availble via {@link #getMonitorThread()} and started soon after this manager is instantiated.  It will 
 * search for files that have exceeded a configured "grace period" interval of time since the last checks
 * and run the checks on them.  
 * <p>
 * One can also request specific data objects or all objects from a specific dataset to be checked on-demand
 * via {@link #queueCheck(String) check()}.  Such requests will be queued up and checked asynchronously in yet 
 * another thread.  To run targeted checks synchronously, one can call {@link #checkDataset(String)} or 
 * {@link #checkObject(String)}.  
 * 
 * <h3> Restoring data to the cache </h3>
 * 
 * Individual objects can be restored synchronously to cache via the methods provided by the 
 * {@link gov.nist.oar.distrib.cachemgr.CacheManager} interface; in particular, the 
 * {@link gov.nist.oar.distrib.cachemgr.CacheManager#cache(String)} method is called automatically within
 * {@link gov.nist.oar.distrib.cachemgr.CacheManager#getObject(String)} if the object is not already in 
 * the cache.  However, many objects can be restored to the asynchronously via {@link #queueCache(String)}.
 * 
 * <h3> Viewing the contents of the cache </h3>
 * 
 * <p>
 */
public class PDRCacheManager extends BasicCacheManager implements PDRConstants, VolumeStatus {

    IntegrityMonitor datamon = null;
    IntegrityMonitor hbmon = null;
    Logger log = null;
    MonitorThread month = null;
    CachingThread cath = null;

    /**
     * create a CacheManager specialized for the PDR
     * @param cache          the cache to manage
     * @param restorer       the restorer to use to restore objects and datasets to the cache
     * @param checklist      the set of integrity tests to include in continuous and on-demand integrity
     *                           checking.
     * @param dutycyclemsec  how often, in milliseconds, the continuous integrity checking should look for 
     *                           cache objects to cache; if less than 0, the default of 30 minutes will be 
     *                           used.  Set this to an integer fraction of any hour to have a check 
     *                           commence at the same time every hour.  
     * @param graceperiod    the minimum time, in milliseconds, since an object's last integrity check 
     *                           before it needs to be rechecked.  
     * @param startoffset    an offset time, in milliseconds since the last midnight, to apply to scheduling
     *                           of the continuous integrity checks.  When zero, each cycle of checking will 
     *                           start on a multiple of duty cycle intervals since midnight; when greater 
     *                           than zero, checks will start on multiples of the duty cycle since midnight
     *                           <i>plus the offset</i>.  This is usually set to a value less than 
     *                           <code>dutycyclemsec</code>.
     * @param cacheq         the file to use as the persistence of the queue for on-demand caching.  This 
     *                           file need not exist yet, but its parent directory must.
     * @param logger         the Logger instance to use for log messages
     */
    public PDRCacheManager(BasicCache cache, PDRDatasetRestorer restorer, List<CacheObjectCheck> checklist,
                           long dutycyclemsec, long graceperiod, long startoffset, File cacheq, Logger logger)
        throws IOException
    {
        super(cache, restorer);
        if (logger == null)
            logger = LoggerFactory.getLogger(getClass());
        log = logger;

        datamon = cache.getIntegrityMonitor(checklist);
        hbmon = restorer.getIntegrityMonitor(checklist);

        month = new MonitorThread(dutycyclemsec, graceperiod, startoffset, false);
        month.setPriority(Math.min(Thread.currentThread().getPriority() - 4, Thread.MIN_PRIORITY));

        cath = new CachingThread(cacheq);
        cath.setPriority(Math.min(Thread.currentThread().getPriority() - 2, Thread.MIN_PRIORITY));
    }

    /**
     * return a set of caching preferences for an object with the given identifier and size
     * to be applied by {@link #cache(String)} when preferences are not specified.  Other internal 
     * processes may alter those preferences as more is learned about the object during restoration. 
     * The default set returned here is expected to reflect the specific cache manager implementation
     * and/or the configured internal cache.  
     * <p>
     * This implementation returns a preference set drawn from the {@link PDRCacheRoles} definitions
     * according to PDR conventions.  
     * @param id     the identifier for the object being cached
     * @param size   the size of the object in bytes; if negative, the size is not known
     * @return int -- an ANDed set of caching preferences, or zero if no preferences are applicable
     */
    public int getDefaultPreferencesFor(String id, long size) {
        return ((PDRDatasetRestorer) restorer).getPreferencesFor(id, size, -1);
    }

    /**
     * cache all of the files from the given dataset
     * @param dsid     the AIP identifier for the dataset; this is either the old-style EDI-ID or 
     *                   local portion of the PDR ARK identifier (e.g., <code>"mds2-2119"</code>).  
     * @param version  the desired version of the dataset or null for the latest version
     */
    public void cacheDataset(String dsid, String version)
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        ((PDRDatasetRestorer) restorer).cacheDataset(dsid, version, theCache);
    }

    /**
     * queue up a dataset or file object to be cached asynchronously via a separate thread
     * @param id   the full aipid for the dataset or object (of the form DSID[/FILEPATH]#[VERSION])
     */
    public void queueCache(String id)
        throws ResourceNotFoundException, StorageVolumeException, CacheManagementException
    {
        if (restorer.doesNotExist(id))
            throw new ResourceNotFoundException(id);
        cath.queue(id);
        if (! cath.isAlive())
            cath.start();
    }

    /**
     * send the results of the integrity monitor's work to interested consumers.  This includes 
     * record information into the log (via {@link #logMonitorResults(int,List)}. 
     * @param checked    the number of files checked
     * @param deleted    the list of cache objects that were deleted because they failed the 
     *                      integrity check.
     */
    public void dispatchMonitorResults(int checked, List<CacheObject> deleted) {
        logMonitorResults(checked, deleted);
        // FUTURE: email results
    }

    /**
     * send the results of the integrity monitor's work to interested consumers.  This includes 
     * record information into the log (via {@link #logMonitorResults(int,List)}. 
     * @param checked    the number of files checked
     * @param deleted    the list of cache objects that were deleted because they failed the 
     *                      integrity check.
     */
    public void logMonitorResults(int checked, List<CacheObject> deleted) {
        if (checked > 0) {
            log.info("Monitor checked {} file{}", checked, (checked == 1) ? "" : "s");
            if (deleted.size() > 0 && log.isWarnEnabled()) {
                StringBuilder sb = new StringBuilder("Monitor deleted ");
                sb.append(Integer.toString(deleted.size())).append(" file")
                  .append((deleted.size() == 1) ? "" : "s")
                  .append(" with detected integrity failures");
                if (deleted.size() > 5)
                    sb.append(", including");
                sb.append(":");

                int i=0;
                for (CacheObject co : deleted) {
                    if (++i > 5) break;  // limit listing to 5 files
                    sb.append("\n   ").append(co.id);
                }

                log.warn(sb.toString());
            }
            else
                log.info("No files detected with integrity failures");
        }
        else
            log.info("Monitor completes cycle with no files to check");
    }

    /**
     * return the thread used to conduct continuous integrity checking.  This thread may or may not be 
     * running.  This method is not guarateed to return the same thread instance on every call:  after 
     * a running thread exits, it gets replaced with a new thread that can be started.  
     */
    public MonitorThread getMonitorThread() {
        return month;
    }

    /**
     * the thread class used to conduct continuous integrity checking.  Via its interface, it can be 
     * started or stopped.
     */
    public class MonitorThread extends Thread {
        long dutycycle = 30 * 60000;   // 30 minutes
        long start = 0L;
        long grace = 24 * 3600000;  // 24 hours
        boolean stopsoon = false;
        boolean once = false;

        MonitorThread(long dutycyclemsec, long graceperiod, long startoffset, boolean runonce) {
            super("CacheMonitor");
            setCycling(dutycyclemsec, graceperiod, startoffset);
            once = runonce;
        }

        /**
         * reset the scheduling parameters for this thread.  These parameters affect the cycle on 
         * which the thread looks for objects requiring a new check (even when the thread is currently 
         * running when the method is called).
         */
        public void setCycling(long dutycyclemsec, long graceperiodmsec, long startoffset) {
            if (dutycyclemsec >= 0)
                dutycycle = dutycyclemsec;
            if (graceperiodmsec >= 0) {
                grace = graceperiodmsec;
                try {
                    BasicCache cache = (BasicCache) theCache;
                    ((JDBCStorageInventoryDB) cache.getInventoryDB()).setCheckGracePeriod(graceperiodmsec);
                } catch (ClassCastException ex) { /* ignore grace period parameter: not supported */ }
            }
            initstart(startoffset);
        }

        /**
         * return true if this thread is current set to execute continuous cycles of checking
         * cache objects.  If false, the thread is set to execute one cycle and then exit.  
         * When true, object checking happens according to its configured duty cycle (see 
         * {@link #setCycling(long, long, long)}).
         */
        public boolean isContinuous() {
            return ! once;
        }

        /**
         * Set whether this thread should run continuously, checking for files to check on a 
         * cyclical schedule.  If set to false, the thread will run one cycle and then exit; 
         * otherwise, it will run forever until explicitly stopped.  Setting this to false while 
         * the thread is running will cause the thread to stop after its current cycle is finished.
         * @param yes    if true, the thread will be set to run continuously; otherwise, it will 
         *               run once and exit.
         */
        public void setContinuous(boolean yes) {
            once = ! yes;
        }

        /**
         * stop this thread as soon as possible. The state of integrity checking will be clean.
         */
        public void interrupt() {
            log.debug("Monitor stop requested; will do so at next interval.");
            super.interrupt();
        }

        /**
         * stop this thread as soon as possible and block until it has exited. The state of integrity 
         * checking will be clean.
         */
        public void interruptAndWait() {
            interrupt();
            try { join(); }
            catch (InterruptedException ex) { }
        }

        void initstart(long startoffset) {
            if (startoffset < 0) startoffset = 0L;
            start = LocalDate.now().atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000 + startoffset;
            resetStart();
        }
        void resetStart() {
            if (dutycycle > 0) {
                long now = Instant.now().toEpochMilli();
                if (start < now) 
                    start = now - ((now - start) % dutycycle) + dutycycle;
            }
        }

        @Override
        public void run() {
            int checked = 0;
            List<CacheObject> deleted = new ArrayList<CacheObject>();
            try {
                if (once) {
                    checked = monitorUntilDone(deleted, 100, 100);
                    dispatchMonitorResults(checked, deleted);
                }
                else {
                    long untilnext = start - Instant.now().toEpochMilli();
                    while (! interrupted()) {
                        if (untilnext <= 0) {
                            checked = monitorUntilDone(deleted, 100, 100);
                            resetStart();
                            dispatchMonitorResults(checked, deleted);
                            log.debug("Next check at epochMilli={}", start);
                            deleted = new ArrayList<CacheObject>();
                            checked = 0;
                        }
                        else {
                            sleep(untilnext);
                        }
                        untilnext = start - Instant.now().toEpochMilli();

                        // if the status of once changed, stop cycling
                        if (once) break;
                    }
                }
            }
            catch (StorageVolumeException ex) {
                log.error("Storage volume problem halted processing: {}", ex.getMessage());
            }
            catch (CacheManagementException ex) {
                log.error("Caching failure halted processing: {}", ex.getMessage());
            }
            catch (InterruptedException ex) {
                log.info("Monitor thread stop requested; exiting");
            }
            finally {
                month = cloneMe();
            }
        }

        /**
         * run a limit number of integrity checks synchronously in the current thread.  This 
         * method will find the <code>maxobjs</code> number of objects in the cache that are 
         * most in need of checking (i.e. having gone the longest since their last check) and 
         * checks them.  Checks are only done on objects that have exceeded the configured 
         * "grace period" since their last checks; if no such objects are in the cache, no checks
         * are done.  
         * @param mon      the {@link gov.nist.oar.distrib.cachemgr.IntegrityMonitor} to use to 
         *                 run the checks.
         * @param deleted  an update-able list to which this method can add the objects that 
         *                 failed an integrity check and was deleted from the cache.
         * @param maxobjs  the maximum number of objects to select for checking.
         */ 
        public int monitorOnce(IntegrityMonitor mon, List<CacheObject> deleted, int maxobjs) 
            throws StorageVolumeException, CacheManagementException, InterruptedException
        {
            if (interrupted()) throw new InterruptedException();
            return mon.findCorruptedObjects(maxobjs, deleted, true);
        }

        /**
         * run a limit number of integrity checks synchronously in the current thread.  This 
         * method will find the <code>maxobjs</code> number of objects in the cache that are 
         * most in need of checking (i.e. having gone the longest since their last check) and 
         * checks them.  Checks are only done on objects that have exceeded the configured 
         * "grace period" since their last checks; if no such objects are in the cache, no checks
         * are done.  
         * <p>
         * This method calls {@link #monitorOnce(IntegrityMonitor,List,int)}.
         * @param deleted  an update-able list to which this method can add the objects that 
         *                 failed an integrity check and was deleted from the cache.
         * @param hbmax    the maximum number of objects to select from the head bag cache for checking.
         * @param dmax     the maximum number of objects to select from the data cache for checking.
         */ 
        public int monitorOnce(List<CacheObject> deleted, int hbmax, int dmax) 
            throws StorageVolumeException, CacheManagementException, InterruptedException
        {
            int count = 0;
            count = monitorOnce(hbmon, deleted, hbmax);
            count +=monitorOnce(datamon, deleted, dmax);
            return count;
        }

        /**
         * run synchronously in the current thread integrity check all of the files in the cache 
         * have exceeded their "grace period" since their last checks.  
         * @param deleted  an update-able list to which this method can add the objects that 
         *                 failed an integrity check and was deleted from the cache.
         * @param hbmax    the maximum number of objects to select <i>at a time</i> from the head bag 
         *                 cache for checking.  This method will continue to check objects in chunks of 
         *                 this size until all objects have been checked.
         * @param dmax     the maximum number of objects to select <i>at a time</i> from the data cache 
         *                 for checking.  This method will continue to check objects in chunks of 
         *                 this size until all objects have been checked.
         */
        public int monitorUntilDone(List<CacheObject> deleted, int hbmax, int dmax) 
            throws StorageVolumeException, CacheManagementException, InterruptedException
        {
            int out = 0;
            int count = -1;
            while (count != 0) {
                count = monitorOnce(deleted, hbmax, dmax);
                out += count;
            }
            return out;
        }

        protected MonitorThread cloneMe() {
            MonitorThread out = new MonitorThread(dutycycle, grace, 0L, once);
            out.start = start;
            out.setPriority(getPriority());
            return out;
        }
    }

    /**
     * create a name for a data object within a particular {@link gov.nist.oar.distrib.cachemgr.CacheVolume}.  
     */
    protected String determineCacheObjectName(String volname, String id) throws CacheManagementException {
        int roles = getRolesFor(volname);
        return ((PDRDatasetRestorer) restorer).nameForObject(id, roles);
    }

    int getRolesFor(String volname) {
        StorageInventoryDB sidb = ((BasicCache) theCache).getInventoryDB();
        int roles = 0;
        try {
            JSONObject md = sidb.getVolumeInfo(volname);
            roles = md.optInt("roles");
        }
        catch (VolumeNotFoundException ex) {
            log.error("Trouble getting roles: volume, {}, not registered ({})", volname, ex.getMessage());
            // keep default values
        }
        catch (InventoryException ex) {
            log.error("Trouble getting roles assigned to volume, {}: {}", volname, ex.getMessage());
            // keep default values
        }
        return roles;
    }

    protected PDRStorageInventoryDB getInventoryDB() {
        return (PDRStorageInventoryDB) ((BasicCache) theCache).getInventoryDB();
    }

    /**
     * provide a summary the state of the volumes that are in the cache.  
     * <p>
     * Each element in the returned array is a <code>JSONObject</code> describing a volume in the cache.  
     * The object properties provided are those returned by {@link #summarizeVolume(String)}.
     */
    public JSONArray summarizeVolumes() throws InventoryException {
        PDRStorageInventoryDB sidb = getInventoryDB();
        JSONArray out = new JSONArray();
        for(String vname : sidb.volumes())
            out.put(summarizeVolume(vname));
        return out;
    }

    /**
     * provide a summary of the state of a particular volume in the cache.  
     * <p>
     * The returned <code>JSONObject</code> describes the volume via properties that  
     * include its static metadata (link "name" and "capacity") as well as the 
     * following status information:
     * <ul>
     *   <li> "filecount" -- the number of files currently in the volume </li>
     *   <li> "totalsize" -- the total number bytes stored in the volume </li>
     *   <li> "since" -- the last date-time an object in the cache was accessed or added, in epoch msecs </li>
     *   <li> "sinceDate"  -- the last date-time an object in the cache was accessed or added, as an 
     *            ISO string </li>
     *   <li> "checked" -- the oldest date-time that an object was last check for integrity (checksum),
     *            in epoch msecs </li>
     *   <li> "checkedDate" -- the oldest date-time that an object was last check for integrity (checksum),
     *            as an ISO string </li>
     * </ul>
     * @param name    the name of the volume to return the summary for
     */
    public JSONObject summarizeVolume(String name) throws VolumeNotFoundException, InventoryException {
        PDRStorageInventoryDB db = getInventoryDB();
        JSONObject out = db.getVolumeInfo(name);
        JSONObject totals = db.getVolumeTotals(name);
        out.put("name", name);

        for (String prop : JSONObject.getNames(totals))
            out.put(prop, totals.get(prop));

        return out;
    }

    /**
     * run integrity checks on objects in the cache from the collection with the given AIP ID. 
     * @param aipid     the AIP ID for the data objects that should be checked
     * @param recache   if true, restore any objects that were removed due to failed checks
     * @return List<CacheObject> -- a list of the objects that failed their check and were deleted.
     */
    public List<CacheObject> check(String aipid, boolean recache)
        throws InventoryException, StorageVolumeException, CacheManagementException
    {
        List<CacheObject> cached = selectDatasetObjects(aipid, VolumeStatus.VOL_FOR_UPDATE);

        List<CacheObject> deleted = new ArrayList<CacheObject>();
        datamon.selectCorruptedObjects(cached, deleted, true);
        if (recache) {
            for (CacheObject co : deleted)
                cache(co.id);
        }
        return deleted;
    }

    /**
     * return a list of objects known to the cache that are part of the dataset having the given AIP dataset 
     * id.  The cache knows about an object if the object is currently in the cache or has once been in the 
     * cache.  
     * @param dsid     the AIP id for the dataset; this is either the old-style EDI-ID or 
     *                   local portion of the PDR ARK identifier (e.g., <code>"mds2-2119"</code>).  
     * @param status   A {@link gov.nist.oar.distrib.cachemgr.VolumeStatus} value indicating the status 
     *                   of the desired objects.  In particular, specify...
     *                 <ul>
     *                    <li> {@link gov.nist.oar.distrib.cachemgr.VolumeStatus#VOL_FOR_INFO VOL_FOR_INFO} 
     *                         for objects that have ever been in the cache (but may not now be), </li>
     *                    <li> {@link gov.nist.oar.distrib.cachemgr.VolumeStatus#VOL_FOR_INFO VOL_FOR_GET} 
     *                         for objects that are currently in the cache, </li>
     *                    <li> {@link gov.nist.oar.distrib.cachemgr.VolumeStatus#VOL_FOR_INFO VOL_FOR_UPDATE} 
     *                         for objects that can be removed, recached, or have their status updated 
     *                         (like the last time it was checked).</li>
     *                 </ul>
     */
    public List<CacheObject> selectDatasetObjects(String dsid, int status) throws CacheManagementException {
        PDRStorageInventoryDB sidb = getInventoryDB();
        List<CacheObject> matched = sidb.selectObjectsLikeID(dsid+"/%", status);
        CacheObject co = null;
        for (Iterator<CacheObject> it = matched.iterator(); it.hasNext();) {
            co = it.next();
            if ("old".equals(co.volname))
                it.remove();
            else
                co.volume = theCache.getVolume(co.volname);
        }
        return matched;
    }


    /**
     * return a CacheObject description of an object in the inventory database or null if the object is 
     * not in the dataset (with the specified status).  
     * @param aipid   the full AIP ID for the dataset or object (of the form DSID[/FILEPATH]#[VERSION])
     * @param status  the required minimal status of the object.  This should be a 
     *                {@link gov.nist.oar.distrib.cachemgr.VolumeStatus} value indicating the desired status 
     *                   of the object.  In particular, null is returned when <code>status</code> is...
     *                 <ul>
     *                    <li> {@link gov.nist.oar.distrib.cachemgr.VolumeStatus#VOL_FOR_INFO VOL_FOR_INFO} 
     *                         and the object has never been in the cache, </li>
     *                    <li> {@link gov.nist.oar.distrib.cachemgr.VolumeStatus#VOL_FOR_INFO VOL_FOR_GET} 
     *                         and the object is not currently in the cache, </li>
     *                    <li> {@link gov.nist.oar.distrib.cachemgr.VolumeStatus#VOL_FOR_INFO VOL_FOR_UPDATE} 
     *                         and the object is locked from being removed, recached, or having its status 
     *                         updated (like the last time it was checked).</li>
     *                 </ul>     
     */
    public CacheObject describeObject(String aipid, String filepath, int status) throws InventoryException {
        PDRStorageInventoryDB sidb = getInventoryDB();
        List<CacheObject> cos = sidb.findObject(aipid+"/"+filepath, status);
        if (cos.size() == 0)
            return null;
        if (cos.size() == 1)
            return cos.get(0);

        // if it is listed multiple times, pick the one latest entry
        long latest = 0;
        CacheObject out = cos.get(0);
        long since = 0L;
        for (CacheObject co : cos) {
            since = co.getMetadatumLong("since", 0L);
            if (since > latest) {
                latest = since;
                out = co;
            }
        }
        return out;
    }

    /**
     * return a summary of the set of files from a particular dataaset currently in the cache.
     * <p>
     * the returned <code>JSONObject</code> will in include the following stats:
     * <ul>
     *   <li> "filecount" -- the number of files from the dataset currently in the volume </li>
     *   <li> "totalsize" -- the total number bytes of all files from the dataset stored in the volume </li>
     *   <li> "since" -- the last date-time a file from the dataset in the cache was accessed or added, 
     *            in epoch msecs </li>
     *   <li> "sinceDate"  -- the last date-time a file from the dataset in the cache was accessed or added, 
     *            as an ISO string </li>
     *   <li> "checked" -- the oldest date-time that a file from the dataset was last check for integrity 
     *            (checksum), in epoch msecs </li>
     *   <li> "checkedDate" -- the oldest date-time that a file from the dataset was last check for 
     *            integrity (checksum), as an ISO string </li>
     * </ul>
     */
    public JSONObject summarizeDataset(String aipid) throws InventoryException {
        return getInventoryDB().summarizeDataset(aipid);        
    }

    /**
     * provide a summary of the contents of the cache by aipid.  Each object in the returned array
     * summarizes a different AIP with the same properties as returned by {@link #summarizeDataset(String)}.
     * @param volname   the name of the volume to restrict results to; if null, results span across volumes
     */
    public JSONArray summarizeContents(String volname) throws InventoryException {
        return getInventoryDB().summarizeContents(volname);
    }

    /**
     * a thread that will cache data in order of their IDs in an internal queue.  Objects can be added 
     * to the queue via {@link #queue(String)}.
     * <p>
     * The internal is persisted to a local file as IDs are added or removed from it.  
     */
    public class CachingThread extends Thread {
        File _queuef = null;
        Queue<String> _queue = new ConcurrentLinkedQueue<String>();

        CachingThread(File savedqueue) throws IOException {
            super("Cacher");
            _queuef = savedqueue;
            if (_queuef.exists())
                restoreQueue();
            else
                saveQueue();
        }

        synchronized void saveQueue() throws IOException {
            BufferedWriter out = new BufferedWriter(new FileWriter(_queuef));
            try {
                for (String id : _queue) {
                    out.write(id);
                    out.newLine();
                }
            }
            finally {
                try { out.close(); }
                catch (IOException ex) {
                    log.warn("Trouble closing cache request queue file after save: "+ex.getMessage());
                }
            }
        }

        synchronized void restoreQueue() throws IOException {
            BufferedReader in = new BufferedReader(new FileReader(_queuef));
            String line = null;
            try {
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.length() > 0)
                        _queue.add(line);
                }
            }
            finally {
                try { in.close(); }
                catch (IOException ex) {
                    log.warn("Trouble closing cache request queue file after restore: "+ex.getMessage());
                }
            }
        }

        public void queue(String aipid) {
            _queue.add(aipid);
            try {
                saveQueue();
            } catch (IOException ex) {
                log.error("Failed to persist cache queue: "+ ex.getMessage());
            }
        }

        public boolean hasPending() {
            return _queue.size() > 0;
        }

        public boolean isQueued(String aipid) {
            return _queue.contains(aipid);
        }

        public String cacheNext() throws CacheManagementException {
            String nextid = _queue.peek();
            if (nextid == null)
                return null;

            String[] parts = ((PDRDatasetRestorer) restorer).parseId(nextid);
            try {
                if (parts[1].length() == 0) 
                    // dataset identifier
                    cacheDataset(parts[0], parts[2]);
                else
                    // data file identifier
                    cache(nextid);
            }
            catch (ResourceNotFoundException ex) {
                throw new CacheManagementException("Unable to cache "+nextid+": resource not found", ex);
            }
            catch (StorageVolumeException ex) {
                throw new CacheManagementException("Storage trouble while caching "+nextid+": "+
                                                   ex.getMessage(), ex);
            }
            finally {
                _queue.remove();
                try { saveQueue(); }
                catch (IOException ex) {
                    log.error("Failed to update persisted queue: "+ex.getMessage());
                }
            }

            return nextid;
        }

        public void run() {
            try {
                while (_queue.peek() != null) {
                    if (interrupted()) throw new InterruptedException();
                    try {
                        cacheNext();
                    }
                    catch (CacheManagementException ex) {
                        log.error(ex.getMessage());
                    }
                }
            }
            catch (InterruptedException ex) {
                log.info("Interruption of caching thread requested; exiting.");
            }
            finally {
                try {
                    cath = cloneMe();
                } catch (IOException ex) {
                    log.error("Failed to refresh caching thread: queue persistence error: "+ex.getMessage());
                }
            }
        }

        protected CachingThread cloneMe() throws IOException {
            CachingThread out = new CachingThread(_queuef);
            out.setPriority(getPriority());
            return out;
        }
    }
}

