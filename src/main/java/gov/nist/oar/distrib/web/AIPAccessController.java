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
 */
package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.service.PreservationBagService;
import gov.nist.oar.distrib.FileDescription;
import gov.nist.oar.distrib.StreamHandle;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.DistributionException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Api;
import springfox.documentation.annotations.ApiIgnore;

/**
 * A web service controller that provides access to Archive Information Package (AIP) files.
 * <p>
 * In the OAR PDR model, an <em>archive information package</em> (AIP) is made up of one or more files.  
 * Together, these make up the AIP.  AIP files have unique names within the service; if one knows 
 * the name, one can access it.  In this service implementation, AIP files are in the form of 
 * serialized <em>preservation bags</em>--files representing BagIt-formatted data packages.  
 * <p>
 * This controller allows access to an AIP file based on its name, allowing download of the 
 * file itself or metadata about it (encoded in JSON).  It provides a web front end to bag file 
 * access provided by the 
 * {@link gov.nist.oar.distrib.service.PreservationBagService PreservationBagService}.  (Note 
 * that discovery of bag file names and head bags, courtesy 
 * of the {@link gov.nist.oar.distrib.service.PreservationBagService PreservationBagService}, is 
 * provided via the DatasetAccessController.)
 *
 * @author Raymond Plante
 */
@RestController
@Api
@RequestMapping(value = "/ds/_aip")
public class AIPAccessController {

    Logger logger = LoggerFactory.getLogger(AIPAccessController.class);

    @Autowired
    PreservationBagService pres;

    @Value("${distrib.baseurl}")
    String svcbaseurl;

    /**
     * stream out the AIP file to the web client.
     * @param name   the name of the desired AIP file
     * @param resp   the response object.  This will be used to set the response header and 
     *               stream the desired file.
     */
    @ApiOperation(value = "Return an AIP file (e.g. a preservation bag)", nickname = "getAIPFile",
                  notes = "return the AIP with the given name")
    @GetMapping(value = "/{name}")
    public void downloadAIP(@PathVariable("name") String name,
                            @ApiIgnore HttpServletResponse resp)
        throws ResourceNotFoundException, DistributionException, IOException
    {
        StreamHandle sh = null;
        try {
            sh = pres.getBag(name);

            if (sh.getInfo().contentType == null || sh.getInfo().contentType.equals(""))
                sh.getInfo().contentType = MediaType.APPLICATION_OCTET_STREAM.toString();

            logger.info("AIP requested: " + sh.getInfo().name);

            /*
             * Need encodeDigest implementat that converts hex to base64
             *
            if (sh.getInfo().checksum != null) 
                response.setHeader("Digest", encodeDigest(sh.getInfo().checksum));
             */
            resp.setHeader("Content-Length", Long.toString( sh.getInfo().contentLength ));
            resp.setHeader("Content-Type", sh.getInfo().contentType);
            resp.setHeader("Content-Disposition", "filename=\"" +
                           Pattern.compile("/+").matcher(name).replaceAll("_") + "\"");

            int len;
            byte[] outdata = new byte[100000];
            OutputStream out = resp.getOutputStream();

            try {
                while ((len = sh.dataStream.read(outdata)) != -1) {
                    out.write(outdata, 0, len);
                }
                resp.flushBuffer();
                logger.info("AIP delivered: " + sh.getInfo().name);
            }
            catch (org.apache.catalina.connector.ClientAbortException ex) {
                logger.info("Client cancelled the download");
                // resp.flushBuffer();
            }
            catch (IOException ex) {
                logger.debug("IOException type: "+ex.getClass().getName());

                // "Connection reset by peer" gets thrown if the user cancels the download
                if (ex.getMessage().contains("Connection reset by peer")) {
                    logger.info("Client cancelled download");
                } else {
                    logger.error("IO error while sending file, " + name +
                                 ": " + ex.getMessage());
                    throw ex;
                }
            }
        }
        catch (FileNotFoundException ex) {
            // send 404
            throw new ResourceNotFoundException("Failed to find AIP file, "+name);
        }
        finally {
            if (sh != null)
                sh.close();
        }
    }

    /**
     * return a description of the AIP with the given name
     * @param name   the name of the desired AIP file
     */
    @ApiOperation(value = "describe an AIP file (e.g. a preservation bag)", nickname = "describeAIPFile",
                  notes = "return a description of the AIP with the given name as a JSON object")
    @GetMapping(value = "/{name}/_info")
    public FileDescription describeAIP(@PathVariable("name") String name)
        throws ResourceNotFoundException, DistributionException
    {
        try {
            FileDescription out = pres.getInfo(name);
            if (svcbaseurl != null && ! svcbaseurl.equals("") && out.name != null) {
                try {
                    // set a download URL for the file being described as a property
                    URL dlurl = new URL(svcbaseurl);
                    dlurl = new URL(dlurl, "_aip/"+name);
                    out.setProp("downloadURL", dlurl.toString());
                } catch (MalformedURLException ex) {
                    // can happen if svcbaseurl is malformed
                    logger.error("Not interpretable as a base URL: "+svcbaseurl);
                }
            }
            return out;
        }
        catch (FileNotFoundException ex) {
            // return 404
            throw new ResourceNotFoundException("AIP file not found: "+name);
        }
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorInfo handleResourceNotFoundException(ResourceNotFoundException ex,
                                                     HttpServletRequest req)
    {
        // error is not specific to a version
        logger.info("Non-existent bag file requested: " + req.getRequestURI() +
                    "\n  " + ex.getMessage());
        return new ErrorInfo(req.getRequestURI(), 404, "AIP file not found");
    }

    @ExceptionHandler(DistributionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleInternalError(DistributionException ex,
                                         HttpServletRequest req)
    {
        logger.info("Failure processing request: " + req.getRequestURI() +
                    "\n  " + ex.getMessage());
        return new ErrorInfo(req.getRequestURI(), 500, "Internal Server Error");
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleStreamingError(DistributionException ex,
                                          HttpServletRequest req)
    {
        logger.info("Streaming failure during request: " + req.getRequestURI() +
                    "\n  " + ex.getMessage());
        return new ErrorInfo(req.getRequestURI(), 500, "Internal Server Error");
    }

}
