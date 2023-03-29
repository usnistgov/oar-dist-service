package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.exceptions.MetadataNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecordNotFoundException;
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

import java.util.Map;

@RestController
@Tag(name = "Cache RPA Datasets", description = "Cache Restricted Public Access data in a dedicated cache space and generate temporary URL for the cached data.")
@RequestMapping(value = "/ds/rpa")
public class RPADataCachingController {

    Logger logger = LoggerFactory.getLogger(RPADataCachingController.class);

    @Autowired
    RPACachingService restrictedSrvc;

    /**
     * Cache a dataset in a temporary repository.
     *
     * @param dsid - the dataset id.
     * @param version - version of the dataset.
     *
     * @return String - a random ID representing the temporary cache object of the dataset.
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
     * Retrieve metadata of a cached dataset.
     *
     * @param randomId - the random ID representing the cached dataset.
     *
     * @return Map<String, Object>  - a map representing the metadata of the cached dataset files.
     */
    @GetMapping(value = "/dlset/{cacheid}")
    public Map<String, Object> retrieveMetadata(@PathVariable("cacheid") String cacheId)
            throws CacheManagementException, MetadataNotFoundException {

        logger.debug("cacheId=" + cacheId);
        Map<String, Object> metadata = restrictedSrvc.retrieveMetadata(cacheId);
        return metadata;
    }

    @ExceptionHandler(MetadataNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorInfo handleMetadataNotFoundException(MetadataNotFoundException ex) {
        return new ErrorInfo(404, "metadata not found: " + ex.getMessage());
    }
}