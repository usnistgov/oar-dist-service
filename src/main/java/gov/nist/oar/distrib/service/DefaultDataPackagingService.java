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

import org.springframework.beans.factory.annotation.Value;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.datapackage.BundleDownloadPlan;
import gov.nist.oar.distrib.datapackage.BundleRequest;
import gov.nist.oar.distrib.datapackage.DefaultDataPackager;
import gov.nist.oar.distrib.datapackage.DownloadBundlePlanner;
import gov.nist.oar.distrib.datapackage.InputLimitException;
import gov.nist.oar.distrib.web.InvalidInputException;

/**
 * This class implements the functionalities defined in DataPackagingService.
 * 
 * @author Deoyani Nandrekar-Heinis
 *
 */
public class DefaultDataPackagingService implements DataPackagingService {

    long maxFileSize = 0;
    int numOfFiles = 0;
    int allowedRedirects = 0;
    String domains;
    DownloadBundlePlanner dwnldPlanner;
    
    public DefaultDataPackagingService() {
	// Default constructor
    }

    /**
     * Parameterized constructor for creating service.
     * @param domains
     * @param maxFileSize
     * @param numOfFiles
     */
    public DefaultDataPackagingService(String domains, long maxFileSize, int numOfFiles, int allowedRedirects) {
	this.maxFileSize = maxFileSize;
	this.numOfFiles = numOfFiles;
	this.domains = domains;
	this.allowedRedirects = allowedRedirects;
    }

    /**
     * Get input request and return DataPackager.
     */
    @Override
    public DefaultDataPackager getDataPackager(BundleRequest br)
	    throws DistributionException {
	return new DefaultDataPackager(br, maxFileSize, numOfFiles, domains, allowedRedirects);
    }

    /*
     * ValidateBundled request, this includes checking input for json
     * validation, checking duplicates and checking size and files count.
     */
    @Override
    public void validateRequest(BundleRequest br) throws DistributionException, IOException, InputLimitException {
	DefaultDataPackager dp = new DefaultDataPackager(br, maxFileSize, numOfFiles, domains, allowedRedirects);
	dp.validateBundleRequest();
    }

    /*
     * Get the Plan for downloading requested data
     */
    @Override
    public BundleDownloadPlan getBundlePlan(BundleRequest br, String bundleName)
        throws DistributionException, InvalidInputException
    {
	dwnldPlanner = new DownloadBundlePlanner(br, maxFileSize, numOfFiles, domains, bundleName, allowedRedirects);
	return dwnldPlanner.getBundleDownloadPlan();
    }

}
