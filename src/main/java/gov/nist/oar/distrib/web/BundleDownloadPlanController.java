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
package gov.nist.oar.distrib.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.InputLimitException;
import gov.nist.oar.distrib.service.DefaultDataPackagingService;
import gov.nist.oar.distrib.web.objects.BundleDownloadPlan;
import gov.nist.oar.distrib.web.objects.BundleNameFilePathUrl;
import gov.nist.oar.distrib.web.objects.ErrorInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import springfox.documentation.annotations.ApiIgnore;

/**
 * BundleDownloadPlanController has api endpoint where client sends list of requested files and urls.
 * The _bundle_plan endpoint accepts this request in the json format representing FilePathUrl object.
 * The endpoint processes the input request and create a plan in the form of DownloadBundlePlan. 
 * In this plan list of files can be divided in several bundles if the total size of bundle goes
 * beyond allowed download limit. _bundle_plan endpoint returns the json DownloadBundlePlan object, 
 * which contains the proposed bundle requests, status of requests/urls, any warnings or messages.
 * Client can use the plan to request to _bundle enpoint to get actual data downloaded.
 * 
 * @author Deoyani Nandrekar-Heinis
 *
 */
@RestController
@Api
public class BundleDownloadPlanController {

    Logger logger = LoggerFactory.getLogger(BundleDownloadPlanController.class);

    @Value("${distrib.filesizelimit}")
    long maxfileSize;

    @Value("${distrib.numberoffiles}")
    int numofFiles;

    @Value("${distrib.validdomains}")
    String validdomains;

    @ApiOperation(value = "Get the plan to download given list of files. ", nickname = "get the plan to download data.", notes = "This api endpoint provides the information to client to how to divide request for number of files download "
	    + "if some limits are not met.")
    @PostMapping(value = "/ds/_bundle_plan", consumes = "application/json", produces = "application/json")

    public BundleDownloadPlan getbundlePlan(@Valid @RequestBody BundleNameFilePathUrl jsonObject,
	    @ApiIgnore HttpServletResponse response, Errors errors){

	String bundleName = "Download-data";
	if (jsonObject.getBundleName() != null && !jsonObject.getBundleName().isEmpty()) {
	    bundleName = jsonObject.getBundleName();
	}
	DefaultDataPackagingService df = new DefaultDataPackagingService(this.validdomains, this.maxfileSize,
		this.numofFiles, jsonObject, bundleName);
	response.setHeader("Content-Type", "application/json");
	return df.getBundlePlan();

    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleInternalError(DistributionException ex, HttpServletRequest req) {
	logger.info("Failure processing request: " + req.getRequestURI() + "\n  " + ex.getMessage());
	return new ErrorInfo(req.getRequestURI(), 500, "Internal Server Error", "POST");
    }

}
