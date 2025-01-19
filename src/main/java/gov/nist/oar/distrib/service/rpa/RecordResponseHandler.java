package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.Record;

/**
 * A handler for handling record creation events.
 */
public interface RecordResponseHandler {

    /**
     * Called when a record creation operation succeeds.
     * @param record the newly created record
     */
    void onRecordCreationSuccess(Record record);

    /**
     * Called when a record creation operation fails.
     * @param statusCode the HTTP status code of the failure response
     */
    void onRecordCreationFailure(int statusCode);

    /**
     * Called when a pre-approved record creation operation succeeds.
     * @param record The newly created pre-approved record
     * @param randomId The unique identifier for the cached dataset associated with the record
     */
    void onPreApprovedRecordCreationSuccess(Record record, String randomId);

    /**
     * This method is called when a record update operation is successful and user was approved.
     * @param record The record that was the user approved for.
     */
    void onRecordUpdateApproved(Record record, String randomId);

    /**
     * This method is called when a record update operation is successful but user was declined.
     */
    void onRecordUpdateDeclined(Record record);

}