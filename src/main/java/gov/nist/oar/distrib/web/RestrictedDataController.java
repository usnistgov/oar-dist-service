package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.service.RestrictedDataCachingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Set;

@RestController
@Tag(name = "", description = "") // todo@omar: add docs
@RequestMapping(value = "/ds/restricted")
public class RestrictedDataController {

    Logger logger = LoggerFactory.getLogger(RestrictedDataController.class);

    RestrictedDataCachingService restrictedSrvc;

    @PutMapping(value = "/{dsid}")
    public Set<String> getURLs(
            @PathVariable("dsid") String dsid,
            @RequestParam(name = "version", defaultValue = "") String version)
            throws CacheManagementException, ResourceNotFoundException, StorageVolumeException {

        Set<String> urls = restrictedSrvc.cacheDataset(dsid, version);
        logUrls(dsid, urls);
        return restrictedSrvc.cacheDataset(dsid, version);
    }

    @GetMapping(value = "/{dsid}")
    @ResponseBody
    public String echo(
            @PathVariable("dsid") String dsid,
            @RequestParam(name = "version", defaultValue = "") String version)
            throws CacheManagementException, ResourceNotFoundException, StorageVolumeException {

        return "ID is " + dsid + " and version is " + version;
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
