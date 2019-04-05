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
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import gov.nist.oar.distrib.service.DataPackagingService;
import gov.nist.oar.distrib.service.DefaultDataPackagingService;
import gov.nist.oar.distrib.web.objects.BundleRequest;
import gov.nist.oar.distrib.web.ErrorInfo;
import gov.nist.oar.distrib.web.objects.FileRequest;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import springfox.documentation.annotations.ApiIgnore;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ApiResponse;

/**
 * @author Deoyani Nandrekar-Heinis
 *
 */
@RestController
@Api
public class DataBundleAccessController {

    Logger logger = LoggerFactory.getLogger(DataBundleAccessController.class);

    @Autowired
    DataPackagingService df;

    /**
     * download a bundle of data files requested
     * 
     * @param jsonarray
     *            of type { "filePath":"", "downloadUrl":""}
     * @param response
     *            the output HTTP response object, used to write the output data
     * @throws DistributionException
     *             catches all exceptions and throws as distribution service
     *             exception
     * @throws InputLimitException
     * 
     */
    @ApiResponses(value = { @ApiResponse(code = 200, message = "Bundle download request is successful."),
	    @ApiResponse(code = 400, message = "Malformed request."),
	    @ApiResponse(code = 403, message = "Download not allowed"),
	    @ApiResponse(code = 500, message = "There is some error in distribution service") })
    @ApiOperation(value = "stream  compressed bundle of data requested", nickname = "get bundle of files", notes = "download files specified in the filepath fiels with associated location/url where it is downloaded.")
    @PostMapping(value = "/ds/_bundle", consumes = "application/json", produces = "application/zip")
    public void getbundlewithname(@Valid @RequestBody BundleRequest bundleRequest,
	    @ApiIgnore HttpServletResponse response, @ApiIgnore Errors errors)
	    throws DistributionException, InputLimitException, IOException {

	String bundleName = "download";

//	try {
	    //df.validateRequest(bundleRequest);
//	 if (bundleRequest.getBundleName() != null && !bundleRequest.getBundleName().isEmpty()) {
//		bundleName = bundleRequest.getBundleName();
//	    }
	    
//	} catch (DistributionException | IOException e) {
//
//	    throw new ServiceSyntaxException(e.getMessage());
//	}

	try {
	    if (bundleRequest.getBundleName() != null && !bundleRequest.getBundleName().isEmpty()) {
		bundleName = bundleRequest.getBundleName();
	    }
	    response.setHeader("Content-Type", "application/zip");
	    response.setHeader("Content-Disposition", "attachment;filename=\"" + bundleName + " \"");
	    ZipOutputStream zout = new ZipOutputStream(response.getOutputStream());

	    df.getBundledZipPackage(bundleRequest, zout);

	    zout.close();
	    response.flushBuffer();
	    logger.info("Data bundled in zip delivered");
	}
	catch (org.apache.catalina.connector.ClientAbortException ex) {
	    logger.info("Client cancelled the download");

	    throw new DistributionException(ex.getMessage());
	} catch (IOException ex) {
	    logger.debug("IOException type: " + ex.getClass().getName());

	    // "Connection reset by peer" gets thrown if the user cancels the
	    // download
	    if (ex.getMessage().contains("Connection reset by peer")) {
		logger.info("Client cancelled download");
	    } else {
		logger.error("IO error while sending file, " + ": " + ex.getMessage());
		throw new DistributionException(ex.getMessage());
	    }
	}

    }

    @ExceptionHandler(ServiceSyntaxException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorInfo handleServiceSyntaxException(ServiceSyntaxException ex, HttpServletRequest req) {
	logger.info("Malformed input detected in " + req.getRequestURI() + "\n  " + ex.getMessage());
	return new ErrorInfo(req.getRequestURI(), 400, "Malformed input", "POST");
    }

    @ExceptionHandler(InputLimitException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorInfo handleInputLimitException(InputLimitException ex, HttpServletRequest req) {
	logger.info("Bundle size and number of files in the bundle have some limits." + req.getRequestURI() + "\n  "
		+ ex.getMessage());
	return new ErrorInfo(req.getRequestURI(), HttpStatus.FORBIDDEN.value(),
		"Number of files and total size of bundle has some limit.", "POST");
    }

    @ExceptionHandler(DistributionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleInternalError(DistributionException ex, HttpServletRequest req) {
	logger.info("Failure processing request: " + req.getRequestURI() + "\n  " + ex.getMessage());
	return new ErrorInfo(req.getRequestURI(), 500, "Internal Server Error", "POST");
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleStreamingError(DistributionException ex, HttpServletRequest req) {
	logger.info("Streaming failure during request: " + req.getRequestURI() + "\n  " + ex.getMessage());
	return new ErrorInfo(req.getRequestURI(), 500, "Internal Server Error", "POST");
    }
}
