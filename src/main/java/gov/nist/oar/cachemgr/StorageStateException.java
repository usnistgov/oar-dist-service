/*
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
package gov.nist.oar.cachemgr;

/**
 * an exception indicating an operation failure using a storage system because that system was 
 * found to be in an unexpected state.
 */
public class StorageStateException extends StorageVolumeException {

    /**
     * create an exception with a custom message
     * @param msg    a custom message
     * @param ex     a Throwable that was caught as the underlying cause 
     *                  of the error
     */
    public StorageStateException(String msg, Throwable ex) {
        super(msg, ex);
    }

    /**
     * create an exception with a custom message
     * @param msg      a custom message
     * @param ex       a Throwable that was caught as the underlying cause 
     *                    of the error
     * @param volname  the name of the storage volume where the error 
     *                    occurred 
     */
    public StorageStateException(String msg, Throwable ex, String volname) {
        super(msg, ex, volname);
    }

    /**
     * create an exception with a custom message
     * @param msg    a custom message
     */
    public StorageStateException(String msg) {
        this(msg, null, null);
    }

    /**
     * create an exception with a custom message
     * @param msg      a custom message
     * @param volname  the name of the storage volume where the error 
     *                  occurred 
     */
    public StorageStateException(String msg, String volname) {
        this(msg, null, volname);
    }

    /**
     * create an exception that wraps another exception.  A message is 
     * generated from the wrapped exception's message
     * @param ex       a Throwable that was caught as the underlying cause 
     *                    of the error
     */
    public StorageStateException(Throwable ex) {
        super(ex);
    }

    /**
     * create an exception that wraps another exception.  A message is 
     * generated from the wrapped exception's message and the volume name.
     * @param ex       a Throwable that was caught as the underlying cause 
     *                    of the error
     * @param volname  the name of the storage volume where the error 
     *                    occurred 
     */
    public StorageStateException(Throwable ex, String volname) {
        this(messageFor(ex, volname), ex, volname);
    }

    /**
     * return a message prefix that can introduce a more specific message
     */
    public static String getMessagePrefix() {
        return "Unexpected state while accessing storage volume";
    }
}


