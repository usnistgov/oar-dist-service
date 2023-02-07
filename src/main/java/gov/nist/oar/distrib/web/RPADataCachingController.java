package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.service.RPACachingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
@Tag(name = "Cache RPA Datasets", description = "Cache Restricted Public Access data in a dedicated cache space and generate temporary URL for the cached data.")
@RequestMapping(value = "/ds/restricted")
public class RPADataCachingController {

    @Value("${distrib.baseurl}")
    String baseURL;

    Logger logger = LoggerFactory.getLogger(RPADataCachingController.class);

    @Autowired
    RPACachingService restrictedSrvc;

    @PutMapping(value = "/{dsid}")
    public String cacheDataset(
            @PathVariable("dsid") String dsid,
            @RequestParam(name = "version", defaultValue = "") String version)
            throws CacheManagementException, ResourceNotFoundException, StorageVolumeException {

        logger.info("dsid=" + dsid);
        String randomId = restrictedSrvc.cacheAndGenerateRandomId(dsid, version);
        return randomId;
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