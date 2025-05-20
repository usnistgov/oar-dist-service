package gov.nist.oar.distrib.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import gov.nist.oar.distrib.service.rpa.RPARequestHandler;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.service.rpa.model.RecordStatus;

public class RPAAsyncExecutor {

    private RPARequestHandler handler = null;
    /**
     * Logger for this class.
     */
    private final static Logger LOGGER = LoggerFactory.getLogger(RPAAsyncExecutor.class);

    public RPAAsyncExecutor(RPARequestHandler handler) {
        this.handler = handler;
    }

    /**
     * Handle the post-processing of a record creation asynchronously. This
     * method does not throw any exceptions; rather, it logs any errors and
     * continues.
     * 
     * @param wrapper The RecordWrapper containing the record and its
     *                details.
     * @param input   The UserInfoWrapper containing the original input data
     *                used to create the record.
     */
    @Async
    public void handleAfterRecordCreationAsync(RecordWrapper wrapper, UserInfoWrapper input, int code) {
        try {
            handler.handleAfterRecordCreation(wrapper, input, code);
        } catch (RequestProcessingException e) {
            LOGGER.error("Async post-processing failed for record ID {}: {}", wrapper.getRecord().getId(),
                    e.getMessage(), e);
        }
    }

    /**
     * Handle the post-processing of a record update asynchronously. This
     * method does not throw any exceptions; rather, it logs any errors and
     * continues.
     * 
     * @param record The record that was updated.
     * @param status The status of the record after the update.
     */
    @Async
    public void handleAfterRecordUpdateAsync(Record record, RecordStatus status, String smeId) {
        try {
               
            Thread.sleep(15000); // Simulate some processing time
            if (status.getApprovalStatus().startsWith("Approved_PENDING_CACHING")) {
                String finalStatus = handler.finalizeApprovalAndPatchStatus(record, smeId);
                handler.onRecordUpdateApproved(record, extractRandomIdFromStatus(finalStatus));
            } else if (status.getApprovalStatus().toLowerCase().contains("declined")) {
                handler.onRecordUpdateDeclined(record);
            } else {
                LOGGER.warn("Unhandled status '{}' for record ID {}", status.getApprovalStatus(), record.getId());
            }
        } catch (Exception e) {
            LOGGER.error("Async update post-processing failed for record ID {}: {}", record.getId(), e.getMessage(), e);
        }
    }


    private String extractRandomIdFromStatus(String approvalStatus) {
        if (approvalStatus != null && approvalStatus.startsWith("Approved_")) {
            String[] parts = approvalStatus.split("_");
            if (parts.length == 4) {
                return parts[3];
            }
        }
        return null;
    }
}
