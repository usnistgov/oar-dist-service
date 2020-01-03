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
package gov.nist.oar.distrib.cachemgr.restore;

import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.StorageStateException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.RestorationException;
import gov.nist.oar.distrib.cachemgr.Restorer;
import gov.nist.oar.distrib.cachemgr.Reservation;

import java.io.InputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

/**
 * a restorer that extracts its objects from zip files in long-term storage
 * <p>
 * This implementation is not particularly useful on its own; besides serving as an 
 * example of an application, it can serve as base class for an implementation that 
 * leverages knowledge about how data objects are distributed among many zip files.  
 */
public class ZipFileRestorer extends RestorerBase {

    protected String defaultSourceFile = null;

    /** to be considered available, object identifiers must begin with this prefix string */
    protected String prefix = "";

    /**
     * wrap a LongTermStorage instance.  
     * <p>
     * This constructor is provided for subclasses that will override {@link #id2loc(String)}.
     */
    protected ZipFileRestorer(LongTermStorage lts) {
        super(lts);
    }

    /**
     * create the restorer assuming a single default source zip file
     */
    public ZipFileRestorer(LongTermStorage lts, String sourcezip) {
        this(lts);
        defaultSourceFile = sourcezip;
    }

    /**
     * create the restorer assuming a single default source zip file and a required 
     * input identifier prefix
     */
    public ZipFileRestorer(LongTermStorage lts, String sourcezip, String prefix) {
        this(lts, sourcezip);
        this.prefix = prefix;
    }

    /**
     * given an object identifier, return the zip file in long-term storage that contains 
     * the object and the file within the zip file that represents that object.
     * @return String[]   a 2-element array where the first element is the locaiton of the 
     *                    zip file and the second element is the name of the file to extract
     *                    from it.  
     */
    protected String[] id2location(String id) throws ObjectNotFoundException {
        if (! id.startsWith(prefix))
            throw new ObjectNotFoundException(id, store.getName());
        String[] out = new String[2];
        out[0] = defaultSourceFile;
        out[1] = id.substring(prefix.length());
        return out;
    }
        
    /**
     * return true if an object does <i>not</i> exist in the long term storage system.  Returning 
     * true indicates that the object <i>may</i> exist, but it is not guaranteed.
     * <p>
     * This implementation just determines if the identifier fails to resolve to a location via 
     * {@link id2location(String)}; if it does resolve, it does not check to see if there is data at 
     * that location.  In other words, a return of <code>false</code> does not guarantee that the 
     * object exists in storage, as described above generally.  
     */
    @Override
    public boolean doesNotExist(String id) throws StorageVolumeException {
        try {
            String[] loc = id2location(id);
            return ! store.exists(loc[0]);
        } catch (ObjectNotFoundException ex) {
            return true;
        }
    }

    /**
     * return the size of the object with the given identifier in bytes or -1L if unknown
     * @throws ObjectNotFoundException    if the object can not be found in the underlying storage
     */
    @Override
    public long getSizeOf(String id) throws StorageVolumeException {
        String[] loc = id2location(id);  // may throw 

        ZipEntry ze = null;
        try (ZipInputStream zis = openZipSource(loc[0])) {
            ze = advanceTo(zis, loc[1]);
            return ze.getSize();
        }
        catch (FileNotFoundException ex) {
            throw new ObjectNotFoundException(id, store.getName(), ex);
        }
        catch (IOException ex) {
            throw new StorageStateException("Failure while reading zip file: "+ex.getMessage(), ex);
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
        String[] loc = id2location(id);  // may throw
        
        ZipEntry ze = null;
        try (ZipInputStream zis = openZipSource(loc[0])) {
            ze = advanceTo(zis, loc[1]);
            return new Checksum(Long.toHexString(ze.getCrc()), Checksum.CRC32);
        }
        catch (FileNotFoundException ex) {
            throw new ObjectNotFoundException(id, store.getName(), ex);
        }
        catch (IOException ex) {
            throw new StorageStateException("Failure while reading zip file: "+ex.getMessage(), ex);
        }
    }

    /**
     * open a zip file and return a ZipInputStream set at the start of that file.
     */
    protected ZipInputStream openZipSource(String zipfile) throws IOException, StorageVolumeException {
        InputStream fs = store.openFile(zipfile);
        try {
            return new ZipInputStream(fs);
        }
        catch (Exception ex) {
            try { fs.close(); } catch (IOException e) { }
            throw ex;
        }
    }

    /**
     * advance the zip input stream to the location of a particular file with the given 
     * filename.
     */
    protected ZipEntry advanceTo(ZipInputStream zis, String filename)
        throws IOException, ObjectNotFoundException
    {
        ZipEntry ze = null;
        while ((ze = zis.getNextEntry()) != null) {
            if (ze.getName().equals(filename))
                return ze;
        }
        throw new ObjectNotFoundException(filename, store.getName());
    }

    @Override
    public InputStream openDataObject(String id) throws StorageVolumeException {
        String[] loc = id2location(id);  // may throw
        
        ZipEntry ze = null;
        ZipInputStream zis = null;
        try {
            zis = openZipSource(loc[0]);
            ze = advanceTo(zis, loc[1]);
            return zis;
        }
        catch (FileNotFoundException ex) {
            try { zis.close(); } catch (IOException e) { }
            throw new ObjectNotFoundException(id, store.getName(), ex);
        }
        catch (IOException ex) {
            try { zis.close(); } catch (IOException e) { }
            throw new StorageStateException("Failure while reading zip file: "+ex.getMessage(), ex);
        }
        catch (Exception ex) {
            try { zis.close(); } catch (IOException e) { }
            throw ex;
        }
    }
}

