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
 * an exception indicating a cache object's integrity is compromised in some way.
 */
public class IntegrityException extends CacheManagementException {

    protected CacheObject co = null;

    /**
     * create an exception with a custom message
     * @param msg    a custom message
     * @param ex     a Throwable that was caught as the underlying cause 
     *                  of the error
     */
    public IntegrityException(String msg, Throwable ex) {
        super(msg, ex);
    }

    /**
     * create an exception with a custom message
     * @param msg      a custom message
     * @param ex       a Throwable that was caught as the underlying cause 
     *                    of the error
     * @param obj      the cache object found to be corrupted in some way
     */
    public IntegrityException(String msg, CacheObject obj, Throwable ex) {
        super(msg, ex);
        co = obj;
    }

    /**
     * create an exception with a custom message
     * @param msg    a custom message
     */
    public IntegrityException(String msg) {
        this(msg, null, null);
    }

    /**
     * create an exception with a custom message
     * @param msg      a custom message
     * @param obj      the cache object that failed a check
     */
    public IntegrityException(String msg, CacheObject obj) {
        this(msg, obj, null);
    }

    /**
     * create an exception that wraps another exception.  A message is 
     * generated from the wrapped exception's message and the volume name.
     * @param obj      the cache object that failed its integrity check
     * @param ex       a Throwable that was caught as the underlying cause 
     *                    of the error
     */
    public IntegrityException(CacheObject obj, Throwable ex) {
        this(messageFor(obj, ex), obj, ex);
    }

    /**
     * return the CacheObject found to be corrupted or null if not known
     */
    public CacheObject getObject() {
        return co;
    }

    /**
     * return the name of the object found to be corrupted, or null if the
     * name is not known or applicable.  A non-null name will be a concatonation
     * of the volume name and the volume-local name, delimited by a colon.  
     */
    public String getObjectName() {
        if (co == null)
            return null;
        return makeObjectName(co);
    }

    protected static String makeObjectName(CacheObject co) {
        return co.volname + ":" + co.name;
    }

    protected static String messageFor(CacheObject co, Throwable cause) {
        StringBuilder sb = new StringBuilder();
        if (co != null) 
            sb.append(makeObjectName(co)).append(": ");
        sb.append("Cache object appears to be corrupted");

        if (cause != null) {
            sb.append(": ");
            String name = cause.getClass().getSimpleName();
            if (name != null)
                sb.append('[').append(name).append("] ");
            sb.append(cause.getMessage());
        }
        return sb.toString();
    }
}

