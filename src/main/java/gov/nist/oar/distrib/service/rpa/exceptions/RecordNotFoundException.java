package gov.nist.oar.distrib.service.rpa.exceptions;

public class RecordNotFoundException extends RPAException {

    private static final long serialVersionUID = 1L;

    /**
     * the identifer for the resource
     */
    public String id = null;

    /**
     * indicate that a particular version of the resource cannot be found
     * @param message   the description of the problem
     * @param id        the identifier used to find the record (can be null)
     */
    public RecordNotFoundException(String message, String id) {
        super(message);
        this.id = id;
    }

    /**
     * * create an instance for a given identifier
     */
    public static RecordNotFoundException forID(String id) {
        return new RecordNotFoundException("record with ID=" + id + " could not be found", id);
    }
}
