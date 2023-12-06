package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.DefaultRPARequestHandlerService;
import gov.nist.oar.distrib.service.rpa.HttpURLConnectionRPARequestHandlerService;
import gov.nist.oar.distrib.service.rpa.IRPARequestHandler;
import gov.nist.oar.distrib.service.rpa.RPARequestHandlerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;

public class RPAServiceProvider {
    RPAConfiguration rpaConfiguration;

    public RPAServiceProvider(RPAConfiguration rpaConfiguration) {
        this.rpaConfiguration = rpaConfiguration;
    }

    public RPARequestHandlerService getRPARequestHandlerService(RestTemplate restTemplate) {
        return this.getDefaultRPARequestHandlerService(restTemplate);
    }

    private DefaultRPARequestHandlerService getDefaultRPARequestHandlerService(RestTemplate restTemplate) {
        return new DefaultRPARequestHandlerService(this.rpaConfiguration, restTemplate);
    }

    @Autowired
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
