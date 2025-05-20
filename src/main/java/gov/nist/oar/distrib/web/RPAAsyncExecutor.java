package gov.nist.oar.distrib.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import gov.nist.oar.distrib.service.rpa.RPARequestHandler;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
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
    public void handleAfterRecordCreationAsync(RecordWrapper wrapper, UserInfoWrapper input, int code) {
        try {
            handler.handleAfterRecordCreation(wrapper, input, code);
        } catch (RequestProcessingException e) {
            LOGGER.error("Async post-processing failed for record ID {}: {}", wrapper.getRecord().getId(),
                    e.getMessage(), e);
        }
    }
}
