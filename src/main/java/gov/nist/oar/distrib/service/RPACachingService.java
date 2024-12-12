package gov.nist.oar.distrib.service;

import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.pdr.PDRDatasetCacheManager;
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
    private PDRDatasetCacheManager pdrCacheManager;
    private RPAConfiguration rpaConfiguration;

    protected static Logger logger = LoggerFactory.getLogger(RPACachingService.class);


    /**
     * Create an instance of the service that wraps a {@link PDRCacheManager}
     *
     * @param pdrCacheManager  the PDRCacheManager to use to store the restricted public data.
     * @param rpaConfiguration the RPAConfiguration object to use with this service.
     **/
    public RPACachingService(PDRDatasetCacheManager pdrCacheManager, RPAConfiguration rpaConfiguration) {
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

        // this is to handle ark IDs
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
        // append "rpa-" with the generated random ID
        String randomID = "rpa-" + generateRandomID(RANDOM_ID_LENGTH, true, true);


        int prefs = ROLE_RESTRICTED_DATA;
        if (version != null && !version.isEmpty())
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
                this.pdrCacheManager.VOL_FOR_GET);
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
     * The download URL includes the random temporary ID, aipid, and the file path from the metadata.
     *
     * @param inMd     the metadata from the cache object
     * @param randomID the random temporary ID associated with the cache object
     * @return a JSON object containing the formatted metadata
     * @throws RequestProcessingException if there was an error formatting the metadata
     */
    private JSONObject formatMetadata(JSONObject inMd, String randomID) throws RequestProcessingException {
        JSONObject outMd = new JSONObject();
        List<String> missingFields = new ArrayList<>();

        String aipid = "";
        if (inMd.has("aipid")) {
            aipid = inMd.getString("aipid");
            outMd.put("aipid", aipid);
        } else {
            missingFields.add("aipid");
        }

        if (inMd.has("filepath")) {
            String downloadURL = getDownloadUrl(
                    rpaConfiguration.getBaseDownloadUrl(),
                    randomID,
                    aipid,
                    inMd.getString("filepath"));
            outMd.put("downloadURL", downloadURL);
            outMd.put("filePath", inMd.getString("filepath"));
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
     * Constructs a download URL using the given base download URL, random ID, aipid, and file path from the metadata.
     *
     * @param baseDownloadUrl the base download URL
     * @param randomId        the random temporary ID
     * @param aipid           the aipid from the metadata
     * @param path            the file path from the metadata
     * @return the download URL as a string
     * @throws RequestProcessingException if there was an error building the download URL
     */

    private String getDownloadUrl(String baseDownloadUrl, String randomId, String aipid, String path) throws RequestProcessingException {
        URL downloadUrl;
        try {
            URL url = new URL(baseDownloadUrl);
            StringBuilder pathBuilder = new StringBuilder();

            // append the randomId to the path
            pathBuilder.append(randomId);

            // append the aipid if it's not empty
            if (!aipid.isEmpty()) {
                pathBuilder.append("/").append(aipid);
            }

            // append the file path, ensuring it doesn't start with a "/"
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            pathBuilder.append("/").append(path);

            downloadUrl = new URL(url, pathBuilder.toString());
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

    /**
     * Uncache dataset objects using a specified random ID.
     *
     * @param randomId - The random ID used to fetch and uncache dataset objects.
     * @return boolean - True if at least one dataset object was uncached successfully; otherwise, false.
     * @throws CacheManagementException if an error occurs during the uncaching process.
     */
    public boolean uncacheById(String randomId) throws CacheManagementException {
        // Validate input
        if (randomId == null || randomId.isEmpty()) {
            throw new IllegalArgumentException("Random ID cannot be null or empty.");
        }

        logger.debug("Request to uncache dataset with ID=" + randomId);

        // Retrieve dataset objects using the randomId
        List<CacheObject> objects = this.pdrCacheManager.selectDatasetObjects(randomId, this.pdrCacheManager.VOL_FOR_INFO);

        if (objects.isEmpty()) {
            logger.debug("No objects found for ID=" + randomId);
            return false;
        }

        boolean isUncached = false;

        // Iterate through the retrieved objects and attempt to uncache them
        for (CacheObject obj : objects) {
            try {
                logger.debug("Deleting file with ID=" + obj.id);
                this.pdrCacheManager.uncache(obj.id);
                isUncached = true;
            } catch (CacheManagementException e) {
                // Log the exception without throwing it to continue attempting to uncache remaining objects
                logger.error("Failed to uncache object with ID=" + obj.id, e);
            }
        }

        return isUncached;
    }

}
