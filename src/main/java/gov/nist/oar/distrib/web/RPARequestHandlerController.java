package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.service.rpa.RPARequestHandlerService;
import gov.nist.oar.distrib.service.rpa.model.RecordPatch;
import gov.nist.oar.distrib.service.rpa.model.RecordStatus;
import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(
        name = "Restricted Public Access Request Handler API",
        description = "These endpoints will handle end user request to download data under restricted public access."
)
@RequestMapping(value = "/rpa")
public class RPARequestHandlerController {
    @Autowired
    RPARequestHandlerService service;
    private final static Logger LOGGER = LoggerFactory.getLogger(RPARequestHandlerController.class);

    @GetMapping("test")
    String testConnectionToSalesforceAPIs() {
        LOGGER.info("Testing connection to Salesforce APIs...");
        return "Salesforce API is available.";
    }

    @GetMapping(value = "{id}")
    RecordWrapper getRecord(@PathVariable String id) {
        LOGGER.info("Getting information about record with ID = " + id);
        RecordWrapper record = service.getRecord(id);
        LOGGER.debug("RECORD=" + record.toString());
        return record;
    }

    @PostMapping(consumes = {"application/json"})
    RecordWrapper createRecord(@RequestBody UserInfoWrapper userInfoWrapper) {
        LOGGER.info("Creating a new record...");
        RecordWrapper newRecord = service.createRecord(userInfoWrapper);
        LOGGER.debug("RECORD=" + newRecord.toString());
        return newRecord;
    }

    @PatchMapping(value = "{id}", consumes = "application/json")
    public RecordStatus updateRecord(@PathVariable String id, @RequestBody RecordPatch patch) {
        LOGGER.info("Updating approval status of record with ID = " + id);
        return service.updateRecord(patch.getApprovalStatus(), id);
    }
}
