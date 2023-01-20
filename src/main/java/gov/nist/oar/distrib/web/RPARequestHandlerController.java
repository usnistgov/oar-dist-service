package gov.nist.oar.distrib.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Restricted Public Access Request Handler API", description = "These endpoints will handle end user request to download data under restricted public access.")
@RequestMapping("/rpa")
public class RPARequestHandlerController {

    @Autowired
    private RPAConfiguration rpaConfiguration;
    private final static Logger LOGGER = LoggerFactory.getLogger(RPARequestHandlerController.class);

    @GetMapping("test")
    String testConnectionToSalesforceAPIs() {
        LOGGER.info("Testing connection to Salesforce APIs...");
        return "Salesforce API is available.";
    }

}