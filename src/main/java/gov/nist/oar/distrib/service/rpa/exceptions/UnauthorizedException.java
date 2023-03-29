package gov.nist.oar.distrib.service.rpa.exceptions;

public class UnauthorizedException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * initialize the exception
     * @param message   the description of the problem
     */
    public UnauthorizedException(String message) {
        super(message);
    }
}
