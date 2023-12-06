package gov.nist.oar.distrib.service.rpa.exceptions;

/**
 * an exception indicating that the record update has failed.
 */
public class FailedRecordUpdateException extends RPAException {

    private static final long serialVersionUID = 1L;

    /**
     * the record identifier
     */
    public String id = null;

    /**
     * indicate that the record with given ID could not be updated.
     * @param message   the description of the problem
     * @param id        the identifier used to find the record (can be null)
     */
    public FailedRecordUpdateException(String message, String id) {
        super(message);
        this.id = id;
    }

    /**
     * create an instance for a given identifier
     */
    public static FailedRecordUpdateException forID(String id) {
        return new FailedRecordUpdateException("failed to update record with ID=" + id, id);
    }
}
