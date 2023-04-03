package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.Record;

public class RecordResponseHandlerImpl implements RecordResponseHandler {

    private final EmailSender emailSender;

    public RecordResponseHandlerImpl(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public void onRecordCreationSuccess(Record record) {

    }

    @Override
    public void onRecordCreationFailure(int statusCode) {

    }
}
