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
import org.springframework.http.HttpStatus;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.datapackage.InputLimitException;
import gov.nist.oar.distrib.datapackage.EmptyBundleRequestException;
import gov.nist.oar.distrib.datapackage.DataPackager;
import gov.nist.oar.distrib.datapackage.NoContentInPackageException;
import gov.nist.oar.distrib.datapackage.NoFilesAccesibleInPackageException;
import gov.nist.oar.distrib.service.DataPackagingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import gov.nist.oar.distrib.datapackage.BundleRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
/**
 * @author Deoyani Nandrekar-Heinis
 *
 */
@RestController
@Tag(name="Get Bundles ", description = "Download a set of files bundled into a zip file.")
public class DataBundleAccessController {

    Logger logger = LoggerFactory.getLogger(DataBundleAccessController.class);

    @Autowired
    DataPackagingService dpService;

    /**
     * download a bundle of data files requested
     * 
     * @param jsonarray of type { "filePath":"", "downloadUrl":""}
     * @param response  the output HTTP response object, used to write the output
     *                  data
     * @throws DistributionException catches all exceptions and throws as
     *                               distribution service exception
     * @throws InputLimitException
     * 
     */
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description="Bundle download request is successful."),
                            @ApiResponse(responseCode = "400", description="Malformed request."),
                            @ApiResponse(responseCode = "403", description="Download not allowed"),
                            @ApiResponse(responseCode = "500", description="There is some error in distribution service") })
    @Operation(summary = "stream  compressed bundle of data requested",
               description = "download files specified in the filepath fiels with associated location/url where it is downloaded.")
    @PostMapping(value = "/ds/_bundle", consumes = "application/json")
    public void getBundle(@Valid @RequestBody BundleRequest bundleRequest,
                          @Parameter(hidden = true) HttpServletResponse response,
                          @Parameter(hidden = true) Errors errors,
                          @Parameter(hidden = true) HttpServletRequest request) throws DistributionException
    {
        ZipOutputStream zout = null;
        try {
            logger.info("Data bundled in zip requested: " + bundleRequest.getBundleName());
            DataPackager dataPackager = dpService.getDataPackager(bundleRequest);
            dataPackager.validateBundleRequest();
            zout = new ZipOutputStream(response.getOutputStream());
            response.setHeader("Content-Type", "application/zip");
            response.setHeader("Content-Disposition", "attachment;filename=\"" + dataPackager.getBundleName() + " \"");
            System.out.println("Requester address:"+request.getRemoteAddr());
            dataPackager.setRequestedAddr(request.getRemoteAddr());
            dataPackager.getData(zout);
            response.flushBuffer();
            zout.close();

            logger.info("Data bundled in zip delivered: " + bundleRequest.getBundleName() + ","
                        + bundleRequest.getBundleSize());
            // logger.info("Data bundled in zip delivered."+dataPackager.getBundleName());

        } catch (org.apache.catalina.connector.ClientAbortException ex) {
            logger.info("Client cancelled the download");
            logger.error(ex.getMessage());
            throw new DistributionException(ex.getMessage());
        } catch (IOException ex) {
            logger.debug("IOException type: " + ex.getClass().getName());
            logger.error("IOException in getBundle" + ex.getMessage());
            // "Connection reset by peer" gets thrown if the user cancels the
            // download
            if (ex.getMessage().contains("Connection reset by peer")) {
                logger.info("Client cancelled download");
            } else {
                logger.error("IO error while sending file, " + ": " + ex.getMessage());
                throw new DistributionException(ex.getMessage());
            }
        } catch (EmptyBundleRequestException ex) {
            logger.warn("Empty bundle request sent");
            throw new ServiceSyntaxException("Bundle Request has empty list of files and urls", ex);
        } finally {
            if (zout != null) {
                try {
                    zout.close();
                } catch (IOException e) {
                    logger.error("Error while closing the output ZipOutputStream: " + e.getMessage());
                    throw new DistributionException("Zip output stream close error: " + e.getMessage(), e);
                }
            }
        }

    }

    @ExceptionHandler(ServiceSyntaxException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorInfo handleServiceSyntaxException(ServiceSyntaxException ex, HttpServletRequest req) {
        return createErrorInfo(req, 400, "Malformed input", "Malformed input detected in ", ex.getMessage());

    }

    @ExceptionHandler(NoContentInPackageException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorInfo handleServiceSyntaxException(NoContentInPackageException ex, HttpServletRequest req) {
        return createErrorInfo(req, 404, "There is no content in the package.", "Malformed input detected in ",
                               ex.getMessage());

    }

    @ExceptionHandler(NoFilesAccesibleInPackageException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorInfo handleServiceSyntaxException(NoFilesAccesibleInPackageException ex, HttpServletRequest req) {
        return createErrorInfo(req, 502, "No files could be accessed successfully.", 
                               "There are no files successfully accessed ", ex.getMessage());

    }

    @ExceptionHandler(InputLimitException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ResponseBody
    public ErrorInfo handleInputLimitException(InputLimitException ex, HttpServletRequest req) {
        return createErrorInfo(req, HttpStatus.FORBIDDEN.value(),
                               "Number of files and total size of bundle has some limit.", 
                               "Bundle size and number of files in the bundle have some limits.", ex.getMessage());
    }

    @ExceptionHandler(DistributionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleInternalError(DistributionException ex, HttpServletRequest req) {
        return createErrorInfo(req, 500, "Internal Server Error", "Failure processing request: ",
                               ex.getMessage());
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleStreamingError(DistributionException ex, HttpServletRequest req) {
        return createErrorInfo(req, 500, "Internal Server Error", "Streaming failure during request: ",
                               ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)

    public ErrorInfo handleStreamingError(RuntimeException ex, HttpServletRequest req) {

        return createErrorInfo(req, 500, "Unexpected Server Error", "Unexpected failure during request: ",
                               ex.getMessage());
    }

    /**
     * Create Error Information object to be returned to the client as a result of failed request
     * 
     * @param req         the request object the resulted in an error
     * @param errorcode   the HTTP status code to return
     * @param pubMessage  the message to return to the client
     * @param logMessage  a message to record in the log
     * @param exception   the message from the original exception that motivates this error response
     * @return ErrorInfo  the object to return to the client
     */
    protected ErrorInfo createErrorInfo(HttpServletRequest req, int errorcode, String pubMessage, 
                                        String logMessage, String exception)
    {
        String URI = "unknown";
        String method = "unknown";
        try {
            if (req != null) {
                URI = req.getRequestURI();
                method = req.getMethod();
            }
            logger.error(logMessage + " " + URI + " " + exception);
        } catch (Exception ex) {
            logger.error("Exception while processing error. " + ex.getMessage());
        }
        return new ErrorInfo(URI, errorcode, pubMessage, method);
    }
}
