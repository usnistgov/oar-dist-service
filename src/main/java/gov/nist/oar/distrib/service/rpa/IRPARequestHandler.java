package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecaptchaVerificationFailedException;
import gov.nist.oar.distrib.service.rpa.exceptions.RecordNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.service.rpa.model.RecordStatus;
import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;

/**
 * An interface for handling requests to manage records.
 */
public interface IRPARequestHandler {

    /**
     * Updates the status of a record by ID.
     * @param recordId the ID of the record to update
     * @param status the new status for the record
     * @return the updated record status
     * @throws RecordNotFoundException if the record is not found
     * @throws InvalidRequestException if the request payload is invalid
     * @throws RequestProcessingException if there is an error while processing the request
     */
    RecordStatus updateRecord(String recordId, String status, String smeId) throws RecordNotFoundException, InvalidRequestException
            , RequestProcessingException;

    /**
     * Gets a record by ID.
     * @param recordId the ID of the record to get
     * @return the record data
     * @throws RecordNotFoundException if the record is not found
     * @throws RequestProcessingException if there is an error while processing the request
     */
    RecordWrapper getRecord(String record) throws RecordNotFoundException, RequestProcessingException;

    /**
     * Creates a new record for the specified user.
     *
     * @param userInfoWrapper     the user info and record data for the new record
     * @param authorizationHeader
     * @return the newly created record data
     * @throws InvalidRequestException    if the request payload is invalid
     * @throws RequestProcessingException if there is an error while processing the request
     */
    RecordWrapper createRecord(UserInfoWrapper userInfoWrapper) throws InvalidRequestException,
            RequestProcessingException, RecaptchaVerificationFailedException;

}
