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
package gov.nist.oar.distrib.web;

/**
 * an exception indicating that illegal input was provide to a web service endpoint
 */
public class ServiceSyntaxException extends RuntimeException {

    public String parameter = null;
    public String reason = null;
    public String value = null;

    /**
     * Create an exception with an arbitrary message
     */
    public ServiceSyntaxException(String msg) {
        super(msg);
    }

    /**
     * initialize the exception indicating a problem with a particular parameter
     */
    public ServiceSyntaxException(String param, String val, String expl, Throwable cause) {
        super("Malformed value for " + param + " parameter: " +
              ((val == null) ? "" : val) + ((expl == null) ? "" : ": "+expl), cause);
        parameter = param;
        reason = expl;
        value = val;
    }

    /**
     * initialize the exception indicating a problem with a particular parameter
     */
    public ServiceSyntaxException(String param, String val) {
        this(param, val, null, null);
    }
}
