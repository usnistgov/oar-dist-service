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
package gov.nist.oar.distrib.datapackage;

import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.zip.ZipOutputStream;

import gov.nist.oar.distrib.DistributionException;

/**
 * an interface for writing out a collection of files into a single data package .  Currently 
 * this package (also refered to as a bundle) is realized as a zip file.  
 * <p>
 * Generally, a class that implements this interface encapsulates at construction time the list 
 * of files that are to included in the package, along with the constraints/configuration for 
 * how it should be done.  In other words, a DataPackager implementation is created for a 
 * specific packaging request; it is not generally reused for a different request for a different
 * set of files.  The data is bunlded via the {@link getData} method, and once this function is 
 * called, its work is over.  
 * <p>
 *
 * @author Deoyani Nandrekar-Heinis
 */
public interface DataPackager {

    /**
     * Write the data files in sequence out to the given zip file. 
     * @param zp    the output stream for the zip file
     * @throws DistributionException   if there is any error while preparing to write this data; this 
     *                       can include if the packaging request violates configured policy (e.g. 
     *                       of total file size).
     * @throws IOException   if there is an error while sending bytes to the output stream.
     */
    public void getData(ZipOutputStream zp) throws DistributionException, IOException;

    /**
     * Return the number of bytes expected to be written to the output zip file stream.  This 
     * method may require examining the files (either on a filesystem or across the network) to 
     * determine their sizes.  The size returned may be approximate as it may not account for 
     * packaging overhead.  
     * @return long -- the size of the output zip file in bytes
     * @throws IOException -- if a non-recoverable error occurs while examining the files
     */
    public long getTotalSize() throws IOException;

    
    /***
     * return the requested name for the output zip file.  This interface does not actually assign
     * the name; rather, this method is for the benefit of the creator of the output zip file to 
     * advise as to the desired name for the file.  
     * @return String -- the recommended name for the output zip file
     * @throws IOException
     */
    public String getBundleName() throws IOException;

    /**
     * validate the client's request (provided at construction time) for compliance with 
     * policies and limits.  The method may require examining the files (either on a filesystem or 
     * across the network) in order to, for example, determine their sizes.  If the request is 
     * invalid, an exception will be thrown.
     * @throws InputLimitException -- if the request exceeds the packager's configured limits on 
     *                                the output bundle file (such as for size).  
     * @throws DistributionException -- if the request violates any other policy restrictions, it can 
     *                                not include any of the requested files in a bundle, or if a
     *                                failure occurs with the underlying distribution infrastructure.
     * @throws IOException -- if a non-recoverable error occurs while examining the files
     */
    public void validateBundleRequest() throws DistributionException, IOException;

    /**
     * Check if a given URL can be included as the source of a data file for a data bundle.  
     * @param url   the URL to check
     * @return boolean -- False if the input is not a legal URL or it violates some policy 
     *                    regarding data sources (such as whether it is from an allowed location).  
     */
    public boolean validateUrl(String url);
}
