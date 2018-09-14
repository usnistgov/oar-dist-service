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
package gov.nist.oar.distrib;

/**
 * a base or generic exception for problems specific to data distribution system.
 */
public class DistributionException extends Exception {

    /**
     * Create an exception with an arbitrary message
     */
    public DistributionException(String msg) { super(msg); }

    /**
     * Create an exception with an arbitrary message and an underlying cause
     */
    public DistributionException(String msg, Throwable cause) { super(msg, cause); }

    /**
     * Create an exception with an underlying cause.  A default message is created.
     */
    public DistributionException(Throwable cause) { super(messageFor(cause), cause); }

    /**
     * return a message prefix that can introduce a more specific message
     */
    public static String getMessagePrefix() {
        return "Data Distribution exception encountered: ";
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
