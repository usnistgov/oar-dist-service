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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
     * @return String -- a url pointing to the data cart that will temporarily hold the restricted public data.
     */
    // todo@omar: test me
    public String cacheDataset(String datasetID, String version)
            throws CacheManagementException, ResourceNotFoundException, StorageVolumeException {

        // @todo: get this baseUrl from configuration file
        String baseDataCartURL = "https://data.nist.gov/pdr/datacart/restricted_datacart";
        String randomID = generateRandomID(20, true, true);

        int prefs = ROLE_RESTRICTED_DATA;
        if (!version.isEmpty())
            prefs = ROLE_OLD_RESTRICTED_DATA;

        // cache dataset
        this.pdrCacheManager.cacheDataset(datasetID, version, true, prefs, randomID);

        return baseDataCartURL + "/" + randomID;
    }

    /**
     * retrieve metadata about all the files in the dataset.
     * @param randomID  the identifier for the dataset. This will be the randomID that was generated during the caching
     *                   phase using {@link #cacheDataset(String, String)}.
     *
     * @return Map<String, Object> -- a key/value mapping representing the metadata in json format.
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
        String baseDownloadURL = "http://localhost:8083/od/ds/restricted/";

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