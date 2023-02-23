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
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(
        name = "Restricted Public Access Request Handler API",
        description = "These endpoints will handle end user request to download data under restricted public access."
)
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:4201"})
@RequestMapping(value = "/ds/rpa")
public class RPARequestHandlerController {
    @Autowired
    RPARequestHandlerService service;
    private final static Logger LOGGER = LoggerFactory.getLogger(RPARequestHandlerController.class);

    /**
     * Test connection to Salesforce.
     */
    @GetMapping("test")
    String testConnectionToSalesforceAPIs() {
        LOGGER.info("Testing connection to Salesforce APIs...");
        return "Salesforce API is available.";
    }

    /**
     * Get information about record.
     *
     * @param id - the record id.
     *
     * @return RecordWrapper - the requested record wrapped within a "record" envelope.
     */
    @GetMapping(value = "{id}")
    RecordWrapper getRecord(@PathVariable String id) {
        LOGGER.info("Getting information about record with ID = " + id);
        RecordWrapper record = service.getRecord(id);
        LOGGER.debug("RECORD=" + record.toString());
        return record;
    }

    /**
     * Create a new record.
     *
     * @param userInfoWrapper - user information, serves as payload to create a new record.
     *
     * @return RecordWrapper - the created record.
     */
    @PostMapping(consumes = {"application/json"})
    RecordWrapper createRecord(@RequestBody UserInfoWrapper userInfoWrapper) {
        LOGGER.info("Creating a new record...");
        RecordWrapper newRecord = service.createRecord(userInfoWrapper);
        LOGGER.debug("RECORD=" + newRecord.toString());
        return newRecord;
    }

    /**
     * Update a record, specifically the status of the record.
     *
     * @param id - the id of the record to update
     * @param patch - the object containing the changes to update
     *
     * @return RecordStatus - the new status of the record
     */
    @PatchMapping(value = "{id}", consumes = "application/json")
    public RecordStatus updateRecord(@PathVariable String id, @RequestBody RecordPatch patch) {
        LOGGER.info("Updating approval status of record with ID = " + id);
        return service.updateRecord(patch.getApprovalStatus(), id);
    }
}
