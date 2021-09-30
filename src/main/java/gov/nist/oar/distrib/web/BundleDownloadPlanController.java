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

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.datapackage.BundleDownloadPlan;
import gov.nist.oar.distrib.datapackage.BundleRequest;
import gov.nist.oar.distrib.service.DataPackagingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;

/**
 * BundleDownloadPlanController has api endpoint where client sends list of
 * requested files and urls. The _bundle_plan endpoint accepts this request in
 * the json format representing FilePathUrl object. The endpoint processes the
 * input request and create a plan in the form of DownloadBundlePlan. In this
 * plan list of files can be divided in several bundles if the total size of
 * bundle goes beyond allowed download limit. _bundle_plan endpoint returns the
 * json DownloadBundlePlan object, which contains the proposed bundle requests,
 * status of requests/urls, any warnings or messages. Client can use the plan to
 * request to _bundle enpoint to get actual data downloaded.
 * 
 * @author Deoyani Nandrekar-Heinis
 *
 */
@RestController
@Tag(name="Get Bundles Plan", description="Get the set of files in the form of bundle as per requested criteria.")
public class BundleDownloadPlanController {

    Logger logger = LoggerFactory.getLogger(BundleDownloadPlanController.class);
    @Autowired
    DataPackagingService df;

    /**
     * The controller api endpoint to accept list of requested files in json format
     * and return a plan to send requests to download files. once the request is
     * posted it is parsed and files are sorted
     * 
     * @param jsonObject of type BundleNameFilePathUrl
     * @param response
     * @param errors
     * @return JsonObject of type BundleDownloadPlan
     * @throws JsonProcessingException
     */
    
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Bundle download request is successful."),
	    @ApiResponse(responseCode = "400", description = "Malformed request."),
	    @ApiResponse(responseCode = "500", description = "There is some error in distribution service") })
    @Operation(summary = "Get the plan to download given list of files. ",
               description = "This api endpoint provides the information to client to how to divide request for "
                             + "number of files download if some limits are not met.")
    @PostMapping(value = "/ds/_bundle_plan", consumes = "application/json", produces = "application/json")
    public BundleDownloadPlan getbundlePlan(@Valid @RequestBody BundleRequest bundleRequest,
                                            @Parameter(hidden = true)  HttpServletResponse response,
                                            @Parameter(hidden = true)  Errors errors)
        throws DistributionException, InvalidInputException
    {
	String bundleName = "Download-data";
	if (bundleRequest.getBundleName() != null && !bundleRequest.getBundleName().isEmpty()) {
	    bundleName = bundleRequest.getBundleName();
	} else {
	    throw new InvalidInputException("The input is empty or invalid");
	}
//	DefaultDataPackagingService df = new DefaultDataPackagingService(this.validdomains, this.maxfileSize,
//		this.numofFiles, jsonObject, bundleName);
	response.setHeader("Content-Type", "application/json");
	return df.getBundlePlan(bundleRequest, bundleName);

    }

    /**
     * Exception thrown due to invalid input.
     * 
     * @param ex
     * @param req
     * @return
     */

    @ExceptionHandler(JsonProcessingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorInfo handleServiceSyntaxException(JsonProcessingException ex, HttpServletRequest req) {

	return this.createErrorInfo(req, 400, "Malformed input", "POST", "Malformed input detected in ",
		ex.getMessage());
    }

    /**
     * Invalid input exception
     * 
     * @param ex
     * @param req
     * @return
     **/
    @ExceptionHandler(InvalidInputException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorInfo handleStreamingError(InvalidInputException ex, HttpServletRequest req) {

	return this.createErrorInfo(req, 400, "Invalid input error", req.getMethod(),
		"There is an error processing input data: ", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorInfo handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest req) {
	return this.createErrorInfo(req, 404, "AIP file not found", "", "Non-existent bag file requested: ",
		ex.getMessage());
    }

    @ExceptionHandler(DistributionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleInternalError(DistributionException ex, HttpServletRequest req) {

	return this.createErrorInfo(req, 500, "Internal Server Error", "", "Failure processing request: ",
		ex.getMessage());
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleStreamingError(DistributionException ex, HttpServletRequest req) {
	return this.createErrorInfo(req, 500, "Internal Server Error", "", "Streaming failure during request: ",
		ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleStreamingError(RuntimeException ex, HttpServletRequest req) {

	return this.createErrorInfo(req, 500, "Unexpected Server Error", "", "Unexpected failure during request: ",
		ex.getMessage());
    }

    public ErrorInfo createErrorInfo(HttpServletRequest req, int errorcode, String pubMessage, String method,
	    String logMessage, String exception) {
	try {
	    String URI = "";
	    if (req.equals(null) || req == null)
		URI = "NULL";
	    else
		URI = req.getRequestURI();
	    logger.error(logMessage + " " + URI + " " + exception);
	    return new ErrorInfo(URI, errorcode, pubMessage, method);
	} catch (Exception ex) {
	    return new ErrorInfo("", errorcode, pubMessage, method);
	}
    }

}
