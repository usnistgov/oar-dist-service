package gov.nist.oar.distrib.service.rpa.exceptions;

import org.apache.http.HttpStatus;

/**
 * Custom exception to indicate that the request payload is invalid.
 */
public class InvalidRequestException extends SalesforceAPIException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor to create an InvalidRequestException with a specific error message.
     * @param message the error message for the exception
     */
    public InvalidRequestException(String message) {
        super(HttpStatus.SC_BAD_REQUEST, message);
    }
}
