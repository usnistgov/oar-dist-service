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

import gov.nist.oar.bags.preservation.BagUtils;
import gov.nist.oar.distrib.service.PreservationBagService;
import gov.nist.oar.distrib.service.FileDownloadService;
import gov.nist.oar.distrib.FileDescription;
import gov.nist.oar.distrib.StreamHandle;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.DistributionException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Api;
import springfox.documentation.annotations.ApiIgnore;

/**
 * A web service controller that provides access to downloadable data products from a PDR dataset
 * <p>
 * In the OAR PDR model, downloadable data products are organized into datasets, with each dataset
 * having a unique identifier.  (Datasets can be recognized via multiple identifiers.)  Each 
 * individual data product is identified within a dataset by a hierarchical <em>file path</em> 
 * (e.g. <tt>borides/zirconium/data.json</tt>).  To download a data product, then, one needs to know 
 * the dataset's identifier and the product's filepath.  Dataset products can also be downloaded 
 * packaged in to <em>archive information package (AIP) files</em> (this is how the products are 
 * stored in long term storage).  The full collection of data products in a dataset (including their
 * metadata and ancillary data) are stored in one or more AIP files.  Currently, AIPs are in the form 
 * of serialized <em>preservation bags</em>--files representing BagIt-formatted data packages.  
 * of the
 * <p>
 * This controller provides provides primarily REST access URLs for individual data products (via 
 * its dataset identifier and the product's filepath).  Because of how the service web API is organized,
 * this controller also provides info about the AIP files associated with a dataset.  
 * Actual downloading of the AIP files is provided via the 
 * {@link gov.nist.oar.distrib.web.AIPAccessController AIPAccessController}.  
 */
@RestController
@Api
@RequestMapping(value = "/ds")
public class DatasetAccessController {

    Logger logger = LoggerFactory.getLogger(DatasetAccessController.class);

    @Autowired
    PreservationBagService pres;

    @Autowired
    FileDownloadService downl;

    @Value("${distrib.baseurl}")
    String svcbaseurl;

    // TODO test inputs

    /**
     * return a list of descriptions of AIP files available for a given ID.  Each 
     * description (delivered within a JSON array) will have a <tt>name</tt> property that 
     * gives the name of the available AIP file and (if it is accessible) a <tt>downloadURL</tt>
     * property that can be used to retrieve the AIP file.  
     * @param  dsid   the dataset identifier
     * @return List<FileDescription> - this list of descriptions.
     * @throws ResourceNotFoundException  if the given ID does not exist
     * @throws DistributionException      if an internal error occurs
     */
    @ApiOperation(value = "List descriptions of available AIP files", nickname = "List all bags",
                  notes = "Each item in the returned JSON array describes an AIP file available for the dataset its identifier")
    @RequestMapping(value = "/{dsid}/_aip", method = RequestMethod.GET)
    public List<FileDescription>  describeAIPs(@PathVariable("dsid") String dsid)
        throws ResourceNotFoundException, DistributionException
    {
        checkDatasetID(dsid);
        
        List<String> bags = pres.listBags(dsid);
        List<FileDescription> info = new ArrayList<FileDescription>(bags.size());
        FileDescription aip = null;
        for (String bag : bags) {
            try {
                aip = pres.getInfo(bag);
                addDownloadURL(aip, bag);
                info.add(aip);
            }
            catch (FileNotFoundException ex) {
                logger.error("Unable to get file information for bag, "+bag+" (skipping...)");
            }
        }
        return info;
    }

    /**
     * List the versions of the AIP that are available for this dataset.  
     * <p>
     * There can be one or more versions of the AIP available, each identified by a version 
     * string.  This function will list the versions available for a dataset with a given ID.
     * 
     * @param dsid   the dataset identifier
     * @return List<String> - this list of descriptions.
     * @throws ResourceNotFoundException  if the given ID does not exist
     * @throws DistributionException      if an internal error occurs
     */
    @ApiOperation(value = "List the AIP versions available for a dataset", nickname = "List versions",
                  notes = "Each item in the returned JSON array is a version string for AIP versions available for this dataset")
    @RequestMapping(value = "/{dsid}/_aip/_v", method = RequestMethod.GET)
    public List<String> listAIPVersions(@PathVariable("dsid") String dsid)
        throws ResourceNotFoundException, DistributionException
    {
        checkDatasetID(dsid);        
        return pres.listVersions(dsid);
    }

    /**
     * describe the AIP files that are associated specifically with a given version of the AIP
     * <p>
     * There can be one or more versions of the AIP available, each identified by a version 
     * string.  This function will describe the files are specifically associated with a 
     * given version.  Note that, in general, this is not a complete list of the AIPS file for 
     * the version of the dataset; the complete set can include all of the files from previous 
     * versions, too.
     *
     * @param  dsid   the dataset identifier
     * @param  ver    the version string
     * @return List<FileDescription> - this list of descriptions.
     * @throws ResourceNotFoundException  if the given ID does not exist
     * @throws DistributionException      if an internal error occurs
     */
    @ApiOperation(value = "List descriptions of AIP files available for a particular version of the dataset",
                  nickname = "Describe versions",
                  notes = "Each item in the returned JSON array describes an AIP file available for the dataset its identifier")
    @RequestMapping(value = "/{dsid}/_aip/_v/{ver}", method = RequestMethod.GET)
    public List<FileDescription>  describeAIPsForVersion(@PathVariable("dsid") String dsid,
                                                         @PathVariable("ver") String ver)
        throws ResourceNotFoundException, DistributionException
    {
        checkDatasetID(dsid);
        
        if (ver.equals("latest")) {
            List<String> versions = pres.listVersions(dsid);
            if (versions.size() == 0)
                ver = "0";
            else {
                versions.sort(BagUtils.versionComparator());
                ver = versions.get(versions.size()-1);
            }
        }
        
        List<String> bags = pres.listBags(dsid);
        List<FileDescription> info = new ArrayList<FileDescription>(bags.size());
        FileDescription aip = null;
        for (String bag : bags) {
            try {
                aip = pres.getInfo(bag);
                if (! ver.equals(aip.getStringProp("sinceVersion")))
                    continue;
                addDownloadURL(aip, bag);
                info.add(aip);
            }
            catch (FileNotFoundException ex) {
                logger.error("Unable to get file information for bag, "+bag+" (skipping...)");
            }
        }
        if (info.size() == 0)
            throw ResourceNotFoundException.forID(dsid, ver);
        
        return info;
    }

    /**
     * Describe the head AIP (or "head bag") for the given version of the AIP.  
     * <p>
     * The head bag contains all of the metadata for the AIP, a listing of the bags that make up 
     * its version of the AIP, and a lookup file for finding specific data products.
     * 
     * @param  dsid   the dataset identifier
     * @param  ver    the version string.  If ver is null or equals "latest", information for the 
     *                latest version will be returned.
     * @return List<FileDescription> - this list of descriptions.
     * @throws ResourceNotFoundException  if the given ID does not exist
     * @throws DistributionException      if an internal error occurs
     */
    @ApiOperation(value = "describe the \"head\" AIP (also called the \"head bag\") for the given version of the AIP",
                  nickname = "Describe head",
                  notes = "The returned JSON object describes the head bag for the dataset, including the URL for downloading it")
    @RequestMapping(value = "/{dsid}/_aip/_v/{ver}/_head", method = RequestMethod.GET)
    public FileDescription describeHeadAIPForVersion(@PathVariable("dsid") String dsid,
                                                    @PathVariable("ver") String ver)
        throws ResourceNotFoundException, DistributionException
    {
        checkDatasetID(dsid);

        if (ver == null || ver.equals("latest")) {
            List<String> versions = pres.listVersions(dsid);
            if (versions.size() == 0)
                ver = "0";
            else {
                versions.sort(BagUtils.versionComparator());
                ver = versions.get(versions.size()-1);
            }
        }
        
        String headbag = pres.getHeadBagName(dsid, ver);
        try {
            return pres.getInfo(headbag);
        }
        catch (FileNotFoundException ex) {
            logger.error("Bad bagname, "+headbag+", returned for version="+ver+", id="+dsid);
            throw new DistributionException("No info found for head bag, "+headbag);
        }
    }

    /**
     * Describe the head AIP (or "head bag") for the latest version of the AIP.  
     * <p>
     * The head bag contains all of the metadata for the AIP, a listing of the bags that make up 
     * its version of the AIP, and a lookup file for finding specific data products.
     * 
     * @param  dsid   the dataset identifier
     * @return List<FileDescription> - this list of descriptions.
     * @throws ResourceNotFoundException  if the given ID does not exist
     * @throws DistributionException      if an internal error occurs
     */
    @ApiOperation(value = "describe the \"head\" AIP (also called the \"head bag\") for the given version of the AIP",
                  nickname = "Describe head",
                  notes = "The returned JSON object describes the head bag for the dataset, including the URL for downloading it")
    @RequestMapping(value = "/{dsid}/_aip/_head", method = RequestMethod.GET)
    public FileDescription describeLatestHeadAIP(@PathVariable("dsid") String dsid)
        throws ResourceNotFoundException, DistributionException
    {
        return describeHeadAIPForVersion(dsid, null);
    }

    private void addDownloadURL(FileDescription aip, String bagname) {
        if (svcbaseurl != null && ! svcbaseurl.equals("") && bagname != null) {
            try {
                // set a download URL for the file being described as a property
                URL dlurl = new URL(svcbaseurl);
                dlurl = new URL(dlurl, "_aip/"+bagname);
                aip.setProp("downloadURL", dlurl.toString());
            } catch (MalformedURLException ex) {
                // can happen if svcbaseurl is malformed
                logger.error("Not interpretable as a base URL: "+svcbaseurl);
            }
        }
    }

    /**
     * download a data file
     * 
     * @param dsid      the dataset identifier
     * @param request   the input HTTP request object 
     * @param response  the output HTTP response object, used to write the output data
     * @throws ResourceNotFoundException  if the given ID does not exist
     * @throws FileNotFoundException      if the file does not exist in the dataset with given ID
     * @throws DistributionException      if an internal service error occurs
     * @throws IOException                if an error occurs while streaming the data to the client
     */
    @ApiOperation(value = "stream the data product with the given name", nickname = "get file",
                  notes = "download the file")
    @RequestMapping(value = "/{dsid}/**", method = RequestMethod.GET)
    public void downloadFile(@PathVariable("dsid") String dsid,
                             @ApiIgnore HttpServletRequest request,
                             @ApiIgnore HttpServletResponse response)
        throws ResourceNotFoundException, FileNotFoundException, DistributionException, IOException
    {
        checkDatasetID(dsid);

        String filepath = (String) request.getAttribute(
                                      HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        filepath = filepath.substring("/ds/".length() + dsid.length() + 1);

        String ver = null;
        if (filepath.startsWith("_v/")) {
            filepath = filepath.substring(3);
            int i = filepath.indexOf('/');
            if (i >= 0) filepath = filepath.substring(i+1);
        }
        checkFilePath(filepath);

        if (logger.isInfoEnabled()) {
            String msg = "Download requested for "+dsid+"/"+filepath;
            if (ver != null)
                msg += " (version "+ver+")";
            logger.info(msg);
        }
        StreamHandle sh = null;
        try {
            sh = downl.getDataFile(dsid, filepath, ver);

            /*
             * Need encodeDigest implementat that converts hex to base64
             *
            if (sh.getInfo().checksum != null) 
                response.setHeader("Digest", encodeDigest(sh.getInfo().checksum));
             */
            response.setHeader("Content-Length",      Long.toString(sh.getInfo().contentLength));
            response.setHeader("Content-Type",        sh.getInfo().contentType);
            response.setHeader("Content-Disposition", "filename=\"" +
                               Pattern.compile("/+").matcher(filepath).replaceAll("_") + "\"");

            int len;
            byte[] buf = new byte[100000];
            OutputStream out = response.getOutputStream();

            try {
                while ((len = sh.dataStream.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
                response.flushBuffer();
                logger.info("Data File delivered: " + filepath);
            }
            catch (org.apache.catalina.connector.ClientAbortException ex) {
                logger.info("Client cancelled the download");
                // response.flushBuffer();
            }
            catch (IOException ex) {
                logger.debug("IOException type: "+ex.getClass().getName());

                // "Connection reset by peer" gets thrown if the user cancels the download
                if (ex.getMessage().contains("Connection reset by peer")) {
                    logger.info("Client cancelled download");
                } else {
                    logger.error("IO error while sending file, " + filepath +
                                 ": " + ex.getMessage());
                    throw ex;
                }
            }
        }
        finally {
            if (sh != null) sh.close();
        }
    }

    static Pattern baddsid = Pattern.compile("[\\s]");
    static Pattern badpath = Pattern.compile("(^\\.)|(/\\.)");

    /**
     * validate the dataset id, checking for illegal characters or sequences.  If an illegal form 
     * is detected a ServiceSyntaxException is raised
     */
    public void checkDatasetID(String dsid) {
        if (baddsid.matcher(dsid).find())
            throw new ServiceSyntaxException("dsid", dsid);
    }

    /**
     * validate the dataset id, checking for illegal characters or sequences.  If an illegal form 
     * is detected a ServiceSyntaxException is raised
     */
    public void checkFilePath(String path) {
        if (badpath.matcher(path).find()) 
            throw new ServiceSyntaxException("filepath", path);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorInfo handleResourceNotFoundException(ResourceNotFoundException ex,
                                                     HttpServletRequest req)
    {
        if (ex.version == null) {
            // error is not specific to a version
            logger.info("Non-existent resource requested: " + req.getRequestURI() +
                        "\n  " + ex.getMessage());
            return new ErrorInfo(req.getRequestURI(), 404, "Resource ID not found");
        }
        else {
            // error is not specific to a version
            logger.info("Non-existent resource version requested: " + req.getRequestURI() +
                        "\n  " + ex.getMessage());
            return new ErrorInfo(req.getRequestURI(), 404, "Requested version of resource not found");
        }
    }

    @ExceptionHandler(FileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorInfo handleResourceNotFoundException(FileNotFoundException ex, HttpServletRequest req) {

        // error is not specific to a version
        logger.info("Non-existent file requested from resource: " + req.getRequestURI() +
                    "\n  " + ex.getMessage());
        return new ErrorInfo(req.getRequestURI(), 404, "File not found in requested dataset");
    }

    @ExceptionHandler(DistributionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleNotFoundException(DistributionException ex,
                                             HttpServletRequest req)
    {
        logger.info("Failure processing request: " + req.getRequestURI() +
                    "\n  " + ex.getMessage());
        return new ErrorInfo(req.getRequestURI(), 500, "Internal Server Error");
    }

    @ExceptionHandler(ServiceSyntaxException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorInfo handleServiceSyntaxException(ServiceSyntaxException ex,
                                                  HttpServletRequest req)
    {
        logger.info("Malformed input detected in " + req.getRequestURI() +
                    "\n  " + ex.getMessage());
        return new ErrorInfo(req.getRequestURI(), 400, "Malformed input");
    }    
}


