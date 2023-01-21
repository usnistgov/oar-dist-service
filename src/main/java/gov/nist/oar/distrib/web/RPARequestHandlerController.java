package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.service.RPAService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Restricted Public Access Request Handler API", description = "These endpoints will handle end user request to download data under restricted public access.")
@RequestMapping(value = "/ds/rpa")
public class RPARequestHandlerController {

    @Autowired
    private RPAService rpaService;
    private final static Logger LOGGER = LoggerFactory.getLogger(RPARequestHandlerController.class);

    @GetMapping(value = "/test")
    String testConnectionToSalesforceAPIs() {
        LOGGER.info("Testing connection to Salesforce APIs, instance URL = " + rpaService.getRpaConfiguration().getSalesforceInstanceUrl());
        return "Salesforce API is available. Instance URL = " + rpaService.getRpaConfiguration().getSalesforceInstanceUrl();
    }

}