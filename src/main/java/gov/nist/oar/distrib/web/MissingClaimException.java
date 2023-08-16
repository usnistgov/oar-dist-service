package gov.nist.oar.distrib.web;

/**
 * Exception class representing a missing claim within a JWT token.
 * This exception is thrown when a required claim is not found in the token.
 */
public class MissingClaimException extends Exception {
    private final String missingClaimName;

    /**
     * Constructs a new MissingClaimException with the specified missing claim name.
     *
     * @param missingClaimName the name of the missing claim, used in the exception message
     */
    public MissingClaimException(String missingClaimName) {
        super(missingClaimName + " claim is missing from the token.");
        this.missingClaimName = missingClaimName;
    }

    /**
     * Retrieves the name of the missing claim associated with this exception.
     *
     * @return the name of the missing claim
     */
    public String getMissingClaimName() {
        return missingClaimName;
    }
}
