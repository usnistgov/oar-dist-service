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
 * @author: Deoyani Nandrekar-Heinis
 */
package gov.nist.oar.cachemgr.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.oar.cachemgr.Checksum;
import gov.nist.oar.cachemgr.StorageVolumeException;
import gov.nist.oar.cachemgr.StorageStateException;
import gov.nist.oar.cachemgr.storage.LongTermStorageBase;

/**
 * An implementation of the LongTermStorage interface for accessing files from a locally mounted disk.
 * <p>
 * In this implementation file names are relative paths interpreted as the location of the file relative 
 * to a configured root directory.  A pre-calculated SHA-256 hash is expected to be stored in a file with 
 * the same name as the file it is a hash of but with a ".sha256" extension.  If the hash file is missing
 * and the file is not too large, a hash may be calculated on the fly.
 *
 * @see gov.nist.oar.cachemgr.LongTermStorage
 */
public class FilesystemLongTermStorage extends LongTermStorageBase {

    public static long defaultChecksumSizeLimit = 50000000L;  // 50 MB

    /**
     * the path to the filesystem directory below which its files are stored.
     */
    public final File rootdir;
    
    private String _name = null;

    private long checksumSizeLim = defaultChecksumSizeLimit;
    
    /**
     * create the storage instance (with a default Logger)
     * 
     * @param dirpath  the directory under which the data accessible to this instance are
     *                 located.
     * @throws FileNotFoundException   if the give path does not exist or is not a directory
     */
    public FilesystemLongTermStorage(String dirpath) throws FileNotFoundException {
        this(dirpath, null);
    }
    
    /**
     * create the storage instance
     * 
     * @param dirpath  the directory under which the data accessible to this instance are
     *                 located.
     * @param log      a Logger to use; if null, a default is created.
     * @throws FileNotFoundException   if the give path does not exist or is not a directory
     */
    public FilesystemLongTermStorage(String dirpath, Logger log) throws FileNotFoundException {
        this(dirpath, null, -1L, log);
    }
    
    /**
     * create the storage instance
     * 
     * @param dirpath  the directory under which the data accessible to this instance are
     *                 located.
     * @param name     a label that identifies this storage system (for, e.g., error messages)
     * @param log      a Logger to use; if null, a default is created.
     * @throws FileNotFoundException   if the give path does not exist or is not a directory
     */
    public FilesystemLongTermStorage(String dirpath, String name, Logger log) throws FileNotFoundException {
        this(dirpath, name, -1L, log);
    }
    
    /**
     * create the storage instance
     * 
     * @param dirpath  the directory under which the data accessible to this instance are
     *                 located.
     * @param name     a label that identifies this storage system (for, e.g., error messages)
     * @param csSizeLim  the file size limit up to which checksums will be calculated on the fly for 
     *                 a file if it is not cached on disk.  If zero, checksums will never be calculated 
     *                 on the fly.  If negative, a default value (50 MB) will be set.  
     * @param log      a Logger to use; if null, a default is created.
     * @throws FileNotFoundException   if the give path does not exist or is not a directory
     */
    public FilesystemLongTermStorage(String dirpath, String name, long csSizeLim, Logger log)
        throws FileNotFoundException
    {
        super(log);
        rootdir = new File(dirpath);
        if (! rootdir.isDirectory())
            throw new FileNotFoundException("Not an existing directory: "+dirpath);

        if (name == null) {
            logger.info("Creating FilesystemLongTermStorage rooted at {}", rootdir.toString());
            name = "Store:" + dirpath;
        }
        else
            logger.info("Creating FilesystemLongTermStorage, {}, rooted at {}", name, rootdir.toString());
        _name = name;

        if (csSizeLim < 0) csSizeLim = defaultChecksumSizeLimit;
        checksumSizeLim = csSizeLim;
    }
    
    /**
     * create the storage instance
     * 
     * @param dirpath  the directory under which the data accessible to this instance are
     *                 located.
     * @param csSizeLim  the file size limit up to which checksums will be calculated on the fly for 
     *                 a file if it is not cached on disk.  If zero, checksums will never be calculated 
     *                 on the fly.  If negative, a default value (50 MB) will be set.  
     * @param log      a Logger to use; if null, a default is created.
     * @throws FileNotFoundException   if the give path does not exist or is not a directory
     */
    public FilesystemLongTermStorage(String dirpath, long csSizeLim, Logger log) throws FileNotFoundException {
        this(dirpath, null, csSizeLim, log);
    }

    /**
     * return a name for the storage system.  This is used primarily for enhancing error messages
     * by indicating which storage system produced the error.
     */
    @Override
    public String getName() {  return _name;  }

    /**
     * Given an exact file name in the storage, return an InputStream open at the start of the file
     * The caller is responsible for closing the stream when finished with it.
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
                         may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return InputStream open at the start of the file
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    @Override
    public InputStream openFile(String filename) throws FileNotFoundException {
        return new FileInputStream(new File(this.rootdir, filename));
    }

    /**
     * return true if a file with the given name exists in the storage 
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     */
    @Override
    public boolean exists(String filename) {
        return (new File(rootdir, filename)).exists();
    }

    /**
     * return the checksum for the given file
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return Checksum, a container for the checksum value
     * @throws FileNotFoundException  if the file with the given filename does not exist
     * @throws UnsupportedOperationException   if an error occurs while retrieving the checksum
     */
    @Override
    public Checksum getChecksum(String filename) throws FileNotFoundException, StorageVolumeException {

        File dataf = new File(rootdir, filename);
        if (! exists(filename))
            throw new FileNotFoundException("File does not exist in storage: "+dataf.toString());

        File chksum = new File(rootdir, filename+".sha256");
        if (! chksum.isFile()) {
            // no cached checksum, calculate it the file is not too big
            if (! chksum.getName().endsWith(".sha256"))
                logger.warn("No cached checksum available for "+filename);
            if (getSize(filename) > checksumSizeLim)
                throw new StorageStateException("No cached checksum for large file: "+dataf.toString());
            try (InputStream is = openFile(filename)) {
                return Checksum.calcSHA256(is);
            } catch (Exception ex) {
                throw new StorageVolumeException("Unable to calculate checksum for small file: " + 
                                                dataf.toString() + ": " + ex.getMessage());
            }
        }

        try (FileReader csrdr = new FileReader(chksum)) {
            return Checksum.SHA256(readHash(csrdr));
        }
        catch (IOException ex) {
            throw new StorageVolumeException("Failed to read cached checksum value from "+ chksum.toString() +
                                             ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Return the size of the named file in bytes
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return long, the size of the file in bytes
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    @Override
    public long getSize(String filename) throws FileNotFoundException {
        File file = new File(this.rootdir, filename);
        if (! exists(filename))
            throw new FileNotFoundException("File does not exist in storage: "+file.toString());
        return file.length();
    }
}

