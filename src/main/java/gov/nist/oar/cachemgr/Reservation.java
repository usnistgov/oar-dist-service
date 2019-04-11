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
 * 
 * @author: Raymond Plante
 */
package gov.nist.oar.cachemgr;

import java.io.InputStream;
import java.io.IOException;

import org.json.JSONObject;
import org.json.JSONException;

/**
 * a claim on space in a {@list gov.nist.oar.cachemgr.Cache}.  An instance is normally received 
 * from a request for a particular amount of space via one of the cache's 
 * {@list gov.nist.oar.cachemgr.Cache.reserveSpace(long)} functions.  Its interface should be used 
 * fill that space with data objects fed in via a input streams.  Once the data objects have been written,
 * any remaining reserved space can be released.  
 */
public class Reservation {
    /**
     * the name given to the reservation entered in the storage inventory
     */
    protected String name = null;

    /**
     * the number of bytes reserved by this reservation
     */
    private long _size = 0L;

    /**
     * the volume where the space is reserved.
     */
    protected CacheVolume vol = null;

    /**
     * the inventory database that should be updated as data is added to the volume
     */
    protected StorageInventoryDB db = null;

    /**
     * instantiate the reservation
     * @param resname   a name that this reservation is represented by within the StorageInventoryDB.
     * @param volume    the CacheVolume instance for the volume where the space has been reserved.
     * @param db        the StorageInventoryDB that should be used to update the volume changes after 
     *                    data are streamed into it.
     */
    public Reservation(String resname, CacheVolume volume, StorageInventoryDB db) {
        name = resname;
        vol = volume;
        db = db;
    }

    /**
     * return the remaining size of the reserved space.  As data is added the cache via this object,
     * this number will go down.  It is possible for it to go negative.  Once it is equal or less than 
     * zero, no more objects can be added.  
     */
    public long getSize() { return _size; }

    /**
     * return the name of the cache volume where the space is located
     */
    public String getVolumeName() { return vol.getName(); }

    /**
     * use some of the reserved space to save a data object to the cache.  This will update the size 
     * of this Reservation as well as update the cache's inventory with the provided data.  Note that 
     * while the provided input stream will be drained, the caller is responsible for closing it after
     * this method returns.
     * @param from    the input stream to read the object from 
     * @param id      the cache-independent identifier to associate with this object (so that 
     *                it can be found again).
     * @param objname a cache-specific name to give to the saved object.  
     * @param metadata  metadata to associate with the saved object
     */
    public synchronized void saveAs(InputStream from, String id, String objname)
        throws CacheManagementException
    {
        this.saveAs(from, id, objname, null);
    }

    /**
     * use some of the reserved space to save a data object to the cache.  This will update the size 
     * of this Reservation as well as update the cache's inventory with the provided data.  Note that 
     * while the provided input stream will be drained, the caller is responsible for closing it after
     * this method returns.
     * @param from    the input stream to read the object from 
     * @param id      the cache-independent identifier to associate with this object (so that 
     *                it can be found again).
     * @param objname a cache-specific name to give to the saved object.  
     * @param metadata  metadata to associate with the saved object
     */
    public synchronized void saveAs(InputStream from, String id, String objname, JSONObject metadata)
        throws CacheManagementException
    {
        long size = -1L;
        if (metadata != null && metadata.has("size")) {
            try { size = metadata.getLong("size"); }
            catch (JSONException ex) { }
        }
        CountingInputStream is = new CountingInputStream(from);
        try {
            vol.saveAs(is, objname);

            if (size >= 0 && is.count() < size) {
                // wrote fewer bytes than expected; assume something went wrong
                vol.remove(objname);
                throw new CacheManagementException("Too few bytes written for "+id+"; "+
                                                   Long.toString(is.count())+" < "+Long.toString(size));
            }
            _size -= is.count();
            updateReservationSize(_size);

            if (metadata != null) {
                if (size != is.count())
                    metadata.put("size", is.count());
            }
            db.addObject(id, vol.getName(), objname, metadata);
        }
        catch (CacheVolumeException ex) {
            throw new CacheManagementException("Problem saving object, id="+id+": "+ex.getMessage(), ex);
        }
        catch (InventoryException ex) {
            // abort the save: remove the object
            vol.remove(objname);
            throw new CacheManagementException("Problem updating inventory for id="+id+": "+
                                               ex.getMessage()+" (aborted save)", ex);
        }
    }

    /**
     * update the entry for the reservation in the inventory database
     */
    protected void updateReservationSize(long newsize) throws InventoryException {
        JSONObject md = new JSONObject();
        md.put("size", newsize);
        db.updateMetadata(vol.getName(), this.name, md);
    }

    class CountingInputStream extends InputStream {
        private InputStream dep = null;
        private long bytesread = 0L;
        private int _incr = 0;
        public CountingInputStream(InputStream in) { dep = in; }
        public boolean markSupported() { return false; }
        public int read() throws IOException {
            _incr = dep.read();
            bytesread += 1;
            return _incr;
        }
        public int read(byte[] b) throws IOException {
            _incr = dep.read(b);
            bytesread += _incr;
            return _incr;
        }
        public int read(byte[] b, int off, int len) throws IOException {
            _incr = dep.read(b, off, len);
            bytesread += _incr;
            return _incr;
        }
        public void close() throws IOException { dep.close(); }
        public long count() { return bytesread; }
    }
}
