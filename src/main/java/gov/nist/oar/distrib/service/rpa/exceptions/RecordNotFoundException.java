package gov.nist.oar.distrib.service.rpa.exceptions;

import org.apache.http.HttpStatus;

/**
 * an exception indicating that the record was not found.
 */
public class RecordNotFoundException extends SalesforceAPIException {

    private static final long serialVersionUID = 1L;

    /**
     * Indicate that a particular the record with given ID could not be found
     *
     * @param message the description of the problem
     * @param id      the identifier used to find the record (can be null)
     */
    public RecordNotFoundException(String message, int status) {
        super(HttpStatus.SC_NOT_FOUND, message);
    }

    /**
     * Helper method to create a new RecordNotFoundException with a default message and HTTP status.
     *
     * @param recordId the ID of the record that was not found
     * @return a new instance of RecordNotFoundException
     */
    public static RecordNotFoundException fromRecordId(String recordId) {
        String message = String.format("Record with ID=%s could not be found", recordId);
        return new RecordNotFoundException("Record with ID=" + recordId + " could not be found",
                HttpStatus.SC_NOT_FOUND);
    }
}
