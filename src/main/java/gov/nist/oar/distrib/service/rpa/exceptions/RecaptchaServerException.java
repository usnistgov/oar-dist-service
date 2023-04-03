package gov.nist.oar.distrib.service.rpa.exceptions;

/**
 * This exception is thrown when the reCAPTCHA response is invalid on the server side.
 */
public class RecaptchaServerException extends RecaptchaException {

    private static final long serialVersionUID = 1L;
    /**
     * Constructs a new ReCaptchaServerException with the specified detail message.
     *
     * @param message the detail message.
     */
    public RecaptchaServerException(String message) {
        super(message);
    }
}