package gov.nist.oar.distrib.service.rpa.client;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import gov.nist.oar.distrib.service.rpa.client.impl.CreateRecordPayloadImpl;
import gov.nist.oar.distrib.service.rpa.external.ExternalCreateRecordPayload;

@JsonDeserialize(as = CreateRecordPayloadImpl.class)
public interface CreateRecordPayload {

    ExternalCreateRecordPayload toExternalPayload();
}
