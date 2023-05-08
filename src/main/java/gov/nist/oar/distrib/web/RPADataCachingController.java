package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.exceptions.MetadataNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * The {@link RPARequestHandlerController} class serves as the REST controller for handling requests to cache
 * restricted public access data.
 * The @CrossOrigin annotation enables Cross-Origin Resource Sharing (CORS) on this controller, allowing requests
 * from other domains.
 */
@RestController
@Tag(name = "Cache RPA Datasets", description = "Cache Restricted Public Access data in a dedicated cache space and generate temporary URL for the cached data.")
@RequestMapping(value = "/ds/rpa")
public class RPADataCachingController {

    Logger logger = LoggerFactory.getLogger(RPADataCachingController.class);

    /**
     * The regex used to match ARK id in the URL path.
     */
    private static final String ARK_REGEX = "ark:/{naan:\\d+}/{dsid}";

    @Autowired
    RPACachingService restrictedSrvc;

    /**
     * This endpoint handles caching of a dataset under restricted public access, and generates a random ID for it.
     *
     * @param dsid The ID of the dataset to cache.
     * @param version The version of the dataset to cache, if applicable. Defaults to an empty string if not specified.
     *
     * @return A randomly generated ID that can be used to access the cached dataset.
     *
     * @throws CacheManagementException If there is an issue with caching the dataset.
     * @throws ResourceNotFoundException If the requested dataset is not found.
     * @throws StorageVolumeException If there is an issue with the storage volume.
     */
    @PutMapping(value = "/cache/{dsid}")
    public String cacheDataset(
            @PathVariable("dsid") String dsid,
            @RequestParam(name = "version", defaultValue = "") String version)
            throws CacheManagementException, ResourceNotFoundException, StorageVolumeException {

        logger.debug("dsid=" + dsid);
        String randomId = restrictedSrvc.cacheAndGenerateRandomId(dsid, version);
        return randomId;
    }

    /**
     * This endpoint handles caching of a dataset under restricted public access, and generates a random ID for it.
     * This endpoint expects an ARK identifier in the path that matches the regular expression {@link #ARK_REGEX}
     *
     * @param dsid     the dataset identifier
     * @param naan     the ARK ID's naming authority number (NAAN)
     * @param version  the version of the dataset (optional, defaults to empty string)
     * @param request  the input HTTP request object
     *
     * @return the randomly generated ID for the cached dataset
     *
     * @throws CacheManagementException if there was an error caching the dataset
     * @throws ResourceNotFoundException if the dataset was not found
     * @throws StorageVolumeException if there was an error accessing the storage volume
     */
    @PutMapping(value = "/cache/" + ARK_REGEX)
    public String cacheDatasetViaARK(
            @PathVariable("dsid") String dsid,
            @PathVariable("naan") String naan,
            @RequestParam(name = "version", defaultValue = "") String version,
            @Parameter(hidden = true) HttpServletRequest request)
            throws CacheManagementException, ResourceNotFoundException, StorageVolumeException {

        logger.debug("Matched ARK ID for caching: ark:/" + naan + "/" + dsid);
        String randomId = restrictedSrvc.cacheAndGenerateRandomId(dsid, version);
        return randomId;
    }

    /**
     * Retrieve metadata of a cached dataset.
     *
     * @param randomId - the random ID representing the cached dataset.
     *
     * @return Map<String, Object>  - a map representing the metadata of the cached dataset files.
     */
    @GetMapping(value = "/dlset/{cacheid}")
    public Map<String, Object> retrieveMetadata(@PathVariable("cacheid") String cacheId)
            throws CacheManagementException, MetadataNotFoundException, RequestProcessingException {

        logger.debug("cacheId=" + cacheId);
        Map<String, Object> metadata = restrictedSrvc.retrieveMetadata(cacheId);
        return metadata;
    }

    @ExceptionHandler(MetadataNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorInfo handleMetadataNotFoundException(MetadataNotFoundException ex) {
        return new ErrorInfo(404, "metadata not found: " + ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorInfo handleResourceNotFoundException(ResourceNotFoundException ex) {
        return new ErrorInfo(404, "resource not found: " + ex.getMessage());
    }

    @ExceptionHandler(CacheManagementException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleCacheManagementException(CacheManagementException ex) {
        return new ErrorInfo(500, "cache management error: " + ex.getMessage());
    }

    @ExceptionHandler(StorageVolumeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleStorageVolumeException(StorageVolumeException ex) {
        return new ErrorInfo(500, "storage volume error: " + ex.getMessage());
    }

    @ExceptionHandler(RequestProcessingException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleRequestProcessingException(RequestProcessingException ex) {
        return new ErrorInfo(500, "internal service error: " + ex.getMessage());
    }
}