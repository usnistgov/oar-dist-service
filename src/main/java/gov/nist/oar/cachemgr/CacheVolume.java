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
package gov.nist.oar.cachemgr;

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;

/**
 * an interface to cache storage.  
 * 
 * A CacheVolume represents storage for holding data objects, file-like chunk 
 * of data that can be read via an InputStream of limited length.  
 * Implementations hide the details of interacting with the underlying storage, 
 * including how the data is organized in that storage.  
 * 
 * Through this interface, callers can put, retrieve, and remove data objects 
 * from the storage.  The exists() function tells you if a named object is 
 * actually stored in the volume, but it does not provide search capabilities 
 * (as this is handled by a 
 * {@link gov.nist.oar.cachemgr.StorageInventoryDB StorageInventoryDB} class).  
 */
public interface CacheVolume {

    /**
     * return the identifier or name assigned to this volume.  If null is returned, 
     * the name is not known.
     */
    public String getName();

    /**
     * return True if an object with a given name exists in this storage volume
     * @param name  the name of the object
     * @throws CacheVolumeException   if there is an error accessing the 
     *              underlying storage system.
     */
    public boolean exists(String name) throws CacheVolumeException;

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
    public void saveAs(InputStream from, String name) throws CacheVolumeException;

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
     *               or if the request calls for copying an object to itself.  
     */
    public void saveAs(CacheObject obj, String name) throws CacheVolumeException;

    /**
     * return an open InputStream to the object with the given name
     * @param name   the name of the object to get
     * @throws ObjectNotFoundException  if the named object does not exist in this 
     *                                     volume
     * @throws CacheVolumeException     if there is any other problem opening the 
     *                                     named object
     */
    public InputStream getStream(String name) throws CacheVolumeException;

    /**
     * return a reference to an object in the volume given its name
     * @param name   the name of the object to get
     * @throws ObjectNotFoundException  if the named object does not exist in this 
     *                                     volume
     * @throws CacheVolumeException     if there is any other problem opening the 
     *                                     named object
     */
    public CacheObject get(String name) throws CacheVolumeException;

    /** 
     * remove the object with the give name from this storage volume
     * @param name       the name of the object to get
     * @returns boolean  True if the file existed in the volume; false if it was 
     *                       not found in this volume
     * @throws CacheVolumeException     if there is an internal error while trying to 
     *                                     remove the Object
     */
    public boolean remove(String name) throws CacheVolumeException;

    /**
     * return a URL that the object with the given name can be alternatively 
     * read from.  This allows for a potentially faster way to deliver a file
     * to web clients than via a Java stream copy.  Not all implementations may
     * support this. 
     * @param name       the name of the object to get
     * @returns URL      a URL where the object can be streamed from
     * @throws CacheVolumeException     if there is an internal error while trying to 
     *                                     remove the Object
     * @throws UnsupportedOperationException  if this operation is not supported on this volume
     */
    public URL getRedirectFor(String name) throws CacheVolumeException, UnsupportedOperationException;
}
