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
 */
package gov.nist.oar.distrib.service;

import gov.nist.oar.distrib.StreamHandle;
import gov.nist.oar.distrib.FileDescription;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.ResourceNotFoundException;

import java.io.FileNotFoundException;
import java.util.List;

/**
 * Service interface for downloading data products from the repository
 * 
 */
public interface FileDownloadService {

    /**
     * Return the filepaths of data files available from the dataset with a given identifier
     *
     * @param dsid      the dataset identifier for the desired dataset
     * @param version   the version of the dataset.  If null, the latest version is returned.
     * @throws ResourceNotFoundException   if the dsid is not recognized or there is no such version
     *                                        available for the dataset with dsid.
     * @throws DistributionException       if an internal error has occurred
     */
    public List<String> listDataFiles(String dsid, String version) 
        throws ResourceNotFoundException, DistributionException;

    /**
     * Download the data file with the given filepath
     *
     * @param dsid    the dataset identifier for the desired dataset
     * @param filepath  the path within the dataset to the desired file
     * @param version   the version of the dataset.  If null, the latest version is returned.
     * @return StreamHandle - an open stream to the file data accompanied by file metadata (like 
     *                  content length, type, checksum).
     * @throws ResourceNotFoundException   if the dsid is not recognized or there is no such version
     *                                        available for the dataset with dsid.
     * @throws FileNotFoundException       if the filepath is not found in the requested version of 
     *                                        the identified dataset
     * @throws DistributionException       if an internal error has occurred
     */
    public StreamHandle getDataFile(String dsid, String filepath, String version)
        throws ResourceNotFoundException, DistributionException, FileNotFoundException;

    /**
     * Describe the data file with the given filepath.  The returned information includes the 
     * file size, type, and checksum information.  
     *
     * @param dsid      the dataset identifier 
     * @param filepath  the path within the dataset to the desired file
     * @param version   the version of the dataset.  If null, the latest version is returned.
     * @throws ResourceNotFoundException   if the dsid is not recognized or there is no such version
     *                                        available for the dataset with dsid.
     * @throws FileNotFoundException       if the filepath is not found in the requested version of 
     *                                        the identified dataset
     * @throws DistributionException       if an internal error has occurred
     */
    public FileDescription getDataFileInfo(String dsid, String filepath, String version)
        throws ResourceNotFoundException, DistributionException, FileNotFoundException;
}
