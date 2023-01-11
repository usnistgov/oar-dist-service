package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.RecordStatus;
import gov.nist.oar.distrib.service.rpa.model.RecordWrapper;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;

public interface RPARequestHandlerService {

     RecordWrapper getRecord(String recordId);
     RecordWrapper createRecord(UserInfoWrapper userInfoWrapper);
     RecordStatus updateRecord(String recordId, String status);

}
