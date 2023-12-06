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
     * Cache all data that is part of the given version of a dataset, and generate a temporary random id.
     *
     * @param datasetID the identifier for the dataset.
     * @param version   the version of the dataset to cache.  If null, the latest is cached.
     * @return String -- the temporary random that will be used to fetch metadata.
     */
    String cacheAndGenerateRandomId(String datasetID, String version)
            throws CacheManagementException, ResourceNotFoundException, StorageVolumeException,
            IllegalArgumentException;
}