package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;

/**
 * Interface RPADatasetCacher for caching datasets
 */
public interface RPADatasetCacher {
    /**
     * Method to cache a dataset using a given dataset ID.
     *
     * @param datasetId the ID of the dataset to cache
     * @return the randomId generated after successfully caching the dataset
     * @throws RequestProcessingException
     */
    String cache(String datasetId) throws RequestProcessingException;

    boolean uncache(String randomId);
}
