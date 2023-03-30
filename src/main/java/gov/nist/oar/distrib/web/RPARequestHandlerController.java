package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.service.rpa.RPARequestHandlerService;
import gov.nist.oar.distrib.service.rpa.exceptions.FailedRecordUpdateException;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRecaptchaException;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecordNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.UnauthorizedException;
import gov.nist.oar.distrib.service.rpa.model.RecordPatch;
import gov.nist.oar.distrib.service.rpa.model.RecordStatus;
import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;


@RestController
@RequiredArgsConstructor
@Tag(
        name = "Restricted Public Access Request Handler API",
        description = "These endpoints will handle end user request to download data under restricted public access."
)
@RequestMapping(value = "/ds/rpa")
public class RPARequestHandlerController {
    RPARequestHandlerService service;

    private final static Logger LOGGER = LoggerFactory.getLogger(RPARequestHandlerController.class);

    @Autowired
    public RPARequestHandlerController(RPAServiceProvider rpaServiceProvider) {
        this.service = rpaServiceProvider.getRPARequestHandlerService(new RestTemplate());
    }
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
    @GetMapping(value = "/request/accepted/{id}")
    ResponseEntity getRecord(@PathVariable String id) throws RecordNotFoundException, UnauthorizedException {
        return new ResponseEntity(service.getRecord(id), HttpStatus.OK);
    }

    /**
     * Create a new record.
     *
     * @param userInfoWrapper - user information, serves as payload to create a new record.
     *
     * @return RecordWrapper - the created record.
     */
    @PostMapping(value = "/request/form" , consumes = {"application/json"})
    RecordWrapper createRecord(@RequestBody UserInfoWrapper userInfoWrapper)
            throws InvalidRecaptchaException, InvalidRequestException, UnauthorizedException {
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
    @PatchMapping(value = "/request/accepted/{id}", consumes = "application/json")
    public ResponseEntity updateRecord(@PathVariable String id, @RequestBody RecordPatch patch)
            throws RecordNotFoundException, UnauthorizedException, FailedRecordUpdateException {
        LOGGER.info("Updating approval status of record with ID = " + id);
        return new ResponseEntity<>(service.updateRecord(id, patch.getApprovalStatus()), HttpStatus.OK);
    }

    @ExceptionHandler(RecordNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorInfo handleRecordNotFoundException(RecordNotFoundException ex) {
        return new ErrorInfo(404, "record not found: " + ex.getMessage());
    }

    @ExceptionHandler(InvalidRecaptchaException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorInfo handleInvalidRecaptchaException(InvalidRecaptchaException ex) {
        return new ErrorInfo(400, "invalid reCaptcha: " + ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorInfo handleInvalidRequestException(InvalidRequestException ex) {
        return new ErrorInfo(400, "invalid request: " + ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleUnauthorizedException(UnauthorizedException ex) {
        return new ErrorInfo(500, "internal server error: " + ex.getMessage());
    }

    @ExceptionHandler(FailedRecordUpdateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleUnauthorizedException(FailedRecordUpdateException ex) {
        return new ErrorInfo(500, "internal server error: " + ex.getMessage());
    }
}
