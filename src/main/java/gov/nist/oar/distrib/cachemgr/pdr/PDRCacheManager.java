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
import gov.nist.oar.distrib.cachemgr.inventory.JDBCStorageInventoryDB;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collection;
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
 * distribution service.  It adds the functionality of a separate thread that can continually the 
 * the integrity of the objects in the cache via an {@link gov.nist.oar.distrib.cachemgr.IntegrityMonitor}.
 */
public class PDRCacheManager extends BasicCacheManager implements PDRConstants {

    IntegrityMonitor datamon = null;
    IntegrityMonitor hbmon = null;
    Logger log = null;
    MonitorThread month = null;

    public PDRCacheManager(BasicCache cache, PDRDatasetRestorer restorer, List<CacheObjectCheck> checklist,
                           long dutycyclemsec, long graceperiod, long startoffset, Logger logger)
    {
        super(cache, restorer);
        datamon = cache.getIntegrityMonitor(checklist);
        hbmon = restorer.getIntegrityMonitor(checklist);
        if (logger == null)
            logger = LoggerFactory.getLogger(getClass());
        month = new MonitorThread(dutycyclemsec, graceperiod, startoffset, false);
        log = logger;
    }

    /**
     * cache all of the files from the given dataset
     */
    public void cacheDataset(String id, String version)
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        ((PDRDatasetRestorer) restorer).cacheDataset(id, version, theCache);
    }

    public void dispatchMonitorResults(int checked, List<CacheObject> deleted) {
        logMonitorResults(checked, deleted);
        // FUTURE: email results
    }

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

    public MonitorThread getMonitorThread() {
        return month;
    }

    public class MonitorThread extends Thread {
        long dutycycle = 30 * 60000;   // 30 minutes
        long start = 0L;
        long grace = 24 * 3600000;  // 24 hours
        boolean stopsoon = false;
        boolean once = false;

        MonitorThread(long dutycyclemsec, long graceperiod, long startoffset, boolean runonce) {
            setCycling(dutycyclemsec, graceperiod, startoffset);
            once = runonce;
        }

        public void setCycling(long dutycyclemsec, long graceperiodmsec, long startoffset) {
            if (dutycyclemsec >= 0)
                dutycycle = dutycyclemsec;
            if (graceperiodmsec >= 0)
                grace = graceperiodmsec;
            initstart(startoffset);
        }

        public boolean isContinuous() {
            return ! once;
        }
        public void setContinuous(boolean yes) {
            once = ! yes;
        }
        public void stopSoon() {
            stopsoon = true;
            log.debug("Monitor stop requested; will do so at next interval.");
        }
        public void stopSoonAndWait() {
            stopSoon();
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
                    while (! stopsoon) {
                        if (untilnext <= 0) {
                            checked = monitorUntilDone(deleted, 100, 100);
                            resetStart();
                            dispatchMonitorResults(checked, deleted);
                            log.debug("Next check at epochMilli={}", start);
                            deleted = new ArrayList<CacheObject>();
                            checked = 0;
                        }
                        else {
                            try {
                                sleep(untilnext);
                            } catch (InterruptedException ex) { break; }
                        }
                        untilnext = start - Instant.now().toEpochMilli();

                        // if the status of once changed, stop cycling
                        if (once) break;
                    }
                    stopsoon = false;
                }
            }
            catch (StorageVolumeException ex) {
                log.error("Storage volume problem halted processing: {}", ex.getMessage());
            }
            catch (CacheManagementException ex) {
                log.error("Caching failure halted processing: {}", ex.getMessage());
            }
        }

        public int monitorOnce(IntegrityMonitor mon, List<CacheObject> deleted, int maxobjs) 
            throws StorageVolumeException, CacheManagementException
        {
            if (interrupted()) stopsoon = true;
            if (stopsoon) return 0;
            return mon.findCorruptedObjects(maxobjs, deleted, true);
        }
        public int monitorOnce(List<CacheObject> deleted, int hbmax, int dmax) 
            throws StorageVolumeException, CacheManagementException
        {
            int count = 0;
            count = monitorOnce(hbmon, deleted, hbmax);
            count +=monitorOnce(datamon, deleted, dmax);
            return count;
        }
        public int monitorUntilDone(List<CacheObject> deleted, int hbmax, int dmax) 
            throws StorageVolumeException, CacheManagementException
        {
            int out = 0;
            int count = -1;
            while (count != 0) {
                count = monitorOnce(deleted, hbmax, dmax);
                out += count;
            }
            return out;
        }
    }

    /**
     * create a name for a data object within a particular {@link CacheVolume}.  
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

    public JSONArray summarizeVolumes() throws InventoryException {
        StorageInventoryDB db = getInventoryDB();
        Collection<String> names = db.volumes();
        JSONArray out = new JSONArray();
        JSONObject row = null;
        for (String name : names) {
            row = db.getVolumeInfo(name);
            row.put("name", name);
            out.put(row);
        }
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
        PDRStorageInventoryDB sidb = getInventoryDB();
        
        List<CacheObject> cached = sidb.selectObjectsLikeID(aipid+"/%", sidb.VOL_FOR_UPDATE);
        for (CacheObject co : cached) 
            co.volume = theCache.getVolume(co.volname);

        List<CacheObject> deleted = new ArrayList<CacheObject>();
        datamon.selectCorruptedObjects(cached, deleted, true);
        if (recache) {
            for (CacheObject co : deleted)
                cache(co.id);
        }
        return deleted;
    }

    public JSONArray summarizeContents(String volname) throws InventoryException {
        String qsel = "SELECT d.ediid,count(*) as count,sum(d.size) as totsz,max(d.since) as newest," +
                      "min(d.checked) as oldest FROM objects d, volumes v WHERE d.volume=v.id AND d.cached=1";

        if (volname != null) 
            qsel += " AND v.name='" + volname + "'";
        else
            qsel += " AND v.name!='old'";
        qsel += " GROUP BY d.ediid ORDER BY oldest";

        PDRStorageInventoryDB sidb = getInventoryDB();
        try {
            ResultSet res = sidb.query(qsel);
            JSONArray out = new JSONArray();
            JSONObject row = null;
            while (res.next()) {
                row = new JSONObject();
                String aipid = PDR_ARK_PAT.matcher(res.getString("ediid")).replaceFirst("");
                row.put("aipid",     aipid);
                row.put("filecount", res.getInt("count"));
                row.put("totalsize", res.getLong("totsz"));
                row.put("since",     res.getLong("newest"));
                row.put("sinceDate", ZonedDateTime.ofInstant(Instant.ofEpochMilli(res.getLong("newest")),
                                                             ZoneOffset.UTC)
                                                  .format(DateTimeFormatter.ISO_INSTANT));

                long timems = res.getLong("oldest");
                row.put("checked",   timems);
                if (timems == 0L)
                    row.put("checkedDate", "(never)");
                else
                    row.put("checkedDate", ZonedDateTime.ofInstant(Instant.ofEpochMilli(res.getLong("oldest")),
                                                                   ZoneOffset.UTC)
                                                        .format(DateTimeFormatter.ISO_INSTANT));
                out.put(row);
            }
            return out;
        }
        catch (SQLException ex) {
            throw new InventorySearchException(ex);
        }
    }
}

