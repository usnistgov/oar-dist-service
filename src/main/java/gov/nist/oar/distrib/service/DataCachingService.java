package gov.nist.oar.distrib.service;

import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;

import java.util.Set;

/**
 * Service interface for caching all the data that is part of a given version of a dataset.
 */
public interface DataCachingService {

    /**
     * cache all data that is part of the given version of a dataset.
     * @param datasetID    the identifier for the dataset.
     * @param version  the version of the dataset to cache.  If null, the latest is cached.
     *
     * @return Set<String> -- a list of the URLs for files that were cached
     */
    String cacheDataset(String datasetID, String version) throws CacheManagementException, ResourceNotFoundException, StorageVolumeException;
}