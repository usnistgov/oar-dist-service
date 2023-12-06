package gov.nist.oar.distrib.web;

/**
 * Exception thrown when a required claim is missing from the JWT token.
 */
public class MissingRequiredClaimException extends Exception {
    private final String missingClaimName;

    public MissingRequiredClaimException(String missingClaimName) {
        super("Missing required claim: " + missingClaimName);
        this.missingClaimName = missingClaimName;
    }

    public String getMissingClaimName() {
        return missingClaimName;
    }
}