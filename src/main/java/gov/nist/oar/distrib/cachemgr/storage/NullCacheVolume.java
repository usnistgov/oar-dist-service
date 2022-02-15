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
package gov.nist.oar.distrib.cachemgr.storage;

import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ObjectNotFoundException;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.HashSet;

import org.json.JSONObject;
import org.json.JSONException;

/**
 * an implementation of the CacheVolume interface throws away all data put into it.  The 
 * data is returned as empty streams.  This implementation is provided primarily for 
 * testing purposes.  It retains in memory a list of names of objects stored in it; thus,
 * when the instance is destroyed so are the objects in the volume.  
 */
public class NullCacheVolume implements CacheVolume {

    protected String name = null;
    protected HashSet<String> holdings = new HashSet<String>(5);

    /**
     * create the volume
     */
    public NullCacheVolume(String name) {
        this.name = name;
    }

    /**
     * return the identifier or name assigned to this volume.  If null is returned, 
     * the name is not known.
     */
    public String getName() { return name; }

    /**
     * return True if an object with a given name exists in this storage volume
     * @param name  the name of the object
     * @throws StorageVolumeException   if there is an error accessing the 
     *              underlying storage system.
     */
    @Override
    public boolean exists(String name) {
        return holdings.contains(name);
    }

    /**
     * add an object name to this volume.  With this implementation, this is equivalent to add an 
     * object to the volume via {@link #saveAs saveAs()}.  
     */
    public void addObjectName(String name) {
        holdings.add(name);
    }

    /**
     * save a copy of the named object to this storage volume.  This implementation 
     * throws away the bytes, but records the name as having been saved.  
     * @param from   an InputStream that contains the bytes the make up object 
     *                 to save
     * @param name   the name to assign to the object within the storage.  
     * @param md     the metadata to be associated with that object (can be null).  This 
     *                 parameter is ignored in this implementation.
     * @throws StorageVolumeException  if the method fails to save the object correctly.
     */
    public void saveAs(InputStream from, String name, JSONObject md) throws StorageVolumeException {
        byte[] buf = new byte[4096];
        try {
            while (from.read(buf) >= 0) { }
        } catch (IOException ex) {
            throw new StorageVolumeException(this.name+":"+name+": Trouble reading object: "+
                                           ex.getMessage(), ex);
        }
        addObjectName(name);
    }

    /**
     * save a copy of an object currently stored in another volume.  If an object 
     * already exists in the volume with this name, it will be replaced.  This 
     * allows for an implementation to invoke special optimizations for certain 
     * kinds of copies (e.g. S3 to S3).  
     * @param obj    an object in another storage volume.
     * @param name   the name to assign to the object within the storage.  
     * @throws ObjectNotFoundException  if the object does not exist in specified
     *                                     volume
     * @throws StorageVolumeException   if method fails to save the object correctly 
     *               or if the request calls for copying an object to itself or 
     *               if the given CacheObject is not sufficiently specified. 
     */
    public void saveAs(CacheObject obj, String name) throws StorageVolumeException {
        if (obj.name == null)
            throw new StorageVolumeException("name for cache object (in volume, "+obj.volname+
                                           ") not set.");
        if (obj.volume == null)
            throw new StorageVolumeException("Unable to locate volume, "+obj.volname+
                                           ", for cache object, "+obj.name);
        if (this.name.equals(obj.volname) && name.equals(obj.name))
            throw new StorageVolumeException("Request to copy "+obj.volname+":"+obj.name+
                                           " onto itself");
        if (! obj.volume.exists(obj.name))
            throw new ObjectNotFoundException(obj.volname, obj.name);

        try (InputStream is = obj.volume.getStream(obj.name)) {
            this.saveAs(is, name, obj.exportMetadata());
        }
        catch (IOException ex) {
            throw new StorageVolumeException("Trouble closing source stream while reading object "+obj.name);
        }
    }    

    /**
     * return an open InputStream to the object with the given name
     * @param name   the name of the object to get
     * @throws ObjectNotFoundException  if the named object does not exist in this 
     *                                     volume
     * @throws StorageVolumeException     if there is any other problem opening the 
     *                                     named object
     */
    public InputStream getStream(String name) throws StorageVolumeException {
        if (! this.exists(name))
            throw new ObjectNotFoundException(this.getName(), name);
        return new ByteArrayInputStream(new byte[0]);
    }

    /**
     * return a reference to an object in the volume given its name
     * @param name   the name of the object to get
     * @throws ObjectNotFoundException  if the named object does not exist in this 
     *                                     volume
     */
    public CacheObject get(String name) throws StorageVolumeException {
        if (! this.exists(name))
            throw new ObjectNotFoundException(this.getName(), name);
        return new CacheObject(name, this);
    }

    /** 
     * remove the object with the give name from this storage volume
     * @param name       the name of the object to get
     * @return boolean  True if the file existed in the volume; false if it was 
     *                       not found in this volume
     * @throws StorageVolumeException     if there is an internal error while trying to 
     *                                     remove the Object
     */
    public boolean remove(String name) throws StorageVolumeException {
        return holdings.remove(name);
    }

    /**
     * return a URL that th eobject with the given name can be alternatively 
     * read from.  This allows for a potentially faster way to deliver a file
     * to web clients than via a Java stream copy.  Not all implementations may
     * support this. 
     *
     * This implementation always throws an UnsupportedOperationException.
     *
     * @param name       the name of the object to get
     * @return URL      a URL where the object can be streamed from
     * @throws UnsupportedOperationException     always as this function is not supported
     */
    public URL getRedirectFor(String name) throws StorageVolumeException, UnsupportedOperationException {
        throw new UnsupportedOperationException("FilesystemCacheVolume: getRedirectFor not supported");
    }
}

    
