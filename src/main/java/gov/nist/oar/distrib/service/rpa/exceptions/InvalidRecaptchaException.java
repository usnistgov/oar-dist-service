package gov.nist.oar.distrib.service.rpa.exceptions;

/**
 * an exception indicating that something went wrong with the recaptcha.
 */
public class InvalidRecaptchaException extends RPAException {

    private static final long serialVersionUID = 1L;

    /**
     * initialize the exception
     * @param message   the description of the problem
     */
    public InvalidRecaptchaException(String message) {
        super(message);
    }

}


