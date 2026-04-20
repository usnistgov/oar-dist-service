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

import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.storage.AWSS3LongTermStorage;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;
import software.amazon.awssdk.services.s3.S3Client;
import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.cachemgr.BasicCache;
import gov.nist.oar.distrib.cachemgr.ConfigurableCache;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.pdr.RestrictedDatasetRestorer;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagCacheManager;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagDB;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagRestorer;
import gov.nist.oar.distrib.cachemgr.pdr.PDRDatasetCacheManager;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * A factory for creating the {@link gov.nist.oar.distrib.service.RPACachingService} and it 
 * dependencies.
 */
public class RPACachingServiceProvider {

    RPAConfiguration rpacfg = null;
    NISTCacheManagerConfig cmcfg = null;
    BagStorage pubstore = null;
    S3Client s3client = null;

    BagStorage rpastore = null;
    HeadBagCacheManager hbcmgr = null;
    RPACachingService cacher = null;

    public RPACachingServiceProvider(NISTCacheManagerConfig cmConfig,
                                     RPAConfiguration rpaConfig,
                                     BagStorage publicBagStore,
                                     S3Client s3c)
    {
        rpacfg = rpaConfig;
        cmcfg = cmConfig;
        pubstore = publicBagStore;
        s3client = s3c;
    }
    
    private Logger _getLogger() {
        return LoggerFactory.getLogger("RPACaching");
    }

    /**
     * return true if this provider has the necessary configuration to create a CacheManager.  Note that 
     * this method does not check the validity of configuration; thus, createCacheManager() may still 
     * raise exceptions.  
     */
    protected boolean canCreateService() {
        if (cmcfg == null || cmcfg.getAdmindir() == null ||
            cmcfg.getAdmindir().equals("@null") || rpacfg.getBagstoreLocation() == null)
        {
            return false;
        }
        return true;
    }

    /**
     * return the instance of the HeadBagCacheManager that should be used as part of the 
     * {@link gov.nist.oar.distrib.service.RPACachingService}.
     * If one has not been created yet, it will be and cached in within this class. 
     * @throws ConfigurationException   if there is a problem with the configuration that prevents 
     *                                     creating the CacheManager instance.  
     */
    public HeadBagCacheManager getHeadBagCacheManager()
        throws ConfigurationException, IOException, CacheManagementException
    {
        if (hbcmgr == null)
            hbcmgr = createHeadBagManager();
        return hbcmgr;
    }

    /**
     * return an instance of the HeadBagCacheManager that should be used as part of the 
     * {@link gov.nist.oar.distrib.service.RPACachingService}.
     * @throws ConfigurationException   if there is a problem with the configuration that prevents 
     *                                     creating the CacheManager instance.  
     */
    protected HeadBagCacheManager createHeadBagManager()
        throws ConfigurationException, IOException, CacheManagementException
    {
        File rootdir = new File(cmcfg.admindir);
        if (! rootdir.exists() || ! rootdir.isDirectory())
            throw new ConfigurationException(rootdir+": Not an existing directory");

        File cmroot = new File(rootdir, "rpaHeadbags");
        if (! cmroot.exists()) cmroot.mkdir();

        // create the database
        Logger logger = LoggerFactory.getLogger(this.getClass());
        String rpaDbUrl = cmcfg.getRpaDburl();
        String dbType = NISTCacheManagerConfig.getDatabaseTypeFromUrl(rpaDbUrl);
        logger.info("Initializing RPA headbag inventory database from URL: {}",
                    rpaDbUrl != null ? rpaDbUrl.replaceAll("password=[^&]*", "password=***") : "null");

        HeadBagDB sidb;
        if ("postgres".equals(dbType)) {
            // PostgreSQL database
            String pgUrl = NISTCacheManagerConfig.extractDbUrlWithoutPrefix(rpaDbUrl);
            if (pgUrl == null || pgUrl.isEmpty())
                throw new ConfigurationException("PostgreSQL database URL (rpaDburl or dburl) must be configured with format: jdbc:postgresql://...");

            logger.info("Using PostgreSQL RPA headbag database");

            // Initialize PostgreSQL database schema if needed
            try {
                HeadBagDB.initializePostgresDB(pgUrl);
            } catch (InventoryException ex) {
                // Database may already be initialized, log and continue
                LoggerFactory.getLogger(this.getClass()).info("PostgreSQL RPA headbag database may already be initialized: " + ex.getMessage());
            }
            sidb = HeadBagDB.createPostgresDB(pgUrl);
        } else {
            // SQLite database (default or explicit jdbc:sqlite: URL)
            File dbf;
            if (rpaDbUrl != null && rpaDbUrl.startsWith("jdbc:sqlite:")) {
                // Use path from JDBC URL
                String sqlitePath = NISTCacheManagerConfig.extractDbUrlWithoutPrefix(rpaDbUrl);
                dbf = new File(sqlitePath).getAbsoluteFile();
            } else {
                // Use default path
                File dbrootdir = cmroot;
                dbf = new File(dbrootdir, "inventory.sqlite");
            }

            logger.info("Using SQLite RPA headbag database: {}", dbf.getAbsolutePath());

            if (! dbf.exists())
                HeadBagDB.initializeSQLiteDB(dbf.getAbsolutePath());
            if (! dbf.isFile())
                throw new ConfigurationException(dbf.toString()+": Not a file");
            sidb = HeadBagDB.createHeadBagDB(dbf.getAbsolutePath());
        }
        sidb.registerAlgorithm("sha256");

        // create the cache
        ConfigurableCache cache = new ConfigurableCache("rpaheadbags", sidb, 2, null);
        File cvd = new File(cmroot, "cv0");
        if (! cvd.exists()) cvd.mkdir();
        cache.addCacheVolume(new FilesystemCacheVolume(cvd, "cv0"),
                             rpacfg.getHeadbagCacheSize()/2, null, true);

        cvd = new File(cmroot, "cv1");
        if (! cvd.exists()) cvd.mkdir();
        cache.addCacheVolume(new FilesystemCacheVolume(cvd, "cv1"),
                             rpacfg.getHeadbagCacheSize()/2, null, true);

        return new HeadBagCacheManager(cache, sidb, new HeadBagRestorer(getRPBagStorage(), pubstore), 
                                       cmcfg.getArkNaan());
    }

    /**
     * return the BagStorage instance to use to access restricted public bags.
     */
    public BagStorage getRPBagStorage() throws ConfigurationException {
        if (rpastore == null)
            rpastore = createRPBagStorage();
        return rpastore;
    }

    /**
     * create the BagStorage instance to use to access restricted public bags.
     */
    public BagStorage createRPBagStorage() throws ConfigurationException {
        String storeloc = rpacfg.getBagstoreLocation();
        if (storeloc == null)
            throw new ConfigurationException("Missing config parameter: distrib.rpa.bagstore-location");

        String mode = rpacfg.getBagstoreMode();
        if (mode == null || mode.length() == 0)
            throw new ConfigurationException("Missing config parameter: distrib.rpa.bagstore-mode");

        try {
            if (mode.equals("aws") || mode.equals("remote")) {
                return new AWSS3LongTermStorage(storeloc, s3client);
            } else if (mode.equals("local")) {
                return new FilesystemLongTermStorage(storeloc);
            } else {
                throw new ConfigurationException("distrib.rpa.bagstore-mode",
                        "Unsupported storage mode: " + mode);
            }
        } catch (FileNotFoundException ex) {
            throw new ConfigurationException("distrib.rpa.bagstore-location",
                    "RP Storage Location not found: " + ex.getMessage(), ex);
        } catch (StorageVolumeException ex) {
            throw new ConfigurationException("distrib.rpa.bagstore",
                    "Failed to initialize AWS S3 storage: " + ex.getMessage(), ex);
        }
    }

    /**
     * return the RP data restorer to use to restore restricted public data into the cache
     */
    public RestrictedDatasetRestorer createRPDatasetRestorer()
        throws ConfigurationException, IOException, CacheManagementException
    {
        RestrictedDatasetRestorer rdr = new RestrictedDatasetRestorer(pubstore, getRPBagStorage(), getHeadBagCacheManager());
        rdr.setExpiryTime(rpacfg.getExpiresAfterMillis());
        return rdr;
        // return new PDRDatasetRestorer(getRPBagStorage(), getHeadBagCacheManager());
    }

    /**
     * return the cache manager to use to place requested restricted public data into the cache
     */
    public PDRDatasetCacheManager createRPACacheManager(BasicCache cache)
        throws ConfigurationException, IOException, CacheManagementException
    {
        return new PDRDatasetCacheManager(cache, createRPDatasetRestorer(), _getLogger());
    }

    /**
     * instantiate an RPACachingService to be used to as the RPA service backend.  Called by
     * {@link #getRPACachingService()}, this method will always create a new instance; thus a 
     * Spring boot configuration should call {@link #getRPACachingService()} instead so as to use 
     * a single instance across the whole application.
     * @param s3   an AmazonS3 interface for accessing S3 buckets for storage (as specified in
     *             the configuration)
     */
    protected RPACachingService createRPACachingService(S3Client s3) 
        throws ConfigurationException, IOException, CacheManagementException
    {
        return new RPACachingService(createRPACacheManager(cmcfg.getCache(s3)), rpacfg);
    }

    /**
     * return a singleton instance of RPACachingService created from the configuration for the 
     * application.  If one has not been created yet, it will be and cached in within this class. 
     * @param s3   an AmazonS3 interface for accessing S3 buckets for storage (as specified in
     *             the configuration); ignored if the service has already been created.
     */
    public RPACachingService getRPACachingService(S3Client s3) 
        throws ConfigurationException, IOException, CacheManagementException
    {
        if (cacher == null && canCreateService())
            cacher = createRPACachingService(s3);
        return cacher;
    }
}
