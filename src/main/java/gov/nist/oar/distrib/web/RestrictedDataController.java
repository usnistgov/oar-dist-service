package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.service.RestrictedDataCachingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@Tag(name = "", description = "") // todo@omar: add docs
@RequestMapping(value = "/ds/restricted")
public class RestrictedDataController {

    @Value("${distrib.baseurl}")
    String baseURL;

    Logger logger = LoggerFactory.getLogger(RestrictedDataController.class);

    @Autowired
    RestrictedDataCachingService restrictedSrvc;

//    @PutMapping(value = "/{dsid}")
//    public Set<String> cacheDataset(
//            @PathVariable("dsid") String dsid,
//            @RequestParam(name = "version", defaultValue = "") String version)
//            throws CacheManagementException, ResourceNotFoundException, StorageVolumeException {
//
//        logger.info("version=" + version);
//        Set<String> urls = restrictedSrvc.cacheDataset(dsid, version);
//        logUrls(dsid, urls);
//        return urls;
//    }

    @PutMapping(value = "/{dsid}")
    public String cacheDataset(
            @PathVariable("dsid") String dsid,
            @RequestParam(name = "version", defaultValue = "") String version)
            throws CacheManagementException, ResourceNotFoundException, StorageVolumeException {

        logger.info("dsid=" + dsid);
        String temporaryURL = restrictedSrvc.cacheAndGenerateTemporaryUrl(dsid, version);
        return temporaryURL;
    }

    @GetMapping(value = "/{randomId}")
    public Map<String, Object> retrieveMetadata(@PathVariable("randomId") String randomId)
            throws CacheManagementException {

        logger.info("randomId=" + randomId);
        Map<String, Object> metadata = restrictedSrvc.retrieveMetadata(randomId);
        return metadata;
    }

    // utility function to log urls
    private void logUrls(String dsid, Set<String> urls) {
        StringBuilder sb = new StringBuilder();
        sb.append("List of temporary URLs for dataset_id=").append(dsid + "\n");
        urls.forEach(url -> {
            sb.append(url + "\n");
        });
        logger.info(sb.toString());
    }
}