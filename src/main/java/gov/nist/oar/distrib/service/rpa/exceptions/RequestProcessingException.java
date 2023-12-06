package gov.nist.oar.distrib.service.rpa.exceptions;

import org.apache.http.HttpStatus;

/**
 * Custom exception to indicate that there was an error while processing the request.
 */
public class RequestProcessingException extends SalesforceAPIException {

    private static final long serialVersionUID = 1L;
    /**
     * Constructor to create a RequestProcessingException with a specific error message.
     * @param message the error message for the exception
     */
    public RequestProcessingException(String message) {
        super(HttpStatus.SC_INTERNAL_SERVER_ERROR, message);
    }
}