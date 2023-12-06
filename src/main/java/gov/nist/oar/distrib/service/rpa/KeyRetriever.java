package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.web.RPAConfiguration;

import java.security.Key;


/**
 * Interface to retrieve the private key.
 */
public interface KeyRetriever {

    /**
     * Get a private key.
     *
     * @param rpaConfiguration - the RPA configuration
     *
     * @return Key - the private key
     */
    Key getKey(RPAConfiguration rpaConfiguration);
}