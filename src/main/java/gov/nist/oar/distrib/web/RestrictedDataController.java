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
@Tag(name = "", description = "") // todo@omar: add docs
@RequestMapping(value = "/ds/restricted")
public class RestrictedDataController {


    @Value("${distrib.baseurl}")
    String baseURL;

    Logger logger = LoggerFactory.getLogger(RestrictedDataController.class);

    @Autowired
    RestrictedDataCachingService restrictedSrvc;


    @PutMapping(value = "/{dsid}")
    public String cacheDataset(
            @PathVariable("dsid") String dsid,
            @RequestParam(name = "version", defaultValue = "") String version)
            throws CacheManagementException, ResourceNotFoundException, StorageVolumeException {

        logger.info("dsid=" + dsid);
        String temporaryURL = restrictedSrvc.cacheDataset(dsid, version);
        return temporaryURL;
    }

    @GetMapping(value = "/{randomId}")
    public Map<String, Object> retrieveMetadata(@PathVariable("randomId") String randomId)
            throws CacheManagementException {

        logger.info("randomId=" + randomId);
        Map<String, Object> metadata = restrictedSrvc.retrieveMetadata(randomId);
        return metadata;
    }
}