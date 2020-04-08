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

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.datapackage.InputLimitException;
import gov.nist.oar.distrib.web.InvalidInputException;
import gov.nist.oar.distrib.datapackage.DataPackager;
import gov.nist.oar.distrib.datapackage.DefaultDataPackager;
import gov.nist.oar.distrib.datapackage.BundleDownloadPlan;
import gov.nist.oar.distrib.datapackage.BundleRequest;
import gov.nist.oar.distrib.datapackage.FileRequest;

/**
 * Service interface for creating arbitrary bundles of data files from the repository.
 * <p>
 * With this service, a client can submit a request for a list of files to be bundled into 
 * a single file (currently, a zip file is the only supported file bundle type).  The service 
 * will validate that the request is valid and can collect the files together and write them
 * out into a package.  Generally, a service may have restrictions on the size of an output 
 * package; thus, the {@link getBundlePlang} method is provided to assist the client with meeting 
 * those restrictions.  
 * <p>
 * Generally, the service, once instantiated, can run for a long time, responding to many 
 * packaging requests.  The instantiation is expected to encapsulate a specific configuration, 
 * including policies like limits on the total size of a bundle.  A request of a specific bundle
 * is made to the {@link getDataPackager} method which returns a 
 * {@link gov.nist.oar.distrib.datapackage.DataPackager DataPackager} instance which handles that 
 * particular request.  
 * <p>
 * @author Deoyani Nandrekar-Heinis
 */
public interface DataPackagingService {
   
   /**
    * Return a packaging request handler for a particular request to bundle data.
    * @param br    the list of files that should be included in the desired data bundle
    * @return DataPackager -- the request handler instance
    * @throws DistributionException if there is a problem interpreting the request
    */
    public DataPackager getDataPackager(BundleRequest br) throws DistributionException;

    /**
     * Validate bundle/package download request for syntax, valid file references, and 
     * adherence to policies (e.g. limits on output bundle size).  This may require that the 
     * files in the request be examined (e.g. to get their sizes).
     * @param br    a bundle request, providing the list of desired files for a data bundle
     * @throws DistributionException  if there are errors interpreting request or accessing the 
     *                                  underlying data distribution infrastructure
     * @throws IOException            if there are errors while examining the files that are part 
     *                                  of the request
     * @throws InputLimitException    if the request exceeds configured limits allowed for output
     *                                  bundles.
     */
    public void validateRequest(BundleRequest br)
        throws DistributionException, IOException, InputLimitException;

    /**
     * Create a bundling plan for the input request.  The plan will split the set of files in the 
     * input request into a series of requests to be made (via {@link getDataPackager}) that are 
     * guaranteed to meet the services policies and restrictions (such as that a limit on the size 
     * of a single output bundle).  Generally, this method should not raise an exception due to 
     * problems with the input request (and the files it includes) as an assessment of this is 
     * typically included in the output plan.  
     * @param br    the list of files requested to be bundled.
     * @param bundleName   a preferred name to be given to the output bundle, assuming the request 
     *                     is not need to be split up.  If it does need to be split up, each request
     *                     in the returned plan will specify a specific name that is based on this
     *                     parameter value (such that each bundle name will be unique).
     * @return BundleDownloadPlan -- an object wrapping a list of bundle requests that constitute the
     *                     plan.
     * @throws DistributionException -- if there is an unexpected problem accessing the underlying 
     *                     distribution infrastructure.
     */
    public BundleDownloadPlan getBundlePlan(BundleRequest br, String bundleName)
        throws DistributionException, InvalidInputException;
}
