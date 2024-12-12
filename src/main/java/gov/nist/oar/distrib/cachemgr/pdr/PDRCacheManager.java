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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;

import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.BasicCache;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.IntegrityMonitor;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.VolumeStatus;
import gov.nist.oar.distrib.cachemgr.inventory.JDBCStorageInventoryDB;

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
public class PDRCacheManager extends PDRDatasetCacheManager implements PDRConstants {

    IntegrityMonitor datamon = null;
    IntegrityMonitor hbmon = null;
    File monstatus = null;
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
     * @param admdir         the directory to use for storing administrative data.  This directory must 
     *                           already exist.  
     * @param logger         the Logger instance to use for log messages
     */
    public PDRCacheManager(BasicCache cache, PDRDatasetRestorer restorer, List<CacheObjectCheck> checklist,
                           long dutycyclemsec, long graceperiod, long startoffset, File admdir, Logger logger)
        throws IOException
    {
        super(cache, restorer);
        if (! admdir.isDirectory())
            throw new FileNotFoundException("Not an existing directory: "+admdir.toString());

        datamon = cache.getIntegrityMonitor(checklist);
        hbmon = restorer.getIntegrityMonitor(checklist);

        month = new MonitorThread(dutycyclemsec, graceperiod, startoffset, false);
        month.setPriority(Math.min(Thread.currentThread().getPriority() - 4, Thread.MIN_PRIORITY));
        monstatus = new File(admdir, "monitorstatus.json");

        cath = new CachingThread(new File(admdir, "cacheq"));
        cath.setPriority(Math.min(Thread.currentThread().getPriority() - 2, Thread.MIN_PRIORITY));
    }

    /**
     * conditionally cache an object or objects associated with the given identifier in an 
     * implementation-specific way.  The given identifier is not required to exist or otherwsie be 
     * recognized, and the implementation may oar may not actually cache any data as a result of a 
     * call to this method.
     * <p>
     * This method is intended to strategicly automate additional data into the cache based on user requests.
     * This particular implementation will accept an AIP identifier that refers either dataset or a file 
     * within a dataset (in the form of <i>dsid</i><tt>/</tt><i>filepath[</i><tt>#</tt><i>version]</i>).
     * In either case, the implementation looks to see if any data files from the identified dataset are 
     * currently in the cache.  If there are none, this method will queue the dataset to be added to the 
     * cache.  If the some files are found and the given ID refers to an individual file, then just that 
     * file is queued to be added.  Otherwise, no additional data is queue.  If any of the targeted data 
     * already exists in the cache, it is not recached. 
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
        String[] parts = ((PDRDatasetRestorer) restorer).parseId(id);
        String dsid = parts[0];
        String file = parts[1];
        String version = parts[2];

        try {
            // see if we should queue the full dataset associated with the given ID
            PDRStorageInventoryDB sidb = (PDRStorageInventoryDB) ((BasicCache) theCache).getInventoryDB();

            JSONObject summ = sidb.summarizeDataset(dsid);
            if (summ == null || summ.optInt("filecount", 0) == 0) {
                // There's nothing in the cache from this dataset; go ahead and cache the whole thing
                log.info("cache-queuing {} {}triggered by user demand", dsid,
                          (version == null) ? "" : "("+version+") ");
                if (version != null)
                    dsid += "#" + version;
                queueCache(dsid, false);
                return;
            }
        }
        catch (ClassCastException ex) { /* oh well */ }
        catch (ResourceNotFoundException ex) {
            log.debug("FYI: requested dataset, {}, not found; ignoring...", dsid);
            return;
        }
        catch (StorageVolumeException ex) {
            throw new CacheManagementException("cache-queuing failed due to storage issue: "+ex.getMessage(), ex);
        }

        if (file != null) {
            // the file is presumed to be not in the cache, so queue it.
            try {
                log.info("cache-queueing {}", id);
                queueCache(id, false);
            }
            catch (ResourceNotFoundException ex) {
                log.warn("Requested dataset for caching, {}, not found", id);
            }
            catch (StorageVolumeException ex) {
                throw new CacheManagementException("cache-queuing failed due to storage issue: "+
                                                   ex.getMessage(), ex);
            }
        }
    }

    /**
     * queue up a dataset or file object to be cached asynchronously via a separate thread
     * @param id   the full aipid for the dataset or object (of the form DSID[/FILEPATH][#VERSION])
     * @param recache  if true, request that files already in the cache be recached from scratch; 
     *                 if false, such files will not be updated.
     */
    public void queueCache(String id, boolean recache)
        throws ResourceNotFoundException, StorageVolumeException, CacheManagementException
    {
        queueCache(id, recache, null);
    }

    /**
     * queue up a dataset or file object to be cached asynchronously via a separate thread
     * @param id   the full aipid for the dataset or object (of the form DSID[/FILEPATH][#VERSION])
     * @param recache  if true, request that files already in the cache be recached from scratch; 
     *                 if false, such files will not be updated.
     * @param sequence a string for restricting the files from the dataset that get restored.  The 
     *                 value is matched against the bagfile sequence number; only those files contained
     *                 in that bag will be restored.  
     */
    public void queueCache(String id, boolean recache, String sequence)
        throws ResourceNotFoundException, StorageVolumeException, CacheManagementException
    {
        if (restorer.doesNotExist(id))
            throw new ResourceNotFoundException(id);
        cath.queue(id, recache, sequence);
        if (! cath.isAlive())
            cath.start();
    }

    /**
     * start a separate thread to cache all items currently found in the queue.
     * @return boolean -- True if caching thread was not running but was started; false if the 
     *                    thread was already running.
     */
    public boolean startCaching() {
        if (cath.isAlive()) return false;
        cath.start();
        return true;
    }

    /**
     * return True if data is currently being added to the cache via the Caching thread.
     */
    public boolean isCaching() { return cath.isAlive(); }

    /**
     * return a name for the item currently being cached or null if the cacher is between items 
     * (or the caching thread is not running).
     */
    public String getCachingItemName() {
        return (cath == null) ? null : cath.inprocess;
    }

    /**
     * return the status of the caching queue as JSONObject
     */
    public JSONObject getCachingQueueStatus() throws CacheManagementException {
        JSONObject out = new JSONObject();
        out.put("status", ((isCaching()) ? "" : "not ") + "running");
        String current = getCachingItemName();
        out.put("current", (current == null) ? JSONObject.NULL : current);

        try {
            Queue<String> inq = cath.loadQueue();
            JSONArray waiting = new JSONArray();
            for(String id : inq) 
                waiting.put(id);
            out.put("waiting", waiting);
            return out;
        }
        catch (IOException ex) {
            throw new CacheManagementException("Trouble reading the current queue: "+ex.getMessage());
        }
    }

    /**
     * send the results of the integrity monitor's work to interested consumers.  This includes 
     * record information into the log (via {@link #recordMonitorResults(int,List)}. 
     * @param checked    the number of files checked
     * @param deleted    the list of cache objects that were deleted because they failed the 
     *                      integrity check.
     * @throws CacheManagementException  if something goes wrong during the dispatch or recording
     */
    public void dispatchMonitorResults(int checked, List<CacheObject> deleted)
        throws CacheManagementException
    {
        recordMonitorResults(checked, deleted);
        // FUTURE: email results
    }

    /**
     * return data describing the integrity montoring status.  This data includes when the last time
     * the integrity monitor was ran, how many files were checked, and which ones had to be deleted.  
     */
    public JSONObject getMonitorStatus() throws CacheManagementException {
        JSONObject out = retrieveMonitorStatus();
        out.put("running", getMonitorThread().isAlive());
        return out;
    }
    private JSONObject retrieveMonitorStatus() {
        JSONObject out = null;
        if (monstatus.exists()) {
            synchronized (monstatus) {
                try (FileReader rdr = new FileReader(monstatus)) {
                    out = new JSONObject(new JSONTokener(rdr));
                }
                catch (IOException ex) {
                    log.error("Failed to read monitor status (JSON) data: {}", ex.getMessage());
                    out = new JSONObject();
                    out.put("lastRanDate", "(unknown)");
                    out.put("lastRan", 0L);
                    out.put("lastCheckedDate", "(unknown)");
                    out.put("lastChecked", 0L);
                }
            }
        }
        
        if (out == null) {
            out = new JSONObject();
            out.put("lastRanDate", "(never)");
            out.put("lastRan", 0L);
            out.put("lastCheckedDate", "(never)");
            out.put("lastChecked", 0L);
        }
        return out;
    }
    private void saveMonitorStatus(JSONObject status) throws CacheManagementException {
        synchronized (monstatus) {
            try (FileWriter wrtr = new FileWriter(monstatus)) {
                status.write(wrtr, 2, 0);
            }
            catch (IOException ex) {
                log.error("Trouble saving monitor status (JSON) data: {}", ex.getMessage());
                throw new CacheManagementException("Trouble saving monitor status (JSON) data: " +
                                                   ex.getMessage(), ex);
            }
        }
    }

    /**
     * record the results of the integrity monitor's work.  This includes both sending messages to 
     * the log and to a persistent file that can be consulted asynchronously.
     * @param checked    the number of files checked
     * @param deleted    the list of cache objects that were deleted because they failed the 
     *                      integrity check.
     * @throws CacheManagementException  if something goes wrong during the recording (such as IO errors).
     */
    public void recordMonitorResults(int checked, List<CacheObject> deleted)
        throws CacheManagementException
    {
        JSONObject rec = retrieveMonitorStatus();
        Instant ran = Instant.now();
        String ranDate = ZonedDateTime.ofInstant(ran, ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        rec.put("lastRan", ran.toEpochMilli());
        rec.put("lastRanDate", ranDate);

        if (checked > 0) {
            log.info("Monitor checked {} file{}", checked, (checked == 1) ? "" : "s");
            rec.put("lastChecked", ran.toEpochMilli());
            rec.put("lastCheckedDate", ranDate);
            rec.put("filecount", checked);

            JSONArray del = new JSONArray();
            if (deleted.size() > 0) {
                StringBuilder sb = new StringBuilder("Monitor deleted ");
                sb.append(Integer.toString(deleted.size())).append(" file")
                  .append((deleted.size() == 1) ? "" : "s")
                  .append(" with detected integrity failures");
                if (deleted.size() > 5)
                    sb.append(", including");
                sb.append(":");

                int i=0;
                for (CacheObject co : deleted) {
                    del.put(co.id);
                    if (++i <= 5)
                        // limit log listing to 5 files
                        sb.append("\n   ").append(co.id);
                }

                log.warn(sb.toString());
            }
            else
                log.info("No files detected with integrity failures");
            rec.put("deleted", del);
        }
        else
            log.info("Monitor completes cycle with no files to check");

        saveMonitorStatus(rec);
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
            log.info("Monitoring started with auto-repeat {}", (once) ? "off" : "on");

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
                        log.info("Running another check cycle...");
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
                cache(co.id, recache);
        }
        return deleted;
    }

    /**
     * cache all of the files from the given dataset
     * @param dsid     the AIP identifier for the dataset; this is either the old-style EDI-ID or 
     *                   local portion of the PDR ARK identifier (e.g., <code>"mds2-2119"</code>).  
     * @param version  the desired version of the dataset or null for the latest version
     * @param recache  if false and a file is already in the cache, the file will not be rewritten;
     *                    otherwise, all current cached files from the dataset will be replaced with a 
     *                    fresh copy.
     * @param prefs    any ANDed preferences for how to cache the data (particularly, where).  
     * @param target   a prefix collection name to insert the data files into within the cache
     */
    public Set<String> cacheDataset(String dsid, String version, CacheOpts opts, String target)
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        return ((PDRDatasetRestorer) restorer).cacheDataset(dsid, version, theCache, opts, target);
    }

    /**
     * a thread that will cache data in order of their IDs in an internal queue.  Objects can be added 
     * to the queue via {@link #queue(String)}.
     * <p>
     * The internal is persisted to a local file as IDs are added or removed from it.  
     */
    public class CachingThread extends Thread {
        File _queuef = null;
        String inprocess = null;

        CachingThread(File savedqueue) throws IOException {
            super("Cacher");
            _queuef = savedqueue;
            if (_queuef.exists())
                // test to make sure the queue is readable
                loadQueue();
        }

        synchronized void saveQueue(Queue<String> queue) throws IOException {
            BufferedWriter out = new BufferedWriter(new FileWriter(_queuef));
            try {
                for (String id : queue) {
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

        synchronized Queue<String> loadQueue() throws IOException {
            Queue<String> _queue = new ConcurrentLinkedQueue<String>();

            if (_queuef.exists()) {
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
            return _queue;
        }

        public void queue(String aipid, boolean recache) throws CacheManagementException {
            queue(aipid, recache, null);
        }

        public synchronized void queue(String aipid, boolean recache, String seq) throws CacheManagementException {
            CacheOpts opts = new CacheOpts(recache, 0, seq);
            aipid += "\t"+opts.serialize();
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter(_queuef, true));
                try {
                    out.write(aipid);
                    out.newLine();
                }
                finally {
                    try { out.close(); }
                    catch (IOException ex) {
                        log.warn("Trouble closing cache request queue file after addition: "+ex.getMessage());
                    }
                }
            } catch (IOException ex) {
                log.error("Can't queue: Trouble writing to persistent cache: "+ ex.getMessage());
                throw new CacheManagementException("Cache queue IO failure: "+ ex.getMessage());
            }
        }

        public boolean hasPending() {
            return _queuef.length() > 0;
        }

        public boolean isQueued(String aipid) {
            try {
                Queue<String> _queue = loadQueue();
                return ! _queue.stream().noneMatch(e -> e.startsWith(aipid+"\t"));
            } catch (IOException ex) {
                log.error("isQueued: status of "+aipid+" unknown; "+
                          "Can't access queue's persistent cache: "+ ex.getMessage());
                return false;
            }
        }

        public synchronized String popQueue() throws CacheManagementException {
            String out = null;
            try {
                Queue<String> _queue = loadQueue();
                out = _queue.poll();
                saveQueue(_queue);
            } catch (IOException ex) {
                log.error("Can't pop: trouble reading/writing queue's persistent cache: "+ex.getMessage());
            }
            return out;
        }

        public String cacheNext() throws CacheManagementException {
            String nextid = popQueue();
            if (nextid == null)
                return null;
            return cacheQueueItem(nextid);
        }

        protected String cacheQueueItem(String qitem) throws CacheManagementException {
            String[] parts = qitem.split("\\s*\\t\\s*");
            String nextid = parts[0];
            inprocess = nextid;

            CacheOpts opts = new CacheOpts();
            if (parts.length > 1) 
                opts = CacheOpts.parse(parts[1]);

            String version = null;
            if (parts.length > 2) version = parts[2];
            
            parts = ((PDRDatasetRestorer) restorer).parseId(nextid);
            try {
                if (parts[1].length() == 0) 
                    // dataset identifier
                    cacheDataset(parts[0], version, opts, null);
                else if (opts.recache || ! isCached(nextid))
                    // data file identifier
                    cache(nextid, true);
            }
            catch (ResourceNotFoundException ex) {
                throw new CacheManagementException("Unable to cache "+nextid+": resource not found", ex);
            }
            catch (StorageVolumeException ex) {
                throw new CacheManagementException("Storage trouble while caching "+nextid+": "+
                                                   ex.getMessage(), ex);
            }
            finally {
                inprocess = null;
            }

            return nextid;
        }

        public void run() {
            String item = null;
            try {
                if (hasPending())
                    log.info("Beginning queued cache request processing");
                while ((item = popQueue()) != null) {
                    if (interrupted()) throw new InterruptedException();
                    try {
                        item = cacheQueueItem(item);
                    }
                    catch (CacheManagementException ex) {
                        log.error(ex.getMessage());
                    }
                    catch (RuntimeException ex) {
                        log.error("Unexpected caching error: "+ex.getMessage()+" (moving on)");
                    }
                }
                log.info("Cache request queue is empty");
            }
            catch (InterruptedException ex) {
                log.info("Interruption of caching thread requested; exiting.");
            }
            catch (CacheManagementException ex) {
                log.error("Trouble reading cache queue: "+ex.getMessage());
            }
            catch (RuntimeException ex) {
                log.error("Unexpected caching exception while/after processing {}: {}",
                          item, ex.getMessage());
            }
            catch (Throwable ex) {
                log.error("Unexpected caching error while/after processing {}: {}: {}",
                          item, ex.getClass().getName(), ex.getMessage());
                throw ex;
            }
            finally {
                try {
                    synchronized (cath) {
                        cath = cloneMe();
                    }
                    /*
                     * if catch an error at this level, it's too big to restart
                     *
                    if (! this.isInterrupted() && hasPending()) {
                        log.warn("Resuming cache queue processing");
                        cath.start();
                    }
                    */
                    if (hasPending())
                        log.warn("Caching thread is exiting with requests unprocessed");
                        
                } catch (IOException ex) {
                    log.error("Failed to refresh caching thread: queue persistence error: "+ex.getMessage());
                } catch (RuntimeException ex) {
                    log.error("Failed to refresh caching thread: "+ex.getMessage());
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

