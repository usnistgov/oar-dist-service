package gov.nist.oar.distrib.service.rpa.exceptions;

/**
 * This exception is thrown when the reCAPTCHA verification process fails.
 */
public class RecaptchaVerificationFailedException extends RecaptchaException {

    private static final long serialVersionUID = 1L;
    /**
     * Constructs a new RecaptchaVerificationFailedException with the specified detail message.
     *
     * @param message the detail message.
     */
    public RecaptchaVerificationFailedException(String message) {
        super(message);
    }
}
