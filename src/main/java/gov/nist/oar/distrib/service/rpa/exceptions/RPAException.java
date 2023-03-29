package gov.nist.oar.distrib.service.rpa.exceptions;

/**
 * a base exception for problems related to restricted public access.
 */
public class RPAException extends Exception {

    /**
     * Create an RPA exception with an arbitrary message
     */
    public RPAException(String msg) { super(msg); }

    /**
     * Create an RPA exception with an arbitrary message and an underlying cause
     */
    public RPAException(String msg, Throwable cause) { super(msg, cause); }

    /**
     * Create an RPA exception with an underlying cause.  A default message is created.
     */
    public RPAException(Throwable cause) { super(messageFor(cause), cause); }

    /**
     * return a message prefix that can introduce a more specific message
     */
    public static String getMessagePrefix() {
        return "RPA exception encountered: ";
    }

    protected static String messageFor(Throwable cause) {
        StringBuilder sb = new StringBuilder(getMessagePrefix());
        String name = cause.getClass().getSimpleName();
        if (name != null)
            sb.append('(').append(name).append(") ");
        sb.append(cause.getMessage());
        return sb.toString();
    }

}

