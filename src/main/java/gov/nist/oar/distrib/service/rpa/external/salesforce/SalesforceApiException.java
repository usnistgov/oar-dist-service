package gov.nist.oar.distrib.service.rpa.external.salesforce;

import gov.nist.oar.distrib.service.rpa.exceptions.SalesforceAPIException;
import gov.nist.oar.distrib.service.rpa.external.ExternalApiException;

public class SalesforceApiException extends ExternalApiException {

    // HTTP status code and error message
    private int status;
    private String message;

    /**
     * Constructor to create an SalesforceAPIException with a specific HTTP status code and error message.
     * @param status the HTTP status code for the exception
     * @param message the error message for the exception
     */
    public SalesforceApiException(int status, String message) {
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
