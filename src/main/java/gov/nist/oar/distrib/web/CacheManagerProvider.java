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
import gov.nist.oar.distrib.service.FileDownloadService;
import gov.nist.oar.distrib.service.CacheEnabledFileDownloadService;
import gov.nist.oar.distrib.service.NerdmDrivenFromBagFileDownloadService;
import gov.nist.oar.distrib.service.PreservationBagService;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.BasicCache;
import gov.nist.oar.distrib.cachemgr.pdr.PDRDatasetRestorer;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheManager;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagCacheManager;

import java.io.IOException;
import javax.activation.MimetypesFileTypeMap;

import org.springframework.lang.Nullable;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.amazonaws.services.s3.AmazonS3;

/**
 * A factory for creating the PDR's CacheManager that can work with the Spring framework's configuration 
 * system.
 * <p>
 * The {@link NISTDistribServiceConfig} class will set up a CacheManager if sufficient configuration 
 * parameters are set (i.e. <code>admindir</code> is set).  This class provides the gateway for testing 
 * if the necessary configuration is present.  
 */
public class CacheManagerProvider {

    NISTCacheManagerConfig cfg = null;
    private BagStorage bagstore = null;
    private HeadBagCacheManager hbcmgr = null;
    private PDRCacheManager cmgr = null;
    private AmazonS3 s3client = null;

    /**
     * create the factory.
     * @param config      the cache configuration data 
     * @param bagstorage  the long-term bag storage
     */
    public CacheManagerProvider(NISTCacheManagerConfig config, BagStorage bagstorage, AmazonS3 s3c) {
        cfg = config;
        bagstore = bagstorage;
        s3client = s3c;
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
    protected boolean canCreateManager() {
        if (cfg == null || cfg.getAdmindir() == null || cfg.getAdmindir().equals("@null"))
            return false;
        return true;
    }

    /**
     * return true if this provider can provide an instance of a CacheManager.  If one has not been 
     * created yet but can be from the configuration (i.e. {@link #canCreateManager()} returns true),
     * then this method will still return true.  
     */
    public boolean canProvideManager() {
        if (cmgr != null)
            return true;
        return canCreateManager();
    }
    
    /**
     * return the instance of the HeadBagCacheManager that was created based on the configuration for the 
     * application.  If one has not been created yet, it will be and cached in within this class. 
     * @throws ConfigurationException   if there is a problem with the configuration that prevents 
     *                                     creating the CacheManager instance.  
     */
    public HeadBagCacheManager getHeadBagManager() throws ConfigurationException {
        if (hbcmgr == null && canCreateManager())
            hbcmgr = createHeadBagManager();
        return hbcmgr;
    }
    
    /**
     * return the instance of the HeadBagCacheManager that was created based on the configuration for the 
     * application.  If one has not been created yet, it will be and cached in within this class. 
     * @throws ConfigurationException   if there is a problem with the configuration that prevents 
     *                                     creating the CacheManager instance.  
     */
    public HeadBagCacheManager createHeadBagManager() throws ConfigurationException {
        if (! canCreateManager())
            throw new ConfigurationException("Cache management not configured");
        
        try {
            return cfg.createHeadBagManager(bagstore);
        }
        catch (ConfigurationException ex) {
            throw ex;
        }
        catch (IOException ex) {
            throw new ConfigurationException("Failed to configure HeadBagCacheManager due to io error: " +
                                             ex.getMessage(), ex);
        }
        catch (CacheManagementException ex) {
            throw new ConfigurationException("Failed to configure CacheManager due to set-up error: " +
                                             ex.getMessage(), ex);
        }
    }

    /**
     * return the instance of the CacheManager that was created based on the configuration for the 
     * application.  If one has not been created yet, it will be and cached in within this class. 
     * @throws ConfigurationException   if there is a problem with the configuration that prevents 
     *                                     creating the CacheManager instance.  
     */
    public PDRCacheManager getPDRCacheManager() throws ConfigurationException {
        if (cmgr == null && canCreateManager())
            cmgr = createPDRCacheManager(getHeadBagManager());
        return cmgr;
    }

    /**
     * instantiate a CacheManager based on the configuration.  Called by {@link #getPDRCacheManager()},
     * this method will always create a new instance; thus, a Spring boot configuration should call 
     * {@link #getPDRCacheManager()} instead so as to use a single instance across the whole application.
     * @param headbagcmgr    the HeadBagCacheManager to use internal to the data CacheManager; if null,
     *                       a new one will be created.  
     * @throws ConfigurationException   if there is a problem with the configuration that prevents 
     *                                     creating the CacheManager instance.  
     */
    protected PDRCacheManager createPDRCacheManager(HeadBagCacheManager headbagcmgr)
        throws ConfigurationException
    {
        if (! canCreateManager())
            throw new ConfigurationException("Configuration is not set for running a CacheManager "
                                             +"(Missing 'admindir')");
        headbagcmgr = getHeadBagManager();

        try {
            BasicCache cache = cfg.createDefaultCache(s3client);
            PDRDatasetRestorer restorer = 
                cfg.createDefaultRestorer(bagstore, headbagcmgr);

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

    /**
     * Create a FileDownloadService reflecting the cache manager configuration.  If the configuration
     * can support a cache manager, a service with a cache manager built-in will be returned.  
     * @param bagService   the PreservationBagService instance the output service should use to access 
     *                         preservation bags in long-term storage.
     * @param mimemap      the MimetypesFileTypeMap that can provide default content-type values based 
     *                         on a file's filename extension.  
     */
    public FileDownloadService getFileDownloadService(PreservationBagService bagService,
                                                      MimetypesFileTypeMap mimemap)
        throws ConfigurationException
    {
        if (canProvideManager()) 
            return new CacheEnabledFileDownloadService(bagService, getPDRCacheManager(),
                                                       getHeadBagManager(), mimemap);

        return new NerdmDrivenFromBagFileDownloadService(bagService, mimemap);
    }
}
