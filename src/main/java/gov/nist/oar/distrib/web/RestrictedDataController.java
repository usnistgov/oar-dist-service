package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.service.RestrictedDataCachingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Tag(name = "Cache and retrieve restricted public data", description = "Allow to cache restricted public datatsets " +
        "and to retrieve metadata for the files belonging to the datatsets.")
@RequestMapping(value = "/ds/restricted")
public class RestrictedDataController {

//    @Value("${distrib.baseurl}")
//    String baseURL;

    Logger logger = LoggerFactory.getLogger(RestrictedDataController.class);

    @Autowired
    RestrictedDataCachingService restrictedSrvc;

    /**
     * The controller api endpoint to cache the files in the dataset specified using the give ID,
     * and return a temporary url that points to the data cart that will display metadata about the cached files.
     *
     * @param dsid the id of the dataset
     * @param version the version of the dataset
     *
     * @return String the temporary url
     * @throws CacheManagementException
     * @throws ResourceNotFoundException
     * @throws StorageVolumeException
     */
    @PutMapping(value = "/{dsid}")
    public String cacheDatasetAndGenerateUrl(
            @PathVariable("dsid") String dsid,
            @RequestParam(name = "version", defaultValue = "") String version)
            throws CacheManagementException, ResourceNotFoundException, StorageVolumeException {

        logger.info("dsid=" + dsid);
        String temporaryURL = restrictedSrvc.cacheDataset(dsid, version);
        return temporaryURL;
    }

    /**
     * The controller api endpoint to retrieve metadata about the cached files.
     *
     * @param randomId the temporary id of the dataset that was generated using {@link #cacheDataset(String, String)}
     *
     * @return a map representation of the json object containing the files metadata
     * @throws CacheManagementException
     */
    @GetMapping(value = "/{randomId}")
    public Map<String, Object> retrieveMetadata(@PathVariable("randomId") String randomId)
            throws CacheManagementException {

        logger.info("randomId=" + randomId);
        Map<String, Object> metadata = restrictedSrvc.retrieveMetadata(randomId);
        return metadata;
    }
}