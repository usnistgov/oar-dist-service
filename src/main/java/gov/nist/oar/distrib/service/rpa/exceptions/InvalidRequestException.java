package gov.nist.oar.distrib.service.rpa.exceptions;

/**
 * an exception indicating that a http request in invalid.
 */
public class InvalidRequestException extends RPAException {
    private static final long serialVersionUID = 1L;

    /**
     * initialize the exception
     * @param message   the description of the problem
     */
    public InvalidRequestException(String message) {
        super(message);
    }
}
