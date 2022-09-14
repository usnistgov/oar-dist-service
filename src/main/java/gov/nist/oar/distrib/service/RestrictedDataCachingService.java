package gov.nist.oar.distrib.service;

import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheManager;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheRoles;
import org.apache.commons.lang3.RandomStringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    HashMap<String, String> url2dsid;

    /**
     * Create an instance of the service that wraps a {@link PDRCacheManager}
     *
     * @param pdrCacheManager the PDRCacheManager to use to store the restricted public data.
     **/
    public RestrictedDataCachingService(PDRCacheManager pdrCacheManager) {
        this.pdrCacheManager = pdrCacheManager;
        this.url2dsid = new HashMap<>();
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
        if (!version.isEmpty())
            prefs = ROLE_OLD_RESTRICTED_DATA;

        Set<String> temporaryUrls = new HashSet<>();
        this.pdrCacheManager.cacheDataset(datasetID, version, true, prefs, randomID).forEach(name ->
                {
                    temporaryUrls.add("/" + randomID + "/" + name);
                }
        );
        return temporaryUrls;
    }

    /**
     * Cache all data that is part of the given version of a dataset, and generate a temporary URL.
     *
     * @param datasetID the identifier for the dataset.
     * @param version   the version of the dataset to cache.  If null, the latest is cached.
     * @return String -- the temporary URL that will be used to fetch the files metadata.
     */
    public String cacheAndGenerateTemporaryUrl(String datasetID, String version)
            throws CacheManagementException, ResourceNotFoundException, StorageVolumeException {

        String baseURL = "https://data.nist.gov/pdr/datacart/restricted_datacart";
        String randomID = generateRandomID(20, true, true);

        int prefs = ROLE_RESTRICTED_DATA;
        if (!version.isEmpty())
            prefs = ROLE_OLD_RESTRICTED_DATA;

        // cache dataset
        this.pdrCacheManager.cacheDataset(datasetID, version, true, prefs, randomID);
        this.url2dsid.put(randomID, datasetID);
        return baseURL + "/" + randomID;
    }


    public Set<JSONObject> retrieveMetadata(String randomID) throws CacheManagementException {
        String dsid = this.url2dsid.get(randomID);
        Set<JSONObject> objects = new HashSet<>();
        this.pdrCacheManager.selectDatasetObjects(dsid, this.pdrCacheManager.VOL_FOR_GET).forEach(dsObj -> {
            objects.add(dsObj.exportMetadata());
        });
        return objects;
    }
    /**
     * Generate a random alphanumeric string for the dataset to store
     * This function uses the {@link RandomStringUtils} from Apache Commons.
     **/
    private String generateRandomID(int length, boolean useLetters, boolean useNumbers) {
        return RandomStringUtils.random(length, useLetters, useNumbers);
    }
}