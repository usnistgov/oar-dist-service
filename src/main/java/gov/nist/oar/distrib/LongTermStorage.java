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
 * @author: Deoyani Nandrekar-Heinis
 */
package gov.nist.oar.distrib;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.util.List;

import gov.nist.oar.distrib.StorageStateException;
import gov.nist.oar.distrib.ResourceNotFoundException;

/**
 * an interterface for accessing files--particularly preservation bags--in long-term storage.
 * <p>
 * This interface is provides a common interface for accessing storage.  Not only may the detailed 
 * API to the underlying storage may differ (e.g. AWS S3 versus local file systems) but the conventions 
 * for how data is organized on the systems may differ.  This interface hides those difference.  
 * <p>
 * Files are stored in a storage system with a filename that is unique only to that instance of the 
 * storage system; a file and its metadata can be access via that file name.  Generally any kind of file 
 * can be stored in the system, but a the main purpose of the storage system is to store "archive 
 * information packages" (AIPs); each one is identified with a repository-unique AIP identifier.  Each 
 * AIP is made up of one or more files, stored as preservation bags (using the BagIt standard).  To 
 * retrieve a bag, one must learn what it's file name is; methods like 
 * {@link #findBagsFor(String) findBagsFor()} and {@link #findHeadBagFor(String, String) findHeadBagFor()} 
 * can map an AIP ID to bag names.  In particular, each version of an AIP has, among its bags, a designated 
 * "head bag", which contains important AIP metadata and information about the other bags that are part of 
 * the AIP.  The {@link #findHeadBagFor(String, String) findHeadBagFor()} will return the filename for the 
 * serialized head bag for the AIP with a given identifier and version.  To determine the name of other 
 * (non-bag) types of files, one must have an "out-of-band" mechanism.
 * <p>
 * The {@link gov.nist.oar.distrib.storage storage} sub-package provides different implementations of 
 * this interface.
 * 
 * @author  Deoyani Nandrekar-Heinis
 */
public interface LongTermStorage {

    /**
     * Given an exact file name in the storage, return an InputStream open at the start of the file.
     * The caller is responsible for closing the stream when finished with it.
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
                         may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return InputStream open at the start of the file
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    InputStream openFile(String filename) throws FileNotFoundException, StorageStateException;

    /**
     * return the checksum for the given file
     * @param filename   The name of the desired file.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return Checksum, a container for the checksum value
     * @throws FileNotFoundException  if the file with the given filename does not exist
     * @throws UncheckedIOException   if an error occurs while retrieving the checksum
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

    /**
     * Return all the bags associated with the given ID
     * @param identifier  the AIP identifier for the desired data collection 
     * @return List<String>, the file names for all bags associated with given ID
     * @throws ResourceNotFoundException   if there exist no bags with the given identifier
     */
    List<String> findBagsFor(String identifier) throws ResourceNotFoundException, StorageStateException;

    /**
     * Return the head bag associated with the given ID
     * @param identifier  the AIP identifier for the desired data collection 
     * @return String, the head bag's file name
     * @throws ResourceNotFoundException   if there exist no bags with the given identifier
     */
    String findHeadBagFor(String identifier) throws ResourceNotFoundException, StorageStateException; 

    /**
     * Return the name of the head bag for the identifier for given version
     * @param identifier  the AIP identifier for the desired data collection 
     * @param version     the desired version of the AIP
     * @return String, the head bag's file name
     * @throws ResourceNotFoundException   if there exist no bags with the given identifier or version
     */
    String findHeadBagFor(String identifier, String version)
        throws ResourceNotFoundException, StorageStateException;
}
