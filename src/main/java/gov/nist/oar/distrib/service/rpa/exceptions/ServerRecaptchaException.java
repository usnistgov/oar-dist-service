package gov.nist.oar.distrib.service.rpa.exceptions;

public class ServerRecaptchaException extends RPAException {

    private static final long serialVersionUID = 1L;

    /**
     * Initialize the exception
     * @param message   the description of the problem
     */
    public ServerRecaptchaException(String message) {
        super(message);
    }
}
