package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.Record;

/**
 * A handler for handling record creation events.
 */
public interface RecordCreationHandler {

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
}