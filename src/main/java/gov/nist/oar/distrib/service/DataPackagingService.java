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
package gov.nist.oar.distrib.service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.core.JsonProcessingException;

import gov.nist.oar.distrib.DataPackager;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.InputLimitException;
import gov.nist.oar.distrib.datapackage.DefaultDataPackager;
import gov.nist.oar.distrib.web.objects.BundleDownloadPlan;
import gov.nist.oar.distrib.web.objects.BundleRequest;
import gov.nist.oar.distrib.web.objects.FileRequest;

/**
 * This service interface defines functions available to call datapackger for given request,
 * Get request validated and get the bundle plan for given request.
 * @author Deoyani Nandrekar-Heinis
 *
 */
public interface DataPackagingService {
   
   /**
    * Accepts the bundle request and return bundlepackager to access data
    * @param br BundleRequest 
    * @return DataPackager
    * @throws DistributionException
    */
    public DefaultDataPackager getDataPackager(BundleRequest br) throws DistributionException;

    /**
     * Validate Bundle/package download request and validate it for syntax, valid urls and allowed sizes
     * @param br
     * @throws DistributionException
     * @throws IOException
     * @throws InputLimitException
     */
    public void validateRequest(BundleRequest br) throws DistributionException, IOException, InputLimitException;

    /**
     * Create bundle plan for the input request and return plan with or without name provided
     * @param br
     * @param bundleName
     * @return
     * @throws JsonProcessingException
     */
    public BundleDownloadPlan getBundlePlan(BundleRequest br, String bundleName) throws JsonProcessingException;
}
