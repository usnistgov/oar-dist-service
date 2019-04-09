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

import gov.nist.oar.distrib.web.objects.BundleDownloadPlan;

/**
 * DataPackager interface declares the methods to be used when data download
 * requests are made.
 * 
 * @author Deoyani Nandrekar-Heinis
 *
 */
public interface DataPackager {

    public int writeData(ZipOutputStream zp) throws DistributionException;

    void validateRequest() throws DistributionException, IOException, InputLimitException;

    long getTotalSize() throws IOException;

    int getFilesCount() throws IOException;

    void validateBundleRequest() throws DistributionException, IOException, InputLimitException;

    void validateInput() throws IOException;

    boolean validateUrl(String url) throws DistributionException, IOException, InputLimitException;
}
