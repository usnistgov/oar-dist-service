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
import gov.nist.oar.distrib.storage.AWSS3LongTermStorage;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;
import gov.nist.oar.distrib.cachemgr.BasicCache;
import gov.nist.oar.distrib.cachemgr.ConfigurableCache;
import gov.nist.oar.distrib.cachemgr.CacheManager;
import gov.nist.oar.distrib.cachemgr.simple.SimpleCacheManager;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.pdr.PDRDatasetRestorer;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagCacheManager;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagDB;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.cachemgr.restore.FileCopyRestorer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.amazonaws.services.s3.AmazonS3;

/**
 * A factory for creating the {@link gov.nist.oar.distrib.service.RPACachingService} and it 
 * dependencies.
 */
public class RPACachingServiceProvider {

    RPAConfiguration rpacfg = null;
    NISTCacheManagerConfig cmcfg = null;
    BagStorage pubstore = null;
    AmazonS3 s3client = null;

    BagStorage rpastore = null;
    HeadBagCacheManager hbcmgr = null;

    public RPACachingServiceProvider(NISTCacheManagerConfig cmConfig,
                                     RPAConfiguration rpaConfig,
                                     BagStorage publicBagStore,
                                     AmazonS3 s3c)
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
        File dbrootdir = cmroot;
        File dbf = new File(dbrootdir, "inventory.sqlite");
        if (! dbf.exists())
            HeadBagDB.initializeSQLiteDB(dbf.getAbsolutePath());
        if (! dbf.isFile())
            throw new ConfigurationException(dbf.toString()+": Not a file");
        HeadBagDB sidb = HeadBagDB.createHeadBagDB(dbf.getAbsolutePath());
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

        // return new HybridHeadBagCacheManager(cache, sidb, getRPBagStorage(), pubstore);
        return new HeadBagCacheManager(cache, sidb, getRPBagStorage(),
                                       new FileCopyRestorer(getRPBagStorage()), cmcfg.getArkNaan());
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
            throw new ConfigurationException("Missing config parameter: distrib.rpa.bagstore.location");
        String mode = rpacfg.getBagstoreMode();
        if (mode == null || mode.length() == 0)
            throw new ConfigurationException("Missing config parameter: distrib.rpa.bagstore.mode");

        try {
            if (mode.equals("aws") || mode.equals("remote")) 
                return new AWSS3LongTermStorage(storeloc, s3client);
            else if (mode.equals("local")) 
                return new FilesystemLongTermStorage(storeloc);
            else
                throw new ConfigurationException("distrib.bagstore.mode",
                                                 "Unsupported storage mode: "+ mode);
        }
        catch (FileNotFoundException ex) {
            throw new ConfigurationException("distrib.rpa.bagstore.location",
                                             "RP Storage Location not found: "+ex.getMessage(), ex);
        }
    }

    /**
     * return the RP data restorer to use to restore restricted public data into the cache
     */
    public PDRDatasetRestorer createRPDatasetRestorer()
        throws ConfigurationException, IOException, CacheManagementException
    {
        // return HybridDatasetRestorer(pubstore, getRPBagStorage(), getHeadBagCacheManager());
        return new PDRDatasetRestorer(getRPBagStorage(), getHeadBagCacheManager());
    }

    /**
     * return the cache manager to use to place requested restricted public data into the cache
     */
    public CacheManager createRPACacheManager(BasicCache cache)
        throws ConfigurationException, IOException, CacheManagementException
    {
        return new SimpleCacheManager(cache, createRPDatasetRestorer());
    }
}
