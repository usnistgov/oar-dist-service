package gov.nist.oar.distrib.service;

import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheManager;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheRoles;
import org.apache.commons.lang3.RandomStringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A DataCachingService implementation that leverages the {@link PDRCacheManager} to cache a dataset.
 * <p>
 * todo@omar: add story/description of the service
 * <p>
 * This implementation uses the {@link PDRCacheManager} to cache a dataset of Restricted Public Data.
 */
public class RPACachingService implements DataCachingService, PDRCacheRoles {

    private PDRCacheManager pdrCacheManager = null;

    protected static Logger logger = LoggerFactory.getLogger(RPACachingService.class);

    HashMap<String, String> url2dsid;

    /**
     * Create an instance of the service that wraps a {@link PDRCacheManager}
     *
     * @param pdrCacheManager the PDRCacheManager to use to store the restricted public data.
     **/
    public RPACachingService(PDRCacheManager pdrCacheManager) {
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

        String baseDataCartURL = "https://data.nist.gov/pdr/datacart/restricted_datacart";
        String randomID = generateRandomID(20, true, true);

        int prefs = ROLE_RESTRICTED_DATA;
        if (!version.isEmpty())
            prefs = ROLE_OLD_RESTRICTED_DATA;

        // cache dataset
        this.pdrCacheManager.cacheDataset(datasetID, version, true, prefs, randomID);
        return baseDataCartURL + "?id=" + randomID;
    }

    /**
     * Cache all data that is part of the given version of a dataset, and generate a temporary random id.
     *
     * @param datasetID the identifier for the dataset.
     * @param version   the version of the dataset to cache.  If null, the latest is cached.
     * @return String -- the temporary random that will be used to fetch metadata.
     */
    public String cacheAndGenerateRandomId(String datasetID, String version)
            throws CacheManagementException, ResourceNotFoundException, StorageVolumeException {

        String randomID = generateRandomID(20, true, true);

        int prefs = ROLE_RESTRICTED_DATA;
        if (!version.isEmpty())
            prefs = ROLE_OLD_RESTRICTED_DATA;

        // cache dataset
        this.pdrCacheManager.cacheDataset(datasetID, version, true, prefs, randomID);
        return randomID;
    }


    /**
     * Retrieve metadata given the random id that was previously generated and used to cache the dataset.
     *
     * @param randomID the random ID used to cache the dataset.
     *
     * @return Map<String, Object> -- metadata about files in dataset
     */
    public Map<String, Object> retrieveMetadata(String randomID) throws CacheManagementException {
        JSONArray metadata = new JSONArray();
        List<CacheObject> objects = this.pdrCacheManager.selectDatasetObjects(randomID, this.pdrCacheManager.VOL_FOR_INFO);
        if (objects.size() > 0) {
            for (CacheObject obj: objects) {
                JSONObject objMd = formatMetadata(obj.exportMetadata(), randomID);
                metadata.put(objMd);
            }
        }
        JSONObject result = new JSONObject();
        result.put("randomId", randomID);
        result.put("metadata", metadata);
        return result.toMap();
    }

    private JSONObject formatMetadata(JSONObject inMd, String randomID) {
        String baseDownloadURL = "http://localhost:8083/od/ds/";

        JSONObject outMd = new JSONObject();
        if (inMd.has("filepath")) {
            String downloadURL = baseDownloadURL + randomID + "/" + inMd.get("filepath");
            outMd.put("downloadURL", downloadURL);
            outMd.put("filePath", inMd.get("filepath"));
        }
        if (inMd.has("contentType")) {
            outMd.put("mediaType", inMd.get("contentType"));
        }
        if (inMd.has("size")) {
            outMd.put("size", inMd.get("size"));
        }
        if (inMd.has("resTitle")) {
            outMd.put("resTitle", inMd.get("resTitle"));
        }
        if (inMd.has("pdrid")) {
            outMd.put("resId", inMd.get("pdrid"));
        }
        if (inMd.has("checksumAlgorithm")) {
            outMd.put("checksumAlgorithm", inMd.get("checksumAlgorithm"));
        }
        if (inMd.has("checksum")) {
            outMd.put("checksum", inMd.get("checksum"));
        }
        if (inMd.has("version")) {
            outMd.put("version", inMd.get("version"));
        }
        if (inMd.has("ediid")) {
            outMd.put("ediid", inMd.get("ediid"));
        }
        if (inMd.has("aipid")) {
            outMd.put("aipid", inMd.get("aipid"));
        }
        if (inMd.has("sinceDate")) {
            outMd.put("sinceDate", inMd.get("sinceDate"));
        }
        return outMd;
    }

    /**
     * Generate a random alphanumeric string for the dataset to store
     * This function uses the {@link RandomStringUtils} from Apache Commons.
     **/
    private String generateRandomID(int length, boolean useLetters, boolean useNumbers) {
        return RandomStringUtils.random(length, useLetters, useNumbers);
    }
}