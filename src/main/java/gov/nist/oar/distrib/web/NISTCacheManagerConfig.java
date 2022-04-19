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
 */
package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.cachemgr.BasicCache;
import gov.nist.oar.distrib.cachemgr.ConfigurableCache;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.storage.AWSS3CacheVolume;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.cachemgr.VolumeStatus;
import gov.nist.oar.distrib.cachemgr.VolumeConfig;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.DeletionStrategy;
import gov.nist.oar.distrib.cachemgr.inventory.JDBCStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.inventory.OldSelectionStrategy;
import gov.nist.oar.distrib.cachemgr.inventory.BigOldSelectionStrategy;
import gov.nist.oar.distrib.cachemgr.inventory.BySizeSelectionStrategy;
import gov.nist.oar.distrib.cachemgr.inventory.ChecksumCheck;
import gov.nist.oar.distrib.cachemgr.restore.FileCopyRestorer;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheManager;
import gov.nist.oar.distrib.cachemgr.pdr.PDRDatasetRestorer;
import gov.nist.oar.distrib.cachemgr.pdr.PDRStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheRoles;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagCacheManager;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagDB;
import gov.nist.oar.distrib.BagStorage;

import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.net.MalformedURLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.amazonaws.services.s3.AmazonS3;

/**
 * The configuration for an application's use of a data cache.
 * <p>
 * It is expected that this class will be instantiated and intialized by the Spring configuration 
 * infrastructure from properties pulled in from the configuration server.  All of the properties 
 * captured by this class are expected to have a common property name prefix (e.g. 
 * <code>distrib.cachemgr</code>) which is set by the application configuration 
 * ({@link NISTDistribServiceConfig}).  The following subproperties are captured:
 * <dl>
 *   <dt> <b><code>admindir</code></b> (string)  </dt>
 *   <dd> a root directory for the manager's internal data, including the storage inventory 
 *        databases, and the head bag cache.  Local cache volumes will be located here when 
 *        a such volumes data directory is configured via a relative path (see 
 *        {@link NISTCacheManagerConfig.CacheVolumeConfig}. </dd>
 *   <dt> <b><code>dbrootdir</code></b> (string)  </dt>
 *   <dd> the directory containing the inventory database data.  Default is <code>admindir</code>. </dd>
 *   <dt> <b><code>smallSizeLimit</code></b> (long integer)  </dt>
 *   <dd> The size limit, in bytes, for a file to be considered small and eligible to be stored 
 *        the "small file" volume.  Default is 10 MB.</dd>
 *   <dt> <b><code>headbagDbrootdir</code></b> (string)  </dt>
 *   <dd> the directory containing the inventory database data specifically for the headbag cache.  
 *        Default is <code>admindir/headbags</code>. </dd>
 *   <dt> <b><code>headbagCacheSize</code></b> (long integer)  </dt>
 *   <dd> The total size limit for the headbag cache.  Note that this size will be split between two 
 *        volumes </dd>
 *   <dt> <b><code>arkNAAN</code></b> (string of integers)  </dt>
 *   <dd> The NAAN--i.e. the integer string namespace controlled by the operating organization--used 
 *        in the ARK identifiers assigned by the PDR.  This defaults to the NIST ARK NAAN, "88434".</dd>
 *   <dt> <b><code>volumes</code></b> (list)  </dt>
 *   <dd> a list where each element configures a cache volume within the cache.  See 
 *        {@link NISTCacheManagerConfig.CacheVolumeConfig} for the subproperties that can be 
 *        included in each element. </dd>
 *   <dt> <b><code>triggerCache</code></b> (long integer)  </dt>
 *   <dd> if True, requests for files not in the cache will trigger automatic caching of that and 
 *        related files (the other files in the dataset). </dd>
 * </ul>
 */
public class NISTCacheManagerConfig {
    /** the ARK NAAN assigned to NIST */
    public final static String NIST_ARK_NAAN = "88434";

    String admindir = null;
    List<CacheVolumeConfig> volumes;
    boolean startmonitor = false;     // don't start the integrity monitor thread on startup
    long smallszlim = 10000000;       // default: 10 MB
    long dutycycle = 20 * 60;         // 20 mins
    long graceperiod = 24 * 3600;     // 24 hours
    long headbagcachesize = 50000000; // 50 MB
    String arknaan = NIST_ARK_NAAN;
    String dbroot = null;
    String hbdbroot = null;
    boolean triggercache = false;

    public String getAdmindir() { return admindir; }
    public void setAdmindir(String dirpath) { admindir = dirpath; }
    public String getArkNaan() { return arknaan; }
    public void setArkNaan(String naan) { arknaan = naan; }
    public long getSmallSizeLimit() { return smallszlim; }
    public void setSmallSizeLimit(long bytes) { smallszlim = bytes; }
    public boolean getMonitorAutoStart() { return startmonitor; }
    public void setMonitorAutoStart(boolean start) { startmonitor = start; }
    public long getCheckDutyCycle() { return dutycycle; }
    public void setCheckDutyCycle(long cyclesec) { dutycycle = cyclesec; }
    public long getCheckGracePeriod() { return graceperiod; }
    public void setCheckGracePeriod(long sec) { graceperiod = sec; }
    public long getHeadbagCacheSize() { return headbagcachesize; }
    public void setHeadbagCacheSize(long size) { headbagcachesize = size; }
    public List<CacheVolumeConfig> getVolumes() { return volumes; }
    public void setVolumes(List<CacheVolumeConfig> volcfgs) { volumes = volcfgs; }
    public String getDbrootdir() { return dbroot; }
    public void   setDbrootdir(String dir) { dbroot = dir; }
    public String getHeadbagDbrootdir() { return hbdbroot; }
    public void   setHeadbagDbrootdir(String dir) { hbdbroot = dir; }
    public boolean getTriggerCache() { return triggercache; }
    public void setTriggerCache(boolean trigger) { triggercache = trigger; }

    /**
     * the configuration of a volume within the cache.  It is expected to be part of a list of 
     * volume configurations, one for each volume to be included in the cache.  
     * <p>
     * The following sub-properties are supported:
     * <ul>
     *   <dt> <b><code>location</code></b> (string)  </dt>
     *   <dd> A URL indicating both the type and location of the cache.  If the URL starts with "s3:/",
     *        the volume is of type {@link gov.nist.oar.distrib.cachemgr.storage.AWSS3CacheVolume}.  The 
     *        rest of the path indicates the bucket and the path to a root folder within the bucket.
     *        If the URL starts with "file:/", then the volume is to be a local volume of type 
     *        {@link gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume}.  If the URL starts 
     *        with "file:///", then the path following it should be taken as an absolute path to a local 
     *        directory that should be the root of the storage; otherwise, the path will be interpreted 
     *        as directory path relative to the <code>admindir</code> directory.  
     *   </dd>
     * 
     *   <dt> <b><code>capacity</code></b> (long)  </dt>
     *   <dd> the maximum number of bytes allowed in the volume. </dd>
     * 
     *   <dt> <b><code>status</code></b> (string)  </dt>
     *   <dd> the status to assign to the volume, one of "update", "get", "info".  These correspond
     *        to the values and meaning defined in {@link gov.nist.oar.distrib.cachemgr.VolumeStatus}. 
     *        In particular, data cannot be added to, checked, or deleted from a volume unless the status is 
     *        "update", and data cannot be retrieved from a volume unless that status is "update" or "get".
     *        If the value is "info", only the metadata for items assigned to the volume are available.  
     *   </dd>
     *   
     *   <dt> <b><code>roles</code></b> (list)  </dt>
     *   <dd> a list of string labels that indicate what kind of data should be stored there.  Values 
     *        include "small" (small files can go in this volume), "large" (for large files), "fast"
     *        (for files requiring fast access), "old" (for deprecated versions of data files), and
     *        "general" (for any kind of file).
     *   </dd>
     * 
     *   <dt> <b><code>deletionStrategy</code></b> (Map&lt;String,Object&gt;)  </dt>
     *   <dd> a dictionary that configures the deletion strategy used to clear space in this volume when
     *        a new object needs to be added .  The value must contain at least the property "type", a
     *        string that determines the {@link gov.nist.oar.distrib.cachemgr.DeletionStrategy} class that 
     *        will be used to select objects to delete; this type determines the other configuration 
     *        properties that can appear in the dictionary as follows for the recognized type values:
     *        <dl>
     *          <dt> "oldest" -- oldest files are deleted first (via 
     *               {@link gov.nist.oar.distrib.cachemgr.inventory.OldSelectionStrategy}).  <br></br> 
     *               Companion properties</dt>
     *          <dd> <b><code>priority0</code></b> -- scaling factor (see 
     *               {@link gov.nist.oar.distrib.cachemgr.inventory.OldSelectionStrategy} docs). </dd>
     *               Default value is 10.  </dd>
     *          <dd> <b><code>minage</code></b> -- minimum age of a file (in msecs) before it is eligible 
     *               to be deleted.  Default value is 1 hour.  </dd>
     *          <dt> "biggest" -- oldest files are deleted first (via 
     *               {@link gov.nist.oar.distrib.cachemgr.inventory.BySizeSelectionStrategy}). </dt>
     *          <dd> <b><code>normSize</code></b> -- scaling factor (see 
     *               {@link gov.nist.oar.distrib.cachemgr.inventory.BySizeSelectionStrategy} docs). </dd>
     *               Default value is 1.0.  </dd>
     *          <dt> "bigoldest" -- the oldest and biggest files are deleted first (via 
     *               {@link gov.nist.oar.distrib.cachemgr.inventory.BigOldSelectionStrategy}
     *               using a nuanced formula). </dt>
     *          <dd> <b><code>sizeTurnOver</code></b> -- size at which its affect on the deletion
     *               score flattens (see 
     *               {@link gov.nist.oar.distrib.cachemgr.inventory.BySizeSelectionStrategy} docs). 
     *               Default value is 0.5 GB.  </dd>
     *          <dd> <b><code>ageTurnOver</code></b> -- age at which it begins to affect the deletion
     *               score (linearly; see 
     *               {@link gov.nist.oar.distrib.cachemgr.inventory.BySizeSelectionStrategy} docs). 
     *               Default value is 2.5 hours. </dd>
     *        <ul> 
     *   </dd>
     * 
     *   <dt> <b><code>redirectBase</code></b> (string)  </dt>
     *   <dd> a base URL for accessing objects in the volume by name (see 
     *        {@link gov.nist.oar.distrib.cachemgr.CacheVolume#getRedirectFor(String)}). </dd>
     * </dl>
     * 
     */
    public static class CacheVolumeConfig {
        /** the default deletion strategy to use with a volume if one is not set by configuration */
        public final static String DEFAULT_DELETION_STRATEGY_TYPE = "oldest";

        private long capacity;
        private String location;    // either "s3:/..." or "file://.."
        private String status = "update";
        private List<String> roles = null;
        private Map<String,Object> delStrat = null;
        private String redirectbase = null;
        private String volname = null;

        public long     getCapacity()           { return capacity; }
        public void     setCapacity(long cap)   { capacity = cap;  }
        public String   getLocation()           { return location; }
        public void     setLocation(String loc) { location = loc;  }
        public String   getStatus()             { return status;   }
        public void     setStatus(String s)     { status = s;      }
        public List<String> getRoles()              { return roles;    }
        public void     setRoles(List<String> rolelist) { roles = rolelist; }
        public Map<String,Object> getDeletionStrategy() { return delStrat; }
        public void     setDeletionStrategy(Map<String,Object> strat) { delStrat = strat; }
        public String   getRedirectBase() { return redirectbase; }
        public void     setRedirectBase(String urlbase) { redirectbase = urlbase; }
        public String   getName()             { return volname;   }
        public void     setName(String n)     { volname = n;      }

        int getStatusCode() throws ConfigurationException {
            if ("info".equals(getStatus()))
                return VolumeStatus.VOL_FOR_INFO;
            if ("get".equals(getStatus()))
                return VolumeStatus.VOL_FOR_GET;
            if ("update".equals(getStatus()))
                return VolumeStatus.VOL_FOR_UPDATE;

            throw new ConfigurationException("volumes[].status", "Unrecognized value: "+getStatus());
        }

        /**
         * construct a {@link gov.nist.oar.distrib.cachemgr.DeletionStrategy} instance base on the value 
         * of the <code>deletionStrategy</code> configuration.
         */
        public DeletionStrategy createDeletionStrategy() throws ConfigurationException {
            Map<String, Object> use = delStrat;
            if (use == null) {
                use = new HashMap<String, Object>();
                use.put("type", DEFAULT_DELETION_STRATEGY_TYPE);
            }
            if (! use.containsKey("type"))
                throw new ConfigurationException("deletionStrategy is missing required sub-property, type");

            if ("oldest".equals(use.get("type"))) {
                int p0 = 10;
                if (use.containsKey("priority0")) {
                    try {
                        p0 = ((Number) use.get("priority0")).intValue();
                    } catch (ClassCastException ex) {
                        throw new ConfigurationException("deletionStrategy sub-property, priority0, not an " +
                                                         "int: " + use.get("priority0").toString());
                    }
                }
                return new OldSelectionStrategy(1, 1, p0);
            }

            if ("biggest".equals(use.get("type"))) {
                double normsz = 1.0;
                if (use.containsKey("normSize")) {
                    try {
                        normsz = ((Number) use.get("normSize")).doubleValue();
                    } catch (ClassCastException ex) {
                        throw new ConfigurationException("deletionStrategy sub-property, normSize, not a " +
                                                         "double: " + use.get("normSize").toString());
                    }
                }
                return new BySizeSelectionStrategy(1, normsz);
            }

            if ("bigoldest".equals(use.get("type"))) {
                double[] param = {
                    2.5 * 3600000,   // ageTurnOver: 2.5 hours
                    0.5 * 1.0e9      // sizeTurnOver: 0.5 GB
                };
                String[] props = { "ageTurnOver", "sizeTurnOver" };
                for (int i=0; i < param.length; i++) {
                    if (use.containsKey(props[i])) {
                        try {
                            param[i] = ((Number) use.get(props[i])).doubleValue();
                        } catch (ClassCastException ex) {
                            throw new ConfigurationException("deletionStrategy sub-property, " + props[i] +
                                                             ", not a double: " +
                                                             use.get(props[i]).toString());
                        }
                    }
                }
                return new BigOldSelectionStrategy(1L, param[0], param[1]);
            }

            throw new ConfigurationException("Unrecognized deletionStrategy type: "+
                                             use.get("type").toString());
        }

        /**
         * create a CacheVolume as prescribed by this configuration
         */
        public CacheVolume createCacheVolume(NISTCacheManagerConfig mgrcfg, AmazonS3 s3client)
            throws ConfigurationException, FileNotFoundException, MalformedURLException, CacheManagementException
        {
            if (location == null || location.length() == 0)
                throw new ConfigurationException("Missing cache volume config parameter: "+location);
            
            // parse the location URL
            Pattern typescheme = Pattern.compile("^(\\w+)://?");
            Matcher m = typescheme.matcher(location);
            if (! m.find())
                throw new ConfigurationException("Bad cache volume location URL: "+location);
            if (m.group(1).equals("s3")) {
                if (s3client == null)
                    throw new ConfigurationException("S3 client instance not availabe for AWS volume, "
                                                     +location);

                // S3 bucket; note: location starts with "s3:/"
                try {
                    Path bucketfolder = Paths.get(location.substring(m.end()));
                    return new AWSS3CacheVolume(bucketfolder.subpath(0,1).toString(),
                                                bucketfolder.subpath(1, bucketfolder.getNameCount()).toString(), 
                                                getName(), s3client, getRedirectBase());
                } catch (InvalidPathException ex) {
                    throw new ConfigurationException("Invalid s3 location URL: " + location);
                }
            }
            else if (m.group(1).equals("file")) {

                // directory on local filesystem; location will start with "file:///" when path is absolute
                try {
                    Path cachedir = Paths.get(location.substring(m.end()));
                    if (! cachedir.isAbsolute()) {
                        cachedir = Paths.get(mgrcfg.getAdmindir()).resolve(cachedir);
                        if (! Files.exists(cachedir)) {
                            try {
                                Files.createDirectories(cachedir);
                            } catch (IOException ex) {
                                throw new CacheManagementException("Unable to initialize new FilesystemCacheVolume: " +
                                                                   "failed to create volume root directory: "+
                                                                   ex.getMessage(), ex);
                            }
                        }
                    }
                    String name = getName();
                    if (name == null)
                        name = location.substring(m.end());
                    name = name.replace("^/+", "/");
                    return new FilesystemCacheVolume(cachedir.toFile(), name, getRedirectBase());
                }
                catch (InvalidPathException ex) {
                    throw new ConfigurationException("Invalid file location URL: " + location);
                }
            }
            else
                throw new ConfigurationException("Unrecognized volume type, "+m.group(0)+" for "+location);
        }
    }

    public BasicCache createDefaultCache(AmazonS3 s3)
        throws ConfigurationException, IOException, CacheManagementException
    {
        // establish the base directory
        File rootdir = new File(admindir);
        if (! rootdir.exists() || ! rootdir.isDirectory())
            throw new ConfigurationException(rootdir+": Not an existing directory");
        if (volumes == null || volumes.size() == 0)
            throw new ConfigurationException("No cache volumes are configured!");

        // set up the inventory database
        File dbrootdir = rootdir;
        if (dbroot != null)
            dbrootdir = new File(dbroot);
        File dbfile = new File(dbrootdir, "data.sqlite");
        File dbf = dbfile.getAbsoluteFile();
        if (! dbf.exists()) 
            PDRStorageInventoryDB.initializeSQLiteDB(dbf.toString());
        if (! dbf.isFile())
            throw new ConfigurationException(dbfile+": Not a file");
        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
        sidb.registerAlgorithm("sha256");

        // create the cache
        ConfigurableCache cache = new ConfigurableCache("data", sidb, volumes.size(),
                                                        LoggerFactory.getLogger("data-cache"));

        // configure/attach volumes
        VolumeConfig vc = null;
        for(CacheVolumeConfig cfg : volumes) {
            int roles = 0;
            if (cfg.getRoles() != null && cfg.getRoles().size() > 0) {
                Collection<String> rolenms = cfg.getRoles();
                if (rolenms.contains("general")) roles |= PDRCacheRoles.ROLE_GENERAL_PURPOSE;
                if (rolenms.contains("fast")) roles |= PDRCacheRoles.ROLE_FAST_ACCESS;
                if (rolenms.contains("small")) roles |= PDRCacheRoles.ROLE_SMALL_OBJECTS;
                if (rolenms.contains("large")) roles |= PDRCacheRoles.ROLE_LARGE_OBJECTS;
                if (rolenms.contains("old")) roles |= PDRCacheRoles.ROLE_OLD_VERSIONS;
                if (rolenms.contains("restricted")) roles |= PDRCacheRoles.ROLE_RESTRICTED_DATA;
            }

            vc = (new VolumeConfig(cfg.getStatusCode())).withDeletionStrategy(cfg.createDeletionStrategy())
                                                        .withRoles(roles);
            cache.addCacheVolume(cfg.createCacheVolume(this, s3), cfg.getCapacity(), null, vc, false);
        }
                
        return cache;
    }

    public HeadBagCacheManager createHeadBagManager(BagStorage ltstore)
        throws ConfigurationException, IOException, CacheManagementException
    {
        File rootdir = new File(admindir);
        if (! rootdir.exists() || ! rootdir.isDirectory())
            throw new ConfigurationException(rootdir+": Not an existing directory");

        File cmroot = new File(rootdir, "headbags");
        if (! cmroot.exists()) cmroot.mkdir();

        // create the database
        File dbrootdir = cmroot;
        if (hbdbroot != null)
            dbrootdir = new File(hbdbroot);
        File dbf = new File(dbrootdir, "inventory.sqlite");
        if (! dbf.exists())
            HeadBagDB.initializeSQLiteDB(dbf.getAbsolutePath());
        if (! dbf.isFile())
            throw new ConfigurationException(dbf.toString()+": Not a file");
        HeadBagDB sidb = HeadBagDB.createHeadBagDB(dbf.getAbsolutePath());
        sidb.registerAlgorithm("sha256");

        // create the cache
        ConfigurableCache cache = new ConfigurableCache("headbags", sidb, 2, null);
        File cvd = new File(cmroot, "cv0");
        if (! cvd.exists()) cvd.mkdir();
        cache.addCacheVolume(new FilesystemCacheVolume(cvd, "cv0"), getHeadbagCacheSize()/2, null, true);
        
        cvd = new File(cmroot, "cv1");  
        if (! cvd.exists()) cvd.mkdir();
        cache.addCacheVolume(new FilesystemCacheVolume(cvd, "cv1"), getHeadbagCacheSize()/2, null, true);

        return new HeadBagCacheManager(cache, sidb, ltstore, new FileCopyRestorer(ltstore), getArkNaan());
    }

    public PDRDatasetRestorer createDefaultRestorer(BagStorage lts, HeadBagCacheManager hbmgr) {
        return new PDRDatasetRestorer(lts, hbmgr, smallszlim);
    }

    /**
     * create a cache manager built from this configuration.  
     * @param cache      the cache to manage; typically, this is the instance returned by 
     *                   {@link #createDefaultCache()} but it need not be.
     * @param rstr       the {@link gov.nist.oar.distrib.cachemgr.pdr.PDRDatasetRestorer Restorer} to use
     *                   to restore datasets to the cache; typically, this is the instance returned by
     *                   {@link #createDefaultRestorer(BagStorage,HeadBagCacheManager)} but it need not be.
     * @param logger     the Logger instance to use for log messages from the manager; if null, a default 
     *                   will be created.
     */
    public PDRCacheManager createCacheManager(BasicCache cache, PDRDatasetRestorer rstr, Logger logger)
        throws IOException, ConfigurationException
    {
        File rootdir = new File(admindir);
        if (! rootdir.exists() || ! rootdir.isDirectory())
            throw new ConfigurationException(rootdir+": Not an existing directory");

        List<CacheObjectCheck> checks = new ArrayList<CacheObjectCheck>();
        checks.add(new ChecksumCheck(false, true));
        PDRCacheManager out = new PDRCacheManager(cache, rstr, checks, getCheckDutyCycle()*1000, 
                                                  getCheckGracePeriod()*1000, -1, rootdir, logger);
        if (getMonitorAutoStart()) {
            PDRCacheManager.MonitorThread mt = out.getMonitorThread();
            mt.setContinuous(true);
            if (! mt.isAlive())
                mt.start();
        }
        return out;
    }
}
