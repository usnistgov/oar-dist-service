package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.web.RPAConfiguration;

import java.security.Key;

public interface KeyRetriever {

    Key getKey(RPAConfiguration rpaConfiguration);
}
