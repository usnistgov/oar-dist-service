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
package gov.nist.oar.distrib;

import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.zip.ZipOutputStream;

import gov.nist.oar.distrib.datapackage.BundleDownloadPlan;

/**
 * DataPackager interface declares the methods to be used when data download
 * requests are made.
 * 
 * @author Deoyani Nandrekar-Heinis
 *
 */
public interface DataPackager {

    /**
     * Returns the outpustream of bundled/packaged data
     * @param zp Zip Output stream
     * @throws DistributionException
     * @throws IOException
     */
    public void getData(ZipOutputStream zp) throws DistributionException, IOException;

    /**
     * Get total size of requested bundle
     * @return long
     * @throws IOException
     */
    long getTotalSize() throws IOException;

    
    /***
     * get Name of requested bundle
     * @return String
     * @throws IOException
     */
    String getBundleName() throws IOException;

    /**
     * Validate Request from syntax validation to content validation
     * @throws DistributionException
     * @throws IOException
     */
    void validateBundleRequest() throws DistributionException, IOException;

    /**
     * Check if URL is from valid domains
     * @param url
     * @return
     * @throws DistributionException
     * @throws IOException
     */
    boolean validateUrl(String url) throws DistributionException, IOException;
}
