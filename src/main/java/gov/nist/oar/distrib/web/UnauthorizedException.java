package gov.nist.oar.distrib.web;

/**
 * Exception indicating that the user is unauthorized to access a resource or perform an action.
 */
public class UnauthorizedException extends RuntimeException {

    /**
     * Constructs a new UnauthorizedException with a default message.
     */
    public UnauthorizedException() {
        super("Unauthorized");
    }

    /**
     * Constructs a new UnauthorizedException with the specified message.
     *
     * @param message the detail message
     */
    public UnauthorizedException(String message) {
        super(message);
    }

}

