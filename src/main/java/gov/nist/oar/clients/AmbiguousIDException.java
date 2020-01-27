/**
 * This software was developed at the National Institute of Standards and Technology by employees of
 * the Federal Government in the course of their official duties. Pursuant to title 17 Section 105
 * of the United States Code this software is not subject to copyright protection and is in the
 * public domain. This is an experimental system. NIST assumes no responsibility whatsoever for its
 * use by other parties, and makes no guarantees, expressed or implied, about its quality,
 * reliability, or any other characteristic. We would appreciate acknowledgement if the software is
 * used. This software can be redistributed and/or modified freely provided that any derivative
 * works bear some notice that they are derived from it, and any modified versions bear some notice
 * that they have been modified.
 */
package gov.nist.oar.distrib.clients;

/**
 * an exception indicating an error that occurred while interacting with an OAR system service.  
 */
public class AmbiguousIDException extends OARServiceException {

    public String id = null;

    /**
     * initialize the exception.  
     * @param message an arbitrary message describing what went wrong
     * @param id      the ambiguous identifier
     * @param cause   an exception representing the underlying failure that occurred with the identifier
     */
    public AmbiguousIDException(String message, String identifier, Throwable cause) {
        super(message, cause);
        id = identifier;
    }

    /**
     * initialize the exception.  A default message is generated based on the given identifier and cause.
     * @param id      the ambiguous identifier
     * @param cause   an exception representing the underlying failure that occurred with the identifier
     */
    public AmbiguousIDException(String id, Throwable cause) {
        this(AmbiguousIDException.makeDefaultMessage(id, cause), id, cause);
    }

    /**
     * initialize the exception.  A default message is generated based on the given identifier
     * @param id      the ambiguous identifier
     */
    public AmbiguousIDException(String id) {
        this(AmbiguousIDException.makeDefaultMessage(id, null), id, null);
    }

    /**
     * create a default message for a failure involving the given identifier
     * @param id      the ambiguous identifier
     * @param cause   an exception representing the underlying failure that occurred with the identifier
     */
    public static String makeDefaultMessage(String id, Throwable cause) {
        StringBuilder sb = new StringBuilder("Failed to interpret identifier");
        if (id != null)
            sb.append(": ").append(id);
        if (cause != null)
            sb.append("(").append(cause.getMessage()).append(")");
        return sb.toString();
    }
}

    
