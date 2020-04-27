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

/**
 * a base class for {@link gov.nist.oar.distrib.cachemgr.CacheObject} implementations.
 */
public abstract class CacheObjectCheckBase implements CacheObjectCheck {

    /**
     * the CacheObject being checked.  It must have a non-null volume field value
     */
    protected CacheObject co = null;

    CacheObjectCheckBase(CacheObject obj) {
        co = obj;
    }

    /**
     * return the CacheObject being checked via the check() method.  This object should include a fully 
     * specified CacheVolume instance, allowing the caller to take action (like remove the file) if the 
     * the check fails.
     */
    @Override
    public CacheObject getObject() { return co; }
}


