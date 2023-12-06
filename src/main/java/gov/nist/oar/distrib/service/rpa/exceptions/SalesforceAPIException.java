package gov.nist.oar.distrib.service.rpa.exceptions;

/**
 * Base class for all custom Salesforce API exceptions.
 */
public class SalesforceAPIException extends RuntimeException {
    // HTTP status code and error message
    private int status;
    private String message;

    /**
     * Constructor to create an SalesforceAPIException with a specific HTTP status code and error message.
     * @param status the HTTP status code for the exception
     * @param message the error message for the exception
     */
    public SalesforceAPIException(int status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * Getter for the HTTP status code of the exception.
     * @return the HTTP status code of the exception
     */
    public int getStatus() {
        return status;
    }

    /**
     * Getter for the error message of the exception.
     * @return the error message of the exception
     */
    @Override
    public String getMessage() {
        return message;
    }
}
