package gov.nist.oar.distrib.service.rpa.exceptions;

public class InternalServerErrorException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Initialize the exception
     * @param message   the description of the problem
     */
    public InternalServerErrorException(String message) {
        super(message);
    }
}
