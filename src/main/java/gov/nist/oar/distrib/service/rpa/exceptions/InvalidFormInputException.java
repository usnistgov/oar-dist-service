package gov.nist.oar.distrib.service.rpa.exceptions;

/**
 * Exception thrown when a form input is invalid.
 */
public class InvalidFormInputException extends RuntimeException {

    /**
     * Constructs a new InvalidFormInputException with the specified detail message.
     *
     * @param message the detail message of the exception
     */
    public InvalidFormInputException(String message) {
        super(message);
    }
}

