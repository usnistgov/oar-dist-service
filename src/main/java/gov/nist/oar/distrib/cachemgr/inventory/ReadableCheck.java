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

import java.io.InputStream;
import java.io.IOException;

/**
 * a CacheObjectCheck that ensures that the full file can be read and that's its size match that stated
 * in the object metadata.  
 */
public class ReadableCheck implements CacheObjectCheck {

    /**
     * create the check
     */
    public ReadableCheck() { }

    /**
     * execute the check
     */
    @Override
    public void check(CacheObject co)
        throws IntegrityException, StorageVolumeException, CacheManagementException
    {
        // First, check the sizes for expediency
        long vsz = co.volume.get(co.name).getSize();
        long sz = co.getSize();
        if (vsz >= 0 && sz >= 0 && vsz != sz)
            throw new IntegrityException("CacheObject has wrong size: expected="+Long.toString(sz)+
                                         " != found="+Long.toString(vsz), co);

        InputStream is = co.volume.getStream(co.name);
        int nb = 0;
        long nr = 0;
        byte[] buf = new byte[50000];
        try {
            while ( (nr = is.read(buf)) >= 0 )
                nb += nr;
        }
        catch (IOException ex) {
            throw new CacheManagementException("IO exception while reading from " +
                                               co.volume.getName() + ":" + co.name + ": " +
                                               ex.getMessage(), ex);
        }
        finally {
            try { is.close(); }
            catch (IOException ex) {
                throw new CacheManagementException("IO exception while object stream: " + 
                                                   co.volume.getName() + ":" + co.name + ": " +
                                                   ex.getMessage(), ex);
            }
        }
        if (nb != vsz)
            throw new IntegrityException("Unexpected number of bytes found: read="+Long.toString(nr)+
                                         " != expected="+Long.toString(vsz));
    }
}
