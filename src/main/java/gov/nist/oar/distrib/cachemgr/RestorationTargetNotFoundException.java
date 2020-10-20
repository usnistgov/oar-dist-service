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
public class RestorationTargetNotFoundException extends RestorationException {

    /**
     * create an exception with a custom message
     * @param msg    a custom message
     * @param ex     a Throwable that was caught as the underlying cause 
     *                  of the error
     */
    public RestorationTargetNotFoundException(String msg, Throwable ex) {
        super(msg, ex);
    }

    /**
     * create an exception with a custom message
     * @param msg      a custom message
     * @param ex       a Throwable that was caught as the underlying cause 
     *                    of the error
     * @param target   the name of the file that failed to be restored
     */
    public RestorationTargetNotFoundException(String msg, Throwable ex, String target) {
        super(msg, ex, target);
    }

    /**
     * create an exception with a custom message
     * @param msg      a custom message
     * @param target   the name of the file that was not found
     */
    public RestorationTargetNotFoundException(String msg, String target) {
        this(msg, null, target);
    }

    /**
     * create an exception that wraps another exception.  A message is 
     * generated from the wrapped exception's message
     * @param ex       a Throwable that was caught as the underlying cause 
     *                    of the error
     */
    public RestorationTargetNotFoundException(Throwable ex) {
        super(ex);
    }

    /**
     * create an exception that wraps another exception.  A message is 
     * generated from the wrapped exception's message and the volume name.
     * @param ex       a Throwable that was caught as the underlying cause 
     *                    of the error
     * @param target  the name of the target that could not be found
     */
    public RestorationTargetNotFoundException(Throwable ex, String target) {
        this(getMessagePrefix()+target, ex, target);
    }

    /**
     * create an exception that wraps another exception.  A message is 
     * generated from the wrapped exception's message and the volume name.
     * @param target  the name of the target that could not be found
     */
    public RestorationTargetNotFoundException(String target) {
        this(getMessagePrefix()+target, target);
    }

    /**
     * return the name of the file that failed to be restored, or null if the 
     * name is not known or applicable.
     */
    public String getTarget() {
        return getFilename();
    }

    /**
     * return a message prefix that can introduce a more specific message
     */
    public static String getMessagePrefix() {
        return "Target source not found: ";
    }
}

