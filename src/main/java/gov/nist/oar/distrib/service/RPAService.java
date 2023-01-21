package gov.nist.oar.distrib.service;

import gov.nist.oar.distrib.web.RPAConfiguration;

public class RPAService {
    RPAConfiguration rpaConfiguration = null;

    public RPAService(RPAConfiguration rpaConfiguration) {
        this.rpaConfiguration = rpaConfiguration;
    }

    public RPAConfiguration getRpaConfiguration() {
        return this.rpaConfiguration;
    }
}
