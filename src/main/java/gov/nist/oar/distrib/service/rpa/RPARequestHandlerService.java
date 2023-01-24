package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.RecordStatus;
import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;


/**
 * Service interface for handling RPA request submissions by end users.
 * When an end user submits a new request, a new record will be created.
 */
public interface RPARequestHandlerService {

    /**
     * Get information about a specific record.
     * @param recordId  the identifier for the record.
     *
     * @return RecordWrapper -- the requested record wrapped within a "record" envelope.
     */
    RecordWrapper getRecord(String recordId);

    /**
     * Create a new record.
     * @param userInfoWrapper  the information provided by the user.
     *
     * @return RecordWrapper -- the newly created record wrapped within a "record" envelope.
     */
    RecordWrapper createRecord(UserInfoWrapper userInfoWrapper);

    /**
     * Update the status of a specific record.
     * @param recordId  the identifier for the record.
     * @param status  the new status.
     *
     * @return RecordStatus -- the updated status of the record.
     */
    RecordStatus updateRecord(String recordId, String status);

}
