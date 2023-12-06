package gov.nist.oar.distrib.service;

import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheManager;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheRoles;
import gov.nist.oar.distrib.service.rpa.exceptions.MetadataNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.apache.commons.lang3.RandomStringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
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
public class RPACachingService implements DataCachingService, PDRCacheRoles {

    public static final int RANDOM_ID_LENGTH = 20;
    private PDRCacheManager pdrCacheManager;
    private RPAConfiguration rpaConfiguration;

    protected static Logger logger = LoggerFactory.getLogger(RPACachingService.class);


    /**
     * Create an instance of the service that wraps a {@link PDRCacheManager}
     *
     * @param pdrCacheManager  the PDRCacheManager to use to store the restricted public data.
     * @param rpaConfiguration the RPAConfiguration object to use with this service.
     **/
    public RPACachingService(PDRCacheManager pdrCacheManager, RPAConfiguration rpaConfiguration) {
        this.pdrCacheManager = pdrCacheManager;
        this.rpaConfiguration = rpaConfiguration;
    }

    /**
     * Cache all data that is part of the given version of a dataset, and generate a temporary random id.
     *
     * @param datasetID the identifier for the dataset.
     * @param version   the version of the dataset to cache.  If null, the latest is cached.
     * @return String -- the temporary random that will be used to fetch metadata.
     */
    public String cacheAndGenerateRandomId(String datasetID, String version)
            throws CacheManagementException, ResourceNotFoundException, StorageVolumeException,
            IllegalArgumentException {

        logger.debug("Request to cache dataset with ID=" + datasetID);

        String dsid = datasetID;
        if (datasetID.startsWith("ark:/")) {
            // Split the dataset ID into components
            String[] parts = datasetID.split("/");
            if (parts.length < 3) { // should contain 3 parts
                throw new IllegalArgumentException("Invalid ark ID format: " + datasetID);
            }
            dsid = parts[2];
        }

        logger.debug("Caching dataset with dsid=" + dsid);
        String randomID = generateRandomID(RANDOM_ID_LENGTH, true, true);

        int prefs = ROLE_RESTRICTED_DATA;
        if (!version.isEmpty())
            prefs = ROLE_OLD_RESTRICTED_DATA;

        // cache dataset
        Set<String> files = this.pdrCacheManager.cacheDataset(dsid, version, true, prefs, randomID);
        // Log the files
        logger.debug("Cached files:");
        for (String file : files) {
            logger.debug("- " + file);
        }
        return randomID;
    }


    /**
     * Retrieve metadata given the random id that was previously generated and used to cache the dataset.
     *
     * @param randomID the random ID used to cache the dataset.
     * @return Map<String, Object> -- metadata about files in dataset
     */
    public Map<String, Object> retrieveMetadata(String randomID) throws CacheManagementException,
            MetadataNotFoundException, RequestProcessingException {
        logger.debug("Requesting metadata for temporary ID=" + randomID);
        JSONArray metadata = new JSONArray();
        List<CacheObject> objects = this.pdrCacheManager.selectDatasetObjects(randomID,
                this.pdrCacheManager.VOL_FOR_INFO);
        if (objects.size() > 0) {
            for (CacheObject obj : objects) {
                JSONObject objMd = formatMetadata(obj.exportMetadata(), randomID);
                if (objMd.has("filePath")) {
                    metadata.put(objMd);
                } else {
                    logger.warn("Skipping object with missing file path in metadata: " + objMd);
                }
            }
        }
        if (metadata.isEmpty()) {
            throw new MetadataNotFoundException("metadata list is empty");
        }
        JSONObject result = new JSONObject();
        result.put("randomId", randomID);
        result.put("metadata", metadata);
        // Log the JSON object
        logger.debug("Result:");
        logger.debug(result.toString(4));
        return result.toMap();
    }

    /**
     * Formats the metadata from a cache object to a JSON object with an additional field for the download URL.
     *
     * @param inMd     the metadata from the cache object
     * @param randomID the random temporary ID associated with the cache object
     * @return a JSON object containing the formatted metadata
     * @throws RequestProcessingException if there was an error formatting the metadata
     */
    private JSONObject formatMetadata(JSONObject inMd, String randomID) throws RequestProcessingException {
        JSONObject outMd = new JSONObject();
        List<String> missingFields = new ArrayList<>();

        if (inMd.has("filepath")) {
            String downloadURL = getDownloadUrl(
                    rpaConfiguration.getBaseDownloadUrl(),
                    randomID,
                    inMd.get("filepath").toString());
            outMd.put("downloadURL", downloadURL);
            outMd.put("filePath", inMd.get("filepath"));
        } else {
            missingFields.add("filepath");
        }

        if (inMd.has("contentType")) {
            outMd.put("mediaType", inMd.get("contentType"));
        } else {
            missingFields.add("contentType");
        }

        if (inMd.has("size")) {
            outMd.put("size", inMd.get("size"));
        } else {
            missingFields.add("size");
        }

        // change to resTitle instead of title
        if (inMd.has("resTitle")) {
            outMd.put("resTitle", inMd.get("resTitle"));
        } else {
            missingFields.add("title");
        }

        if (inMd.has("pdrid")) {
            outMd.put("resId", inMd.get("pdrid"));
        } else {
            missingFields.add("pdrid");
        }

        if (inMd.has("checksumAlgorithm")) {
            outMd.put("checksumAlgorithm", inMd.get("checksumAlgorithm"));
        } else {
            missingFields.add("checksumAlgorithm");
        }

        if (inMd.has("checksum")) {
            outMd.put("checksum", inMd.get("checksum"));
        } else {
            missingFields.add("checksum");
        }

        if (inMd.has("version")) {
            outMd.put("version", inMd.get("version"));
        } else {
            missingFields.add("version");
        }

        if (inMd.has("ediid")) {
            outMd.put("ediid", inMd.get("ediid"));
        } else {
            missingFields.add("ediid");
        }

        if (inMd.has("aipid")) {
            outMd.put("aipid", inMd.get("aipid"));
        } else {
            missingFields.add("aipid");
        }

        if (inMd.has("sinceDate")) {
            outMd.put("sinceDate", inMd.get("sinceDate"));
        } else {
            missingFields.add("sinceDate");
        }

        if (!missingFields.isEmpty()) {
            logger.debug("Metadata missing fields: " + String.join(", ", missingFields));
        }

        return outMd;
    }


    /**
     * Constructs a download URL using the given base download URL, random ID, and file path from the metadata.
     *
     * @param baseDownloadUrl the base download URL
     * @param randomId        the random temporary ID
     * @param path            the file path from the metadata
     * @return the download URL as a string
     * @throws RequestProcessingException if there was an error building the download URL
     */
    private String getDownloadUrl(String baseDownloadUrl, String randomId, String path) throws RequestProcessingException {
        URL downloadUrl;
        try {
            URL url = new URL(baseDownloadUrl);
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            downloadUrl = new URL(url, randomId + "/" + path);
        } catch (MalformedURLException e) {
            throw new RequestProcessingException("Failed to build downloadUrl: " + e.getMessage());
        }
        return downloadUrl.toString();
    }

    /**
     * Generate a random alphanumeric string for the dataset to store
     * This function uses the {@link RandomStringUtils} from Apache Commons.
     **/
    private String generateRandomID(int length, boolean useLetters, boolean useNumbers) {
        return RandomStringUtils.random(length, useLetters, useNumbers);
    }
}