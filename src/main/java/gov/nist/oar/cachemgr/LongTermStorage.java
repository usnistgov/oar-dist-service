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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.util.List;

/**
 * an interface for accessing files in long-term storage.  
 * <p>
 * Long-term storage is used to store the source data objects that can be put into a cache.
 * The expectation is that access to long-term storage is slower or otherwise less performant
 * than cache storage.  (Further, data objects may also be bundled into aggregating files--e.g. 
 * zipfiles; see {@link Restorer}.)  This interface provides a minimal interface for accessing
 * the files. 
 */
public interface LongTermStorage {

    /**
     * Given an exact file name in the storage, return an InputStream open at the start of the file.
     * The caller is responsible for closing the stream when finished with it.
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return InputStream open at the start of the file
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    InputStream openFile(String filename) throws FileNotFoundException, StorageStateException;

    /**
     * return true if a file with the given name exists in the storage 
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     */
    boolean exists(String filename) throws StorageStateException;

    /**
     * return the checksum for the given file
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return Checksum, a container for the checksum value
     * @throws FileNotFoundException  if the file with the given filename does not exist
     * @throws UnsupportedOperationException   if checksums are not supported on this storage system
     */
    Checksum getChecksum(String filename) throws FileNotFoundException, StorageStateException;

    /**
     * Return the size of the named file in bytes
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return long, the size of the file in bytes.
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    long getSize(String filename) throws FileNotFoundException, StorageStateException;
}

