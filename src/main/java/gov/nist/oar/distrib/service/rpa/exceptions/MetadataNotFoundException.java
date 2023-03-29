package gov.nist.oar.distrib.service.rpa.exceptions;

public class MetadataNotFoundException extends RPAException {

    private static final long serialVersionUID = 1L;

    /**
     * initialize the exception
     * @param message   the description of the problem
     */
    public MetadataNotFoundException(String message) {
        super(message);
    }
}
