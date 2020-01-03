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
 * 
 * @author: Raymond Plante
 */
package gov.nist.oar.distrib.cachemgr;

/**
 * an exception indicating that something went wrong while restoring a file from long-term storage
 */
public class RestorationException extends CacheManagementException {

    protected String file = null;

    /**
     * create an exception with a custom message
     * @param msg    a custom message
     * @param ex     a Throwable that was caught as the underlying cause 
     *                  of the error
     */
    public RestorationException(String msg, Throwable ex) {
        super(msg, ex);
    }

    /**
     * create an exception with a custom message
     * @param msg      a custom message
     * @param ex       a Throwable that was caught as the underlying cause 
     *                    of the error
     * @param reqfile  the name of the file that failed to be restored
     */
    public RestorationException(String msg, Throwable ex, String reqfile) {
        super(msg, ex);
        file = reqfile;
    }

    /**
     * create an exception with a custom message
     * @param msg    a custom message
     */
    public RestorationException(String msg) {
        this(msg, null, null);
    }

    /**
     * create an exception with a custom message
     * @param msg      a custom message
     * @param reqfile  the name of the file that failed to be restored
     */
    public RestorationException(String msg, String reqfile) {
        this(msg, null, reqfile);
    }

    /**
     * create an exception that wraps another exception.  A message is 
     * generated from the wrapped exception's message
     * @param ex       a Throwable that was caught as the underlying cause 
     *                    of the error
     */
    public RestorationException(Throwable ex) {
        super(ex);
    }

    /**
     * create an exception that wraps another exception.  A message is 
     * generated from the wrapped exception's message and the volume name.
     * @param ex       a Throwable that was caught as the underlying cause 
     *                    of the error
     * @param reqfile  the name of the file that failed to be restored
     */
    public RestorationException(Throwable ex, String reqname) {
        this(messageFor(ex, reqname), ex, reqname);
    }

    /**
     * return the name of the file that failed to be restored, or null if the 
     * name is not known or applicable.
     */
    public String getFilename() {
        return file;
    }

    protected static String messageFor(Throwable cause, String reqfile) {
        StringBuilder sb = new StringBuilder(getMessagePrefix());
        if (reqfile != null)
            sb.append('(').append(reqfile).append(')');
        sb.append(": ");
        
        String name = cause.getClass().getSimpleName();
        if (name != null)
            sb.append('[').append(name).append("] ");
        sb.append(cause.getMessage());
        return sb.toString();
    }

    protected static String messageFor(Throwable cause) {
        return messageFor(cause, null);
    }

    /**
     * return a message prefix that can introduce a more specific message
     */
    public static String getMessagePrefix() {
        return "Problem restoring file";
    }

}

