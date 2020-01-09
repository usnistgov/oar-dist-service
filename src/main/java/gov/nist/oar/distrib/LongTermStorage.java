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
package gov.nist.oar.distrib;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.util.List;

/**
 * an interterface for accessing files in long-term, read-only storage.
 * <p>
 * This interface is provides a common interface for accessing storage.  Not only may the detailed 
 * API to the underlying storage may differ (e.g. AWS S3 versus local file systems) but the conventions 
 * for how data is organized on the systems may differ.  This interface hides those difference.  
 * <p>
 * Files are stored in a storage system with a filename that is unique only to that instance of the 
 * storage system; a file and its metadata can be access via that file name.  Generally any kind of file 
 * can be stored in the system, but a the main purpose of the storage system is to store "archive 
 * information packages" (AIPs).
 * <p>
 * The {@link gov.nist.oar.distrib.storage storage} sub-package provides different implementations of 
 * this interface.
 * 
 * @see gov.nist.oar.distrib.BagStorage
 */
public interface LongTermStorage {

    /**
     * return a name for the storage system.  This is used primarily for enhancing error messages
     * by indicating which storage system produced the error.
     */
    public String getName();

    /**
     * Given an exact file name in the storage, return an InputStream open at the start of the file.
     * The caller is responsible for closing the stream when finished with it.
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return InputStream open at the start of the file
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    public InputStream openFile(String filename) throws FileNotFoundException, StorageVolumeException;

    /**
     * return true if a file with the given name exists in the storage 
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     */
    public boolean exists(String filename) throws StorageVolumeException;

    /**
     * return the checksum for the given file
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return Checksum, a container for the checksum value, or null if the checksum is not known
     *                   for this particular file.
     * @throws FileNotFoundException  if the file with the given filename does not exist
     * @throws UnsupportedOperationException   if checksums are not supported on this storage system
     */
    public Checksum getChecksum(String filename) throws FileNotFoundException, StorageVolumeException;

    /**
     * Return the size of the named file in bytes
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return long, the size of the file in bytes.
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    public long getSize(String filename) throws FileNotFoundException, StorageVolumeException;
}

