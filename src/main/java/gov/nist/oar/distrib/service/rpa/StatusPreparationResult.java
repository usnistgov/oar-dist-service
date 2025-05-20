package gov.nist.oar.distrib.service.rpa;

public class StatusPreparationResult {

    public String status;
    public String randomId;
    
    public StatusPreparationResult(String status, String randomId) {
        this.status = status;
        this.randomId = randomId;
    }
    public String getStatus() {
        return status;
    }
    public String getRandomId() {
        return randomId;
    }
    
}
