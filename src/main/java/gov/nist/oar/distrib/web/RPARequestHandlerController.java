package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.RPARequestHandler;
import gov.nist.oar.distrib.service.rpa.RecordCreationResult;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaClientException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaServerException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaVerificationFailedException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecordNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.service.rpa.model.RecaptchaResponse;
import gov.nist.oar.distrib.service.rpa.model.RecordPatch;
import gov.nist.oar.distrib.service.rpa.model.RecordStatus;
import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.S3Client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller for handling data requests under Restricted Public Access (RPA).
 * <p>
 * This controller provides endpoints to manage user requests for downloading
 * data with restricted public access.
 * It includes functionalities such as creating new records, updating existing
 * ones, and retrieving record information.
 * <p>
 * All endpoints are prefixed with <code>/ds/rpa</code>.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "RPA Data Request Handler API", description = "API endpoints for managing user requests to download data under Restricted Public Access. "
        +
        "Provides functionalities for creating, updating, and retrieving records.")
@CrossOrigin
@RequestMapping(value = "/ds/rpa")
public class RPARequestHandlerController {
    /**
     * The primary service for handling RPA requests.
     */
    RPARequestHandler service = null;

    /**
     * The sanitizer for incoming requests.
     */
    private RequestSanitizer requestSanitizer;

    /**
     * The validator for JWT tokens.
     */
    private JwtTokenValidator jwtTokenValidator;

    private RPAConfiguration configuration;

    private RecaptchaVerificationHelper recaptchaHelper;

    private RPAAsyncExecutor asyncExecutor;
    /**
     * Logger for this class.
     */
    private final static Logger LOGGER = LoggerFactory.getLogger(RPARequestHandlerController.class);

    /**
     * Constructs a new RPARequestHandlerController.
     * <p>
     * Initializes the service, request sanitizer, and JWT token validator using the
     * provided RPA service provider
     * and caching service.
     *
     * @param rpaServiceProvider The service provider for RPA-related services.
     * @param rpaCachingService  The caching service for storing and retrieving RPA
     *                           data.
     */
    @Autowired
    public RPARequestHandlerController(RPAServiceProvider rpaServiceProvider,
            RPACachingServiceProvider cachingProvider,
            S3Client s3,
            RPAAsyncExecutor asyncExecutor)
            throws ConfigurationException, IOException, CacheManagementException {
        this(rpaServiceProvider, getCachingServiceFromProvider(cachingProvider, s3), asyncExecutor);
    }

    protected static RPACachingService getCachingServiceFromProvider(RPACachingServiceProvider cachingProvider,
            S3Client s3)
            throws ConfigurationException, IOException, CacheManagementException {
        if (cachingProvider == null || !cachingProvider.canCreateService())
            return null;
        return cachingProvider.getRPACachingService(s3);
    }

    public RPARequestHandlerController(RPAServiceProvider rpaServiceProvider,
            RPACachingService cachingService,
            RPAAsyncExecutor asyncExecutor) {
        if (cachingService != null && rpaServiceProvider != null)
            this.service = rpaServiceProvider.getRPARequestHandler(cachingService);
        this.requestSanitizer = new RequestSanitizer();
        this.configuration = rpaServiceProvider.getRpaConfiguration();
        this.jwtTokenValidator = new JwtTokenValidator(this.configuration);
        this.recaptchaHelper = new RecaptchaVerificationHelper(this.configuration);
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * a specialized exception indicating that a CacheManager is not currently in
     * use. This is used
     * to inform clients to such if they try to access endpoints provided by this
     * class.
     */
    class NotOperatingException extends RequestProcessingException {
        NotOperatingException(String message) {
            super(message);
        }

        NotOperatingException() {
            this("Restricted data service is not in operation at this time (no cache manager available)");
        }
    }

    private void _checkForService() throws NotOperatingException {
        if (service == null)
            throw new NotOperatingException();
    }

    /**
     * Sets the request sanitizer for this controller.
     *
     * @param requestSanitizer The RequestSanitizer to set.
     */
    public void setRequestSanitizer(RequestSanitizer requestSanitizer) {
        this.requestSanitizer = requestSanitizer;
    }

    /**
     * Sets the JWT token validator for this controller.
     *
     * @param jwtTokenValidator The JwtTokenValidator to set.
     */
    public void setJwtTokenValidator(JwtTokenValidator jwtTokenValidator) {
        this.jwtTokenValidator = jwtTokenValidator;
    }

    public RPAConfiguration getConfig() {
        return this.configuration;
    }

    public void setConfiguration(RPAConfiguration config) {
        this.configuration = config;
    }

    public void setRecaptchaHelper(RecaptchaVerificationHelper recaptchaHelper) {
        this.recaptchaHelper = recaptchaHelper;
    }

    /**
     * Tests the connection to Salesforce APIs.
     * <p>
     * This method verifies whether a connection can be established to the
     * Salesforce APIs.
     * It performs a simple health check by trying to reach the Salesforce server,
     * and
     * returns the status of this attempt in the ResponseEntity.
     *
     * <p>
     * Endpoint: <code>GET /test</code>
     *
     * @return ResponseEntity The response from the Salesforce server encapsulated
     *         within a ResponseEntity object.
     *         The response status indicates whether the connection test was
     *         successful.
     */
    @GetMapping("test")
    ResponseEntity<String> testConnectionToSalesforceAPIs() {
        _checkForService();
        LOGGER.info("Testing connection to Salesforce APIs...");
        // TODO: make Salesforce API call
        return new ResponseEntity<String>("Salesforce API is available.", HttpStatus.OK);
    }

    /**
     * Retrieves information about a specific record identified by its ID.
     * <p>
     * This method returns a RecordWrapper object encapsulating the requested
     * record's data.
     * The operation requires authorization as indicated by the authorization
     * header.
     *
     * <p>
     * Endpoint: <code>GET /request/accepted/{id}</code>
     *
     * @param id                  The unique identifier of the record to retrieve.
     * @param authorizationHeader The authorization header, indicating the caller's
     *                            credentials.
     * @return ResponseEntity A ResponseEntity encapsulating the HTTP response. On
     *         success, it contains a
     *         RecordWrapper object representing the requested record.
     * @throws RecordNotFoundException    If a record with the specified ID could
     *                                    not be found.
     * @throws RequestProcessingException If an error occurs during the request
     *                                    processing.
     * @throws UnauthorizedException      If the authorization header does not
     *                                    contain valid credentials.
     */
    @GetMapping(value = "/request/accepted/{id}", produces = "application/json")
    public ResponseEntity<RecordWrapper> getRecord(@PathVariable String id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION) String authorizationHeader)
            throws RecordNotFoundException, RequestProcessingException, UnauthorizedException {

        _checkForService();
        LOGGER.debug("Attempting to retrieve record with ID {}", id);

        // Extracting the token from the header
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring("Bearer ".length());

            // Validate token using JwtTokenValidator
            boolean isValid = false;
            try {
                isValid = jwtTokenValidator.validate(token) != null;
                LOGGER.debug("Token validation successful for record with ID: {}", id);
            } catch (Exception e) {
                LOGGER.error("Error during token validation for record with ID: {}. Error Message: {}", id,
                        e.getMessage());
                throw new RequestProcessingException(e.getMessage());
            }

            if (isValid) {
                RecordWrapper recordWrapper = service.getRecord(id);
                LOGGER.debug("Successfully retrieved record with ID: {}", id);
                return new ResponseEntity<RecordWrapper>(recordWrapper, HttpStatus.OK);
            } else {
                LOGGER.warn("Invalid token provided for record with ID: {}", id);
                throw new UnauthorizedException("invalid token");
            }
        } else {
            LOGGER.warn("Invalid authorization header for record with ID: {}", id);
            throw new UnauthorizedException("invalid authorization header");
        }
    }

    /**
     * Creates a new record based on user information, subject to reCAPTCHA
     * verification.
     * <p>
     * This method takes a UserInfoWrapper object as a request body and creates a
     * new record accordingly.
     * reCAPTCHA verification is performed unless the authorization header matches a
     * configured bypass token.
     * The created record is encapsulated within a RecordWrapper object and returned
     * in the HTTP response.
     * <p>
     * Endpoint: <code>POST /request/form</code>
     * <p>
     * Content type: <code>application/json</code>
     *
     * @param userInfoWrapper     The UserInfoWrapper object encapsulating the user
     *                            information used to create a new
     *                            record.
     * @param authorizationHeader The optional authorization header, indicating the
     *                            caller's credentials. May be used
     *                            to bypass reCAPTCHA verification.
     * @return ResponseEntity A ResponseEntity that encapsulates the HTTP response.
     *         On success, it contains a
     *         RecordWrapper object representing the newly created record.
     * @throws InvalidRequestException              If the request or the
     *                                              UserInfoWrapper object is
     *                                              invalid.
     * @throws RecaptchaVerificationFailedException If the reCAPTCHA verification
     *                                              fails.
     * @throws RequestProcessingException           If an error occurs during
     *                                              request processing.
     * @throws UnauthorizedException                If the request is unauthorized
     *                                              (optional, to be implemented).
     */
    @PostMapping(value = "/request/form", consumes = { "application/json" }, produces = "application/json")
    public ResponseEntity<RecordWrapper> createRecord(@RequestBody UserInfoWrapper userInfoWrapper,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader)
            throws InvalidRequestException, RecaptchaVerificationFailedException, RequestProcessingException {
        _checkForService();
        LOGGER.debug("Attempting to create a new record...");

        // Check if reCAPTCHA verification should be skipped
        boolean shouldVerifyRecaptcha = recaptchaHelper.shouldVerifyRecaptcha(authorizationHeader);

        RecaptchaResponse recaptchaResponse;
        if (shouldVerifyRecaptcha) {
            String recaptchaToken = userInfoWrapper.getRecaptcha();
            try {
                recaptchaResponse = recaptchaHelper.verifyRecaptcha(recaptchaToken);
            } catch (RecaptchaServerException e) {
                throw new RequestProcessingException(e.getMessage());
            } catch (RecaptchaClientException e) {
                throw new InvalidRequestException(e.getMessage());
            }

            if (!recaptchaResponse.isSuccess()) {
                throw new RecaptchaVerificationFailedException("reCAPTCHA verification failed");
            }
        }

        // Sanitize and validate the user input
        LOGGER.debug("Sanitizing and validating user input...");
        requestSanitizer.sanitizeAndValidate(userInfoWrapper);

        RecordCreationResult result = service.createRecord(userInfoWrapper);

        asyncExecutor.handleAfterRecordCreationAsync(result.getRecordWrapper(), userInfoWrapper, result.getStatusCode());

        LOGGER.debug("Record successfully created. Returning response.");
        return ResponseEntity.ok(result.getRecordWrapper());
    }

    /**
     * Updates a record's status based on the given changes.
     * <p>
     * This method patches a record with a given ID using the provided RecordPatch
     * object and
     * then returns the new status of the record. The operation requires
     * authorization as indicated
     * by the authorization header.
     *
     * <p>
     * Endpoint: <code>PATCH /request/accepted/{id}</code>
     * <p>
     * Content type: <code>application/json</code>
     *
     * @param id                  The unique identifier of the record to update.
     * @param patch               The RecordPatch object containing the changes to
     *                            be applied to the record.
     * @param authorizationHeader The authorization header, indicating the caller's
     *                            credentials.
     * @return ResponseEntity A response entity encapsulating the HTTP response. On
     *         success, it contains the new
     *         status of the record.
     * @throws RecordNotFoundException    If a record with the specified ID could
     *                                    not be found.
     * @throws InvalidRequestException    If the request or the provided patch is
     *                                    invalid.
     * @throws RequestProcessingException If an error occurs during request
     *                                    processing.
     * @throws UnauthorizedException      If the authorization header does not
     *                                    contain valid credentials.
     */
    @PatchMapping(value = "/request/accepted/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<RecordStatus> updateRecord(@PathVariable String id, @RequestBody RecordPatch patch,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION) String authorizationHeader)
            throws RecordNotFoundException, InvalidRequestException, RequestProcessingException, UnauthorizedException {

        _checkForService();
        LOGGER.debug("Attempting to update record with ID: {}", id);

        // Extracting the token from the header
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring("Bearer ".length());
            // Validate token using JwtTokenValidator
            Map<String, String> tokenDetails = null;
            try {
                tokenDetails = jwtTokenValidator.validate(token);
                LOGGER.debug("Token is validated");
            } catch (MissingRequiredClaimException e) {
                String missingClaimName = e.getMissingClaimName();
                LOGGER.debug("Missing required claim detected: " + missingClaimName);
                throw new InvalidRequestException("JWT token invalid");
            } catch (JwtException e) {
                LOGGER.debug("Token validation failed due to a JwtException: " + e.getMessage());
                throw new UnauthorizedException("JWT token validation failed");
            }

            if (tokenDetails != null) {
                LOGGER.debug("Updating record with ID: {}", id);
                String smeId = tokenDetails.get("userEmail");
                RecordStatus recordStatus = service.updateRecord(id, patch.getApprovalStatus(), smeId);

                logUpdateAction(tokenDetails, id);

                LOGGER.debug("Record successfully updated");
                return new ResponseEntity<RecordStatus>(recordStatus, HttpStatus.OK);
            } else {
                LOGGER.error("Token is invalid");
                throw new UnauthorizedException("invalid token");
            }
        } else {
            LOGGER.error("Invalid authorization header");
            throw new UnauthorizedException("invalid authorization header");
        }

    }

    /**
     * Logs the update action made by a Subject Matter Expert (SME) and their
     * identity.
     * <p>
     * This private method logs an update action made by an SME. It extracts the
     * SME's email and full name
     * from the supplied token details and logs these details along with the ID of
     * the updated record.
     *
     * @param tokenDetails A Map containing the token details, specifically the
     *                     SME's email (key: "userEmail") and
     *                     full name (key: "userFullname").
     * @param recordId     The ID of the record that was updated.
     */
    private void logUpdateAction(Map<String, String> tokenDetails, String recordId) {
        String smeEmail = tokenDetails.get("userEmail");
        String smeFullname = tokenDetails.get("userFullname");
        LOGGER.info(smeFullname + " (" + smeEmail + ") has updated record with ID=" + recordId);
    }

    /**
     * Handles {@link RecordNotFoundException} by constructing a custom error
     * response with a status of 404 (Not Found).
     * <p>
     * When a RecordNotFoundException is thrown within the controller, this method
     * creates a custom ErrorInfo object
     * encapsulating
     * the status code and the exception details. The HTTP response status is set to
     * NOT_FOUND (404).
     *
     * @param ex The instance of RecordNotFoundException that was thrown.
     * @return ErrorInfo A new ErrorInfo object containing the status code and the
     *         detailed error message.
     */
    @ExceptionHandler(RecordNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public ResponseEntity<ErrorInfo> handleRecordNotFoundException(RecordNotFoundException ex) {
        LOGGER.error("RecordNotFoundException encountered: {}", ex.getMessage());

        ErrorInfo errorInfo = new ErrorInfo(404, "record not found: " + ex.getMessage());

        // Return ResponseEntity with the ErrorInfo and the appropriate HTTP status
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorInfo);
    }

    /**
     * Handles {@link InvalidRequestException} by constructing a custom error
     * response with a status of 400 (Bad
     * Request).
     * <p>
     * When an InvalidRequestException is thrown within the controller, this method
     * creates a custom ErrorInfo object
     * encapsulating
     * the status code and the exception details. The HTTP response status is set to
     * BAD_REQUEST (400).
     *
     * @param ex The instance of InvalidRequestException that was thrown.
     * @return ErrorInfo A new ErrorInfo object containing the status code and the
     *         detailed error message.
     */
    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ResponseEntity<ErrorInfo> handleInvalidRequestException(InvalidRequestException ex) {
        LOGGER.error("InvalidRequestException encountered: {}", ex.getMessage());
        ErrorInfo errorInfo = new ErrorInfo(400, "invalid request: " + ex.getMessage());

        // Return ResponseEntity with the ErrorInfo and the appropriate HTTP status
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorInfo);
    }

    /**
     * Handles {@link RecaptchaVerificationFailedException} by constructing a custom
     * error response with a status of
     * 401 (Unauthorized).
     * <p>
     * When a RecaptchaVerificationFailedException is thrown within the controller,
     * this method creates a custom
     * ErrorInfo object encapsulating
     * the status code and the exception details. The HTTP response status is set to
     * UNAUTHORIZED (401), indicating
     * that reCAPTCHA verification has failed.
     *
     * @param ex The instance of RecaptchaVerificationFailedException that was
     *           thrown.
     * @return ErrorInfo A new ErrorInfo object containing the status code and the
     *         detailed error message.
     */
    @ExceptionHandler(RecaptchaVerificationFailedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ResponseBody
    public ResponseEntity<ErrorInfo> handleRecaptchaVerificationFailedException(
            RecaptchaVerificationFailedException ex) {
        LOGGER.error("RecaptchaVerificationFailedException encountered: {}", ex.getMessage());
        ErrorInfo errorInfo = new ErrorInfo(401, "Unauthorized: " + ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorInfo);
    }

    /**
     * Handles {@link RequestProcessingException} and constructs a custom error
     * response with a status of 500
     * (Internal Server Error).
     * <p>
     * When a RequestProcessingException is thrown within the controller, this
     * method is invoked to handle the
     * exception. It creates a
     * custom ErrorInfo object containing the status code and a message that
     * encapsulates the exception details. The
     * HTTP response status
     * is set to INTERNAL_SERVER_ERROR (500).
     *
     * @param ex The instance of RequestProcessingException that was thrown.
     * @return ErrorInfo A new ErrorInfo object that encapsulates the status code
     *         and the detailed error message.
     */
    @ResponseBody
    @ExceptionHandler(RequestProcessingException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorInfo> handleRequestProcessingException(RequestProcessingException ex) {
        LOGGER.error("RequestProcessingException encountered: {}", ex.getMessage());
        ErrorInfo errorInfo = new ErrorInfo(500, "internal server error: " + ex.getMessage());

        // Return ResponseEntity with the ErrorInfo and the appropriate HTTP status
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorInfo);
    }

    /**
     * Handles {@link UnauthorizedException} by constructing a custom error response
     * with a status of 401
     * (Unauthorized).
     * <p>
     * When an UnauthorizedException is thrown within the controller, this method is
     * invoked to manage the exception.
     * It creates a
     * custom ErrorInfo object containing the status code and a message that
     * encapsulates the exception details. The
     * HTTP response status
     * is set to UNAUTHORIZED (401).
     *
     * @param ex The instance of UnauthorizedException that was thrown.
     * @return ErrorInfo A new ErrorInfo object that encapsulates the status code
     *         and the detailed error message.
     */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ResponseBody
    public ResponseEntity<ErrorInfo> handleUnauthorizedException(UnauthorizedException ex) {
        LOGGER.error("UnauthorizedException encountered: {}", ex.getMessage());
        ErrorInfo errorInfo = new ErrorInfo(401, "Unauthorized: " + ex.getMessage());

        // Return ResponseEntity with the ErrorInfo and the appropriate HTTP status
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorInfo);
    }

    @ExceptionHandler(NotOperatingException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ResponseBody
    public ResponseEntity<ErrorInfo> handleNotOperatingException(NotOperatingException ex, HttpServletRequest req) {
        LOGGER.warn("Request to non-engaged RPACachingService: " + req.getRequestURI() + "\n  " +
                ex.getMessage());
        ErrorInfo errorInfo = new ErrorInfo(req.getRequestURI(), 503, "RPA request handling is not in operation");
        // Return ResponseEntity with the ErrorInfo and the appropriate HTTP status
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorInfo);
    }
}
