package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;

import java.time.Instant;

public class Main {

    private final static String RECORD_PENDING_STATUS = "pending";
    private final static String RECORD_APPROVED_STATUS = "approved";
    private final static String RECORD_DECLINED_STATUS = "declined";

    public static void main(String[] args) {
        System.out.println(generateApprovalStatus("Approved"));
    }

    private static String generateApprovalStatus(String status) throws InvalidRequestException {
        String formattedDate = Instant.now().toString(); // ISO 8601 format: 2023-05-09T15:59:03.872Z
        String approvalStatus;
        switch (status.toLowerCase()) {
            case RECORD_APPROVED_STATUS:
                approvalStatus = "Approved_";
                break;
            case RECORD_DECLINED_STATUS:
                approvalStatus = "Declined_";
                break;
            default:
                throw new InvalidRequestException("Invalid approval status: " + status);
        }
        return approvalStatus + formattedDate;
    }
}


