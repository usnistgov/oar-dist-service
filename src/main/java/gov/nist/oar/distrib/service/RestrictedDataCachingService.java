package gov.nist.oar.distrib.service;

import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheManager;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheRoles;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * A DataCachingService implementation that leverages the {@link PDRCacheManager} to cache a dataset.
 * <p>
 * todo@omar: add story/description of the service
 * <p>
 * This implementation uses the {@link PDRCacheManager} to cache a dataset of Restricted Public Data.
 */
public class RestrictedDataCachingService implements DataCachingService, PDRCacheRoles {

    private PDRCacheManager pdrCacheManager = null;

    protected static Logger logger = LoggerFactory.getLogger(RestrictedDataCachingService.class);


    /**
     * Create an instance of the service that wraps a {@link PDRCacheManager}
     *
     * @param pdrCacheManager the PDRCacheManager to use to store the restricted public data.
     **/
    public RestrictedDataCachingService(PDRCacheManager pdrCacheManager) {
        this.pdrCacheManager = pdrCacheManager;
    }

    /**
     * Cache all data that is part of the given version of a dataset.
     *
     * @param datasetID the identifier for the dataset.
     * @param version   the version of the dataset to cache.  If null, the latest is cached.
     * @return Set<String> -- a list of the URLs for files that were cached
     */
    // todo@omar: test me
    public Set<String> cacheDataset(String datasetID, String version)
            throws CacheManagementException, ResourceNotFoundException, StorageVolumeException {
        String randomID = generateRandomID(20, true, true);

        int prefs = ROLE_RESTRICTED_DATA;
        if (version != null)
            prefs = ROLE_OLD_RESTRICTED_DATA;

        return this.pdrCacheManager.cacheDataset(datasetID, version, true, prefs, randomID);
    }

    /**
     * Generate a random alphanumeric string for the dataset to store
     * This function uses the {@link RandomStringUtils} from Apache Commons.
     **/
    private String generateRandomID(int length, boolean useLetters, boolean useNumbers) {
        return RandomStringUtils.random(length, useLetters, useNumbers);
    }
}
