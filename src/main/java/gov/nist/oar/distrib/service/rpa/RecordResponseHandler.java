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
     * This method is called when a record update operation is successful and user was approved.
     * @param record The record that was the user approved for.
     */
    void onRecordUpdateApproved(Record record);

    /**
     * This method is called when a record update operation is successful but user was declined.
     */
    void onRecordUpdateDeclined(Record record);

}