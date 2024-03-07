package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.HttpURLConnectionRPARequestHandlerService;
import gov.nist.oar.distrib.service.rpa.IRPARequestHandler;

public class RPAServiceProvider {
    RPAConfiguration rpaConfiguration;

    public RPAServiceProvider(RPAConfiguration rpaConfiguration) {
        this.rpaConfiguration = rpaConfiguration;
    }

    public IRPARequestHandler getIRPARequestHandler(RPACachingService rpaCachingService) {
        return this.getHttpURLConnectionRPARequestHandler(rpaCachingService);
    }

    private IRPARequestHandler getHttpURLConnectionRPARequestHandler(RPACachingService rpaCachingService) {
        return new HttpURLConnectionRPARequestHandlerService(this.rpaConfiguration, rpaCachingService);
    }

    public RPAConfiguration getRpaConfiguration() {
        return this.rpaConfiguration;
    }
}
