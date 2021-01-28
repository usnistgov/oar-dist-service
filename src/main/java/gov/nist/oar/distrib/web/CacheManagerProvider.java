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
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.BasicCache;
import gov.nist.oar.distrib.cachemgr.pdr.PDRDatasetRestorer;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheManager;

import java.io.IOException;

import org.springframework.lang.Nullable;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * A factory for creating the PDR's CacheManager that can work with the Spring framework's configuration 
 * system.
 * <p>
 * The {@link NISTDistribServiceConfig} class will set up a CacheManager if sufficient configuration 
 * parameters are set (i.e. <code>admindir</code> is set).  This class provides the gateway for testing 
 * if the necessary configuration is present.  
 */
public class CacheManagerProvider {

    private NISTCacheManagerConfig cfg = null;
    private BagStorage bagstore = null;
    private PDRCacheManager cmgr = null;

    /**
     * create the factory.
     * @param config      the cache configuration data 
     * @param bagstorage  the long-term bag storage
     */
    public CacheManagerProvider(NISTCacheManagerConfig config, BagStorage bagstorage) {
        cfg = config;
        bagstore = bagstorage;
        if (canCreateManager())
            _getLogger().info("A CacheManager will be created for this application.");
        else
            _getLogger().debug("Insufficient configuration to run a CacheManager; none will used.");
    }

    private Logger _getLogger() {
        return LoggerFactory.getLogger("PDRCacheMgr");
    }

    /**
     * return true if this provider has the necessary configuration to create a CacheManager.  Note that 
     * this method does not check the validity of configuration; thus, createCacheManager() may still 
     * raise exceptions.  
     */
    public boolean canCreateManager() {
        if (cfg == null || cfg.getAdmindir() == null || cfg.getAdmindir().equals("@null"))
            return false;
        return true;
    }

    /**
     * return true if this provider can provide an instance of a CacheManager.  If one has not been 
     * created yet but can be from the configuration (i.e. {@link #canCreateManager()} returns true),
     * then this method will still return true.  
     */
    public boolean managerAvailable() {
        if (cmgr != null)
            return true;
        return canCreateManager();
    }
    
    /**
     * return the instance of the CacheManager that was created based on the configuration for the 
     * application.  If one has not been created yet, it will be and cached in within this class. 
     * @throws ConfigurationException   if there is a problem with the configuration that prevents 
     *                                     creating the CacheManager instance.  
     */
    public PDRCacheManager getPDRCacheManager() throws ConfigurationException {
        if (cmgr == null && canCreateManager())
            cmgr = createPDRCacheManager();
        return cmgr;
    }

    /**
     * instantiate a CacheManager based on the configuration.  Called by {@link #getPDRCacheManager()},
     * this method will always create a new instance; thus, a Spring boot configuration should call 
     * {@link #getPDRCacheManager()} instead so as to use a single instance across the whole application.
     * @throws ConfigurationException   if there is a problem with the configuration that prevents 
     *                                     creating the CacheManager instance.  
     */
    public PDRCacheManager createPDRCacheManager() throws ConfigurationException {
        if (! canCreateManager())
            throw new ConfigurationException("Configuration is not set for running a CacheManager "
                                             +"(Missing 'admindir')");

        try {
            BasicCache cache = cfg.createDefaultCache();
            PDRDatasetRestorer restorer = 
                cfg.createDefaultRestorer(bagstore, cfg.createHeadBagManager(bagstore));

            return cfg.createCacheManager(cache, restorer, _getLogger());
        }
        catch (ConfigurationException ex) {
            throw ex;
        }
        catch (IOException ex) {
            throw new ConfigurationException("Failed to configure CacheManager due to io error: " +
                                             ex.getMessage(), ex);
        }
        catch (CacheManagementException ex) {
            throw new ConfigurationException("Failed to configure CacheManager due to set-up error: " +
                                             ex.getMessage(), ex);
        }
    }
}
