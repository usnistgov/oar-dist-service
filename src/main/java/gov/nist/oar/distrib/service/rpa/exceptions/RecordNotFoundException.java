package gov.nist.oar.distrib.service.rpa.exceptions;

/**
 * an exception indicating that the record was not found.
 */
public class RecordNotFoundException extends RPAException {

    private static final long serialVersionUID = 1L;

    /**
     * The record identifier
     */
    public String id = null;

    /**
     * Indicate that a particular the record with given ID could not be found
     * @param message   the description of the problem
     * @param id        the identifier used to find the record (can be null)
     */
    public RecordNotFoundException(String message, String id) {
        super(message);
        this.id = id;
    }

    /**
     * * Create an instance for a given identifier
     */
    public static RecordNotFoundException forID(String id) {
        return new RecordNotFoundException("Record with ID=" + id + " could not be found", id);
    }
}
