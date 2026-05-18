package gov.nist.oar.distrib.web;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import gov.nist.oar.distrib.service.rpa.RPALogContext;
import gov.nist.oar.distrib.service.rpa.RPARequestHandler;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.service.rpa.model.Record;
import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;

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
    public void handleAfterRecordCreationAsync(RecordWrapper wrapper, UserInfoWrapper input, int code,
            Map<String, String> logContext) {
        Map<String, String> previousContext = RPALogContext.capture();
        RPALogContext.restore(logContext);
        try {
            handler.handleAfterRecordCreation(wrapper, input, code);
        } catch (RequestProcessingException e) {
            String recordId = wrapper != null && wrapper.getRecord() != null ? wrapper.getRecord().getId() : "unknown";
            LOGGER.error("RPA async create post-processing failed reqId={} recordId={}: {}",
                    RPALogContext.requestId(), recordId,
                    e.getMessage(), e);
        } finally {
            RPALogContext.restore(previousContext);
        }
    }

    /**
     * Handle the post-processing of a record update (approval/decline) asynchronously.
     * This method does not throw any exceptions; rather, it logs any errors and continues.
     *
     * @param record    The record that was updated.
     * @param status    The approval status ("approved" or "declined").
     * @param datasetId The ID of the dataset associated with the record.
     */
    @Async
    public void handleAfterRecordUpdateAsync(Record record, String status, String datasetId,
            Map<String, String> logContext) {
        String previousApprovalStatus = record != null && record.getUserInfo() != null
                ? record.getUserInfo().getApprovalStatus()
                : null;
        doHandleAfterRecordUpdate(record, status, datasetId, previousApprovalStatus, logContext);
    }

    /**
     * Handle the post-processing of a record update with the approval status that
     * existed before the synchronous patch.
     *
     * @param record                 The record that was updated.
     * @param status                 The approval status ("approved" or "declined").
     * @param datasetId              The ID of the dataset associated with the record.
     * @param previousApprovalStatus The approval status before the synchronous patch.
     * @param logContext             The logging context to restore in the async thread.
     */
    @Async
    public void handleAfterRecordUpdateAsync(Record record, String status, String datasetId,
            String previousApprovalStatus, Map<String, String> logContext) {
        doHandleAfterRecordUpdate(record, status, datasetId, previousApprovalStatus, logContext);
    }

    private void doHandleAfterRecordUpdate(Record record, String status, String datasetId,
            String previousApprovalStatus, Map<String, String> logContext) {
        Map<String, String> previousContext = RPALogContext.capture();
        RPALogContext.restore(logContext);
        try {
            handler.handleAfterRecordUpdate(record, status, datasetId, previousApprovalStatus);
        } catch (RequestProcessingException e) {
            String recordId = record != null ? record.getId() : "unknown";
            LOGGER.error("RPA async update post-processing failed reqId={} recordId={} action={}: {}",
                    RPALogContext.requestId(), recordId, status, e.getMessage(), e);
        } finally {
            RPALogContext.restore(previousContext);
        }
    }
}
