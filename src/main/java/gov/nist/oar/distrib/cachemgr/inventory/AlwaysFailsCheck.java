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
package gov.nist.oar.distrib.cachemgr.inventory;

import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.IntegrityException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ObjectNotFoundException;

/**
 * a CacheObjectCheck that always fails and throws an exception for testing purposes.  By default, it 
 * raise an IntegrityException; however, it can be configured to throw other exceptions that are part of 
 * the interface.
 */
public class AlwaysFailsCheck implements CacheObjectCheck {

    protected Class exceptcls = null;
    protected String errmsg = null;

    /**
     * create the doomed check configured to throw an IntegrityException with a default message.
     */
    public AlwaysFailsCheck() {
        this("Compulsory integrity failure");
    }

    /**
     * Create the doomed check configured to throw an IntegrityException with a specific message.
     * @param excepmsg     the message to instantiate the exception with.  
     */
    public AlwaysFailsCheck(String excepmsg) {
        errmsg = excepmsg;
    }

    /**
     * Create the doomed check configured to throw a specific exception
     * @param throwthis    the exception class which must be a subclass of 
     *                     {@link gov.nist.oar.distrib.StorageVolumeException}, 
     *                     {@link gov.nist.oar.distrib.cachemgr.CacheManagementException}, or 
     *                     {@link gov.nist.oar.distrib.cachemgr.IntegrityException}
     * @param excepmsg     the message to instantiate the exception with.  
     */
    public AlwaysFailsCheck(Class throwthis, String excepmsg) {
        // check compatibe super class
        if (! StorageVolumeException.class.isAssignableFrom(throwthis) &&
            ! CacheManagementException.class.isAssignableFrom(throwthis) &&
            ! RuntimeException.class.isAssignableFrom(throwthis))
          throw new IllegalArgumentException("Illegal argument: throwthis class not throwable from check(): "+
                                             throwthis.getName());

        exceptcls = throwthis;
        errmsg = excepmsg;
    }

    /** 
     * execute the check that is doomed to failure.  This will raise the exception that this check was 
     * configured to throw which, by default, is an IntegrityException.
     */
    @Override
    public void check(CacheObject object)
        throws IntegrityException, StorageVolumeException, CacheManagementException
    {
        if (exceptcls != null) {
            try {
                Throwable ex = (Throwable) exceptcls.getConstructor(String.class).newInstance(errmsg);
            
                if (StorageVolumeException.class.isAssignableFrom(exceptcls))
                    throw (StorageVolumeException) ex;
                if (CacheManagementException.class.isAssignableFrom(exceptcls))
                    throw (CacheManagementException) ex;
                if (RuntimeException.class.isAssignableFrom(exceptcls))
                    throw (RuntimeException) ex;
            } catch (ReflectiveOperationException ex) {
                // shouldn't happen
                throw new RuntimeException("Programming error detected: "+ex.getMessage(), ex);
            } 
        }

        throw new IntegrityException(errmsg, object);
    }
}
