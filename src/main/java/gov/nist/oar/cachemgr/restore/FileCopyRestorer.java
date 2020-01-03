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
package gov.nist.oar.cachemgr.restore;

import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.cachemgr.CacheManagementException;
import gov.nist.oar.cachemgr.RestorationException;
import gov.nist.oar.cachemgr.Restorer;
import gov.nist.oar.cachemgr.Reservation;

import java.io.InputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

/**
 * a restorer that assumes that data objects are stored in long-term storage as individual files.
 */
public class FileCopyRestorer extends RestorerBase {

    /** to be considered available, object identifiers must begin with this prefix string */
    protected String prefix = "";

    /**
     * wrap a LongTermStorage object
     */
    public FileCopyRestorer(LongTermStorage lts) {
        super(lts);
    }

    /**
     * wrap a LongTermStorage object
     */
    public FileCopyRestorer(LongTermStorage lts, String prefix) {
        this(lts);
        this.prefix = prefix;
    }

    /**
     * given an object identifier, return a name for where it can be found in the storage
     */
    protected String id2name(String id) throws ObjectNotFoundException {
        if (! id.startsWith(prefix))
            throw new ObjectNotFoundException(id, store.getName());
        return id.substring(prefix.length());
    }

    @Override
    public InputStream openDataObject(String id) throws StorageVolumeException {
        try {
            return store.openFile(id2name(id));
        } catch (FileNotFoundException ex) {
            throw new ObjectNotFoundException(id, "long-term");
        }
    }

    /**
     * return true if an object does <i>not</i> exist in the long term storage system.  Returning 
     * true indicates that the object <i>may</i> exist, but it is not guaranteed.
     */
    @Override
    public boolean doesNotExist(String id) throws StorageVolumeException {
        return (! store.exists(id));
    }

    /**
     * return the size of the object with the given identifier in bytes or -1L if unknown
     */
    @Override
    public long getSizeOf(String id) throws StorageVolumeException {
        try {
            return store.getSize(id2name(id));
        } catch (FileNotFoundException ex) {
            throw new ObjectNotFoundException(id, "long-term");
        }
    }

    /**
     * return the checksum hash of the object with the given identifier or null if unknown.  
     * @throws UnsupportedOperationException   if due to implementation limitations, this Restorer is 
     *             unable to return sizes for any objects it knows about.  
     * @throws ObjectNotFoundException    if the object can not be found in the underlying storage
     */
    @Override
    public Checksum getChecksum(String id) throws StorageVolumeException {
        try {
            return store.getChecksum(id2name(id));
        } catch (FileNotFoundException ex) {
            throw new ObjectNotFoundException(id, "long-term");
        }
    }
}
