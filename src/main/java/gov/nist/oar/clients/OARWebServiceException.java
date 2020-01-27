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
 * an OARServiceException specifically for web services that indicates that the service returned an
 * unexpected response code.
 */
public class OARWebServiceException extends OARServiceException {

    public int statusCode = 0;
    public String statusMessage = null;

    /**
     * initialize the exception around the response code and message.
     * @param message   an arbitrary message describing what went wrong
     * @param code      the response code returned from the service
     * @param what      the associated message returned for the code
     */
    public OARWebServiceException(String message, int code, String what) {
        super(message);
        statusCode = code;
        statusMessage = what;
    }

    /**
     * initialize the exception around the response code and message.  A default message is generated.
     * @param code      the response code returned from the service
     * @param what      the associated message returned for the code
     */
    public OARWebServiceException(int code, String what) {
        this(makeDefaultMessage(code, what), code, what);
    }

    /**
     * create a default message for a failure based on the response code and associated message
     * @param code      the response code returned from the service
     * @param what      the associated message returned for the code
     */
    public static String makeDefaultMessage(int code, String what) {
        StringBuilder sb = new StringBuilder();

        if (code >= 500)
            sb.append("Failure due to server-side error");
        else if (code >= 400)
            sb.append("Failure due to client-side usage error");
        else
            sb.append("Unexpected error response from server");
        if (what != null)
            sb.append(": ").append(what);
        sb.append(" (").append(Integer.toString(code)).append(")");
        
        return sb.toString();
    }
}

    
