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
package gov.nist.oar.cachemgr.storage;

import gov.nist.oar.cachemgr.CacheVolume;
import gov.nist.oar.cachemgr.CacheObject;
import gov.nist.oar.cachemgr.CacheVolumeException;
import gov.nist.oar.cachemgr.ObjectNotFoundException;

import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URL;

import org.apache.commons.io.FileUtils;

/**
 * an implementation of the CacheVolume interface assuming that the data is 
 * stored on a local disk.  
 *
 * The storage model has all data stored under a single directory.  Within that 
 * directory, objects are stored as files with the name as given to the addObject().
 * When that name includes a slash, the object file is stored in a subdirectory 
 * consistent with directory path implied by the name.  
 */
public class FilesystemCacheVolume implements CacheVolume {

    protected File root = null;
    protected String name = null;

    /**
     * create a FilesystemCacheVolume 
     * 
     * @param rootdir   the path to the root directory for the volume
     * @param name      a name to refer to this volume by (in exception messages)
     */
    public FilesystemCacheVolume(String rootdir, String name) throws FileNotFoundException {
        root = new File(rootdir);
        if (! root.exists())
            throw new FileNotFoundException(rootdir + ": directory not found");
        if (! root.isDirectory())
            throw new FileNotFoundException(rootdir + ": not found as a directory");
        if (name == null)
            name = rootdir;
        this.name = name;
    }

    /**
     * create a FilesystemCacheVolume.
     *
     * The volume's name (used in exception messages) will be set to the root directory path.
     * 
     * @param rootdir   the path to the root directory for the volume
     */
    public FilesystemCacheVolume(String rootdir) throws FileNotFoundException {
        this(rootdir, rootdir);
    }
    
    /**
     * return the identifier or name assigned to this volume.  If null is returned, 
     * the name is not known.
     */
    public String getName() { return name; }

    /**
     * return the identifier or name assigned to this volume.  If null is returned, 
     * the name is not known.
     */
    public File getRootDir() { return root; }

    /**
     * return True if an object with a given name exists in this storage volume
     * @param name  the name of the object
     * @throws CacheVolumeException   if there is an error accessing the 
     *              underlying storage system.
     */
    public boolean exists(String name) throws CacheVolumeException {
        File f = new File(root, name);
        return f.isFile();
    }

    /**
     * save a copy of the named object to this storage volume.  If an object 
     * already exists in the volume with this name, it will be replaced.  The 
     * actual name used in the native storage system may be different from this 
     * name; it is the responsibility of the CacheVolume implementation to 
     * determine the actual location for the given name.  
     * @param from   an InputStream that contains the bytes the make up object 
     *                 to save
     * @param name   the name to assign to the object within the storage.  
     * @throws CacheVolumeException  if the method fails to save the object correctly.
     */
    public synchronized void saveAs(InputStream from, String name) throws CacheVolumeException {
        try {
            FileUtils.copyToFile(from, new File(root, name));
        } catch (IOException ex) {
            throw new CacheVolumeException(this.name+":"+name+": Failed to save object: "+
                                           ex.getMessage(), ex);
        }
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
     * @throws CacheVolumeException   if method fails to save the object correctly 
     *               or if the request calls for copying an object to itself or 
     *               if the given CacheObject is not sufficiently specified. 
     */
    public synchronized void saveAs(CacheObject obj, String name) throws CacheVolumeException {
        if (obj.name == null)
            throw new CacheVolumeException("name for cache object (in volume, "+obj.volname+
                                           ") not set.");
        if (obj.volume == null)
            throw new CacheVolumeException("Unable to locate volume, "+obj.volname+
                                           ", for cache object, "+obj.name);
        if (this.name.equals(obj.volname) && name.equals(obj.name))
            throw new CacheVolumeException("Request to copy "+obj.volname+":"+obj.name+
                                           " onto itself");
        if (! obj.volume.exists(obj.name))
            throw new ObjectNotFoundException(obj.volname, obj.name);

        this.saveAs(obj.volume.getStream(obj.name), name);
    }

    /**
     * return an open InputStream to the object with the given name
     * @param name   the name of the object to get
     * @throws ObjectNotFoundException  if the named object does not exist in this 
     *                                     volume
     * @throws CacheVolumeException     if there is any other problem opening the 
     *                                     named object
     */
    public synchronized InputStream getStream(String name) throws CacheVolumeException {
        if (! this.exists(name))
            throw new ObjectNotFoundException(this.getName(), name);
        try {
            return new FileInputStream(new File(root, name));
        }
        catch (IOException ex) {
            throw new CacheVolumeException(this.name+":"+name+": Failed to open object: "+
                                           ex.getMessage(), ex);
        }
    }

    /**
     * return a reference to an object in the volume given its name
     * @param name   the name of the object to get
     * @throws ObjectNotFoundException  if the named object does not exist in this 
     *                                     volume
     */
    public CacheObject get(String name) throws CacheVolumeException {
        if (! this.exists(name))
            throw new ObjectNotFoundException(this.getName(), name);
        return new CacheObject(name, this);
    }

    /** 
     * remove the object with the give name from this storage volume
     * @param name       the name of the object to get
     * @returns boolean  True if the file existed in the volume; false if it was 
     *                       not found in this volume
     * @throws CacheVolumeException     if there is an internal error while trying to 
     *                                     remove the Object
     */
    public synchronized boolean remove(String name) throws CacheVolumeException {
        if (! this.exists(name))
            return false;

        File target = new File(root, name);
        try {
            target.delete();
        } catch (SecurityException ex) {
            throw new CacheVolumeException("Unexpected SecurityException: "+ex.getMessage(), ex);
        }
        
        return true;
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
     * @returns URL      a URL where the object can be streamed from
     * @throws UnsupportedOperationException     always as this function is not supported
     */
    public URL getRedirectFor(String name) throws CacheVolumeException, UnsupportedOperationException {
        throw new UnsupportedOperationException("FilesystemCacheVolume: getRedirectFor not supported");
    }


}
