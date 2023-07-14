package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.IRPARequestHandler;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaVerificationFailedException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecordNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.service.rpa.model.RecordPatch;
import gov.nist.oar.distrib.service.rpa.model.RecordStatus;
import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@RequiredArgsConstructor
@Tag(
        name = "Restricted Public Access Request Handler API",
        description = "These endpoints will handle end user request to download data under restricted public access."
)
@CrossOrigin
@RequestMapping(value = "/ds/rpa")
public class RPARequestHandlerController {
    IRPARequestHandler service;
    private RequestSanitizer requestSanitizer;
    private JwtTokenValidator jwtTokenValidator;


    private final static Logger LOGGER = LoggerFactory.getLogger(RPARequestHandlerController.class);

    @Autowired
    public RPARequestHandlerController(RPAServiceProvider rpaServiceProvider, RPACachingService rpaCachingService) {
        this.service = rpaServiceProvider.getIRPARequestHandler(rpaCachingService);
        this.requestSanitizer = new RequestSanitizer();
        this.jwtTokenValidator = new JwtTokenValidator(rpaServiceProvider.getRpaConfiguration());
    }

    public void setRequestSanitizer(RequestSanitizer requestSanitizer) {
        this.requestSanitizer = requestSanitizer;
    }

    public void setJwtTokenValidator(JwtTokenValidator jwtTokenValidator) {
        this.jwtTokenValidator = jwtTokenValidator;
    }

    /**
     * Test connection to Salesforce.
     */
    @GetMapping("test")
    ResponseEntity testConnectionToSalesforceAPIs() {
        LOGGER.info("Testing connection to Salesforce APIs...");
        return new ResponseEntity("Salesforce API is available.", HttpStatus.OK);
    }

    /**
     * Get information about record.
     *
     * @param id - the record id.
     * @return RecordWrapper - the requested record wrapped within a "record" envelope.
     */
    @GetMapping(value = "/request/accepted/{id}")
    public ResponseEntity getRecord(@PathVariable String id, @RequestHeader(value = HttpHeaders.AUTHORIZATION) String authorizationHeader)
            throws RecordNotFoundException, RequestProcessingException, UnauthorizedException {
        // Extracting the token from the header
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring("Bearer ".length());

            // Validate token using JwtTokenValidator
            boolean isValid = false;
            try {
                isValid = jwtTokenValidator.validate(token) != null;
            } catch (Exception e) {
                throw new RequestProcessingException(e.getMessage());
            }

            if (isValid) {
                RecordWrapper recordWrapper = service.getRecord(id);
                return new ResponseEntity(recordWrapper, HttpStatus.OK);
            } else {
                throw new UnauthorizedException("invalid token");
            }
        } else {
            throw new UnauthorizedException("invalid authorization header");
        }
    }

    /**
     * Create a new record.
     *
     * @param userInfoWrapper - user information, serves as payload to create a new record.
     * @return RecordWrapper - the created record.
     */
    @PostMapping(value = "/request/form", consumes = {"application/json"})
    public ResponseEntity createRecord(@RequestBody UserInfoWrapper userInfoWrapper,
                                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader)
            throws InvalidRequestException, RecaptchaVerificationFailedException, RequestProcessingException {
        LOGGER.debug("Creating a new record...");
        // Sanitize and validate the user input
        requestSanitizer.sanitizeAndValidate(userInfoWrapper);
        RecordWrapper recordWrapper = service.createRecord(userInfoWrapper, authorizationHeader);
        return new ResponseEntity(recordWrapper, HttpStatus.OK);
    }

    /**
     * Update a record, specifically the status of the record.
     *
     * @param id    - the id of the record to update
     * @param patch - the object containing the changes to update
     * @return RecordStatus - the new status of the record
     */
    @PatchMapping(value = "/request/accepted/{id}", consumes = "application/json")
    public ResponseEntity updateRecord(@PathVariable String id, @RequestBody RecordPatch patch,
                                       @RequestHeader(value = HttpHeaders.AUTHORIZATION) String authorizationHeader)
            throws RecordNotFoundException, InvalidRequestException, RequestProcessingException, UnauthorizedException {

        // Extracting the token from the header
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring("Bearer ".length());
            // Validate token using JwtTokenValidator
            Map<String, String> tokenDetails = null;
            try {
                tokenDetails = jwtTokenValidator.validate(token);
            } catch (Exception e) {
                throw new RequestProcessingException(e.getMessage());
            }

            if (tokenDetails != null) {
                RecordStatus recordStatus = service.updateRecord(id, patch.getApprovalStatus());
                logUpdateAction(tokenDetails, id);
                return new ResponseEntity(recordStatus, HttpStatus.OK);
            } else {
                throw new UnauthorizedException("invalid token");
            }
        } else {
            throw new UnauthorizedException("invalid authorization header");
        }
    }

    // Log SME action
    private void logUpdateAction(Map<String, String> tokenDetails, String recordId) {
        String smeID = tokenDetails.get("email");
        LOGGER.info("SME " + smeID + " updated record with ID=" + recordId);
    }

    /**
     * Handles RecordNotFoundException and returns a custom error response with a 404 status.
     *
     * @param ex The RecordNotFoundException instance thrown.
     * @return An ErrorInfo instance containing the error details.
     */
    @ExceptionHandler(RecordNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorInfo handleRecordNotFoundException(RecordNotFoundException ex) {
        return new ErrorInfo(404, "record not found: " + ex.getMessage());
    }

    /**
     * Handles {@link InvalidRequestException} and returns a custom error response with a 400 status.
     *
     * @param ex The InvalidRequestException instance thrown.
     * @return An ErrorInfo instance containing the error details.
     */
    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorInfo handleInvalidRequestException(InvalidRequestException ex) {
        return new ErrorInfo(400, "invalid request: " + ex.getMessage());
    }

    /**
     * Handles {@link RecaptchaVerificationFailedException} and returns a custom error response with a 401 status,
     * indicating that the reCAPTCHA verification has failed and user is unauthorized.
     *
     * @param ex The RequestProcessingException instance thrown.
     * @return An ErrorInfo instance containing the error details.
     */

    @ExceptionHandler(RecaptchaVerificationFailedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorInfo handleRecaptchaVerificationFailedException(RecaptchaVerificationFailedException ex) {
        return new ErrorInfo(401, "Unauthorized: " + ex.getMessage());
    }

    /**
     * Handles {@link RequestProcessingException} and returns a custom error response with a 500 status.
     *
     * @param ex The RequestProcessingException instance thrown.
     * @return An ErrorInfo instance containing the error details.
     */
    @ExceptionHandler(RequestProcessingException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleRequestProcessingException(RequestProcessingException ex) {
        return new ErrorInfo(500, "internal server error: " + ex.getMessage());
    }


    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorInfo handleUnauthorizedException(UnauthorizedException ex) {
        return new ErrorInfo(401, "Unauthorized: " + ex.getMessage());
    }
}
