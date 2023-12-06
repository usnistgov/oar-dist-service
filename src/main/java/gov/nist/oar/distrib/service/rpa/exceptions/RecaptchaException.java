package gov.nist.oar.distrib.service.rpa.exceptions;

/**
 * This is the base class for all exceptions related to the reCAPTCHA verification process.
 */
public class RecaptchaException extends RuntimeException {

    /**
     * Constructs a new ReCaptchaException with the specified detail message.
     *
     * @param message the detail message.
     */
    public RecaptchaException(String message) {
        super(message);
    }
}


