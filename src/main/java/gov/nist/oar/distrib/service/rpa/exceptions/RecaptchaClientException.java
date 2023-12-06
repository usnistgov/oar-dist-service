package gov.nist.oar.distrib.service.rpa.exceptions;

/**
 * This exception is thrown when the reCAPTCHA response is invalid on the client side.
 */
public class RecaptchaClientException extends RecaptchaException {

    private static final long serialVersionUID = 1L;
    /**
     * Constructs a new ReCaptchaClientException with the specified detail message.
     *
     * @param message the detail message.
     */
    public RecaptchaClientException(String message) {
        super(message);
    }
}

