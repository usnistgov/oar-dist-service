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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.HandlerMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.bags.preservation.BagUtils;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.FileDescription;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StreamHandle;
import gov.nist.oar.distrib.service.FileDownloadService;
import gov.nist.oar.distrib.service.NerdmDownloadService;
import gov.nist.oar.distrib.service.PreservationBagService;
import gov.nist.oar.distrib.service.CacheEnabledFileDownloadService;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * A web service controller that provides access to downloadable data products
 * from a PDR dataset
 * <p>
 * In the OAR PDR model, downloadable data products are organized into datasets,
 * with each dataset having a unique identifier. (Datasets can be recognized via
 * multiple identifiers.) Each individual data product is identified within a
 * dataset by a hierarchical <em>file path</em> (e.g.
 * <tt>borides/zirconium/data.json</tt>). To download a data product, then, one
 * needs to know the dataset's identifier and the product's filepath. Dataset
 * products can also be downloaded packaged in to <em>archive information
 * package (AIP) files</em> (this is how the products are stored in long term
 * storage). The full collection of data products in a dataset (including their
 * metadata and ancillary data) are stored in one or more AIP files. Currently,
 * AIPs are in the form of serialized <em>preservation bags</em>--files
 * representing BagIt-formatted data packages. of the
 * <p>
 * This controller provides provides primarily REST access URLs for individual
 * data products (via its dataset identifier and the product's filepath).
 * Because of how the service web API is organized, this controller also
 * provides info about the AIP files associated with a dataset. Actual
 * downloading of the AIP files is provided via the
 * {@link gov.nist.oar.distrib.web.AIPAccessController AIPAccessController}.
 */
@Controller
@Tag(name = "Download Files API",
     description = " These API endpoints allow access to different data products provided by NIST public data repository.")
@RequestMapping(value = "/ds")
public class DatasetAccessController {

    Logger logger = LoggerFactory.getLogger(DatasetAccessController.class);

    @Autowired
    PreservationBagService pres;

    @Autowired
    FileDownloadService downl;

    @Autowired
    NerdmDownloadService nerdmService;

    @Value("${distrib.baseurl}")
    String svcbaseurl;
    
    private final ObjectMapper mapper = new ObjectMapper();

    // TODO test inputs

    // NOTE: The order of the methods with @RequestMapping/@GetMapping matters!

    /**
     * return a list of descriptions of AIP files available for a given ID. Each
     * description (delivered within a JSON array) will have a <tt>name</tt>
     * property that gives the name of the available AIP file and (if it is
     * accessible) a <tt>downloadURL</tt> property that can be used to retrieve the
     * AIP file.
     * 
     * @param dsid the dataset identifier
     * @return List<FileDescription> - this list of descriptions.
     * @throws ResourceNotFoundException if the given ID does not exist
     * @throws DistributionException     if an internal error occurs
     */
    @Operation(summary = "List descriptions of available AIP files",
               description = "Each item in the returned JSON array describes an AIP file available for the dataset its identifier")
    @GetMapping(value = "/{dsid}/_aip", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<FileDescription> describeAIPs(@PathVariable("dsid") String dsid)
        throws ResourceNotFoundException, DistributionException
    {
        String fileInfo = "List descriptions of available AIP files: ";
        checkDatasetID(dsid);

        List<String> bags = pres.listBags(dsid);
        List<FileDescription> info = new ArrayList<FileDescription>(bags.size());
        FileDescription aip = null;
        for (String bag : bags) {
            try {
                aip = pres.getInfo(bag);
                addDownloadURL(aip, bag);
                info.add(aip);
                fileInfo += aip.name + "," + aip.contentLength + "\n";
            } catch (FileNotFoundException ex) {
                logger.error("Unable to get file information for bag, " + bag + " (skipping...)");
            }
        }
        logger.info(fileInfo);
        return info;
    }

    /**
     * List the versions of the AIP that are available for this dataset.
     * <p>
     * There can be one or more versions of the AIP available, each identified by a
     * version string. This function will list the versions available for a dataset
     * with a given ID.
     * 
     * @param dsid the dataset identifier
     * @return List<String> - this list of descriptions.
     * @throws ResourceNotFoundException if the given ID does not exist
     * @throws DistributionException     if an internal error occurs
     */
    @Operation(summary = "List the AIP versions available for a dataset",
               description = "Each item in the returned JSON array is a version string for AIP versions available for this dataset")
    @GetMapping(value = "/{dsid}/_aip/_v", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<String> listAIPVersions(@PathVariable("dsid") String dsid)
        throws ResourceNotFoundException, DistributionException
    {
        logger.info("List Versions:" + dsid);
        checkDatasetID(dsid);
        return pres.listVersions(dsid);
    }

    /**
     * describe the AIP files that are associated specifically with a given version
     * of the AIP
     * <p>
     * There can be one or more versions of the AIP available, each identified by a
     * version string. This function will describe the files are specifically
     * associated with a given version. Note that, in general, this is not a
     * complete list of the AIPS file for the version of the dataset; the complete
     * set can include all of the files from previous versions, too.
     *
     * @param dsid the dataset identifier
     * @param ver  the version string
     * @return List<FileDescription> - this list of descriptions.
     * @throws ResourceNotFoundException if the given ID does not exist
     * @throws DistributionException     if an internal error occurs
     */
    @Operation(summary = "List descriptions of AIP files available for a particular version of the dataset",
               description = "Each item in the returned JSON array describes an AIP file available for the dataset its identifier")
    @GetMapping(value = "/{dsid}/_aip/_v/{ver}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<FileDescription> describeAIPsForVersion(@PathVariable("dsid") String dsid,
                                                        @PathVariable("ver") String ver)
        throws ResourceNotFoundException, DistributionException
    {
        String versionDescription = " Describe versions:";
        checkDatasetID(dsid);

        if (ver.equals("latest")) {
            List<String> versions = pres.listVersions(dsid);
            if (versions.size() == 0)
                ver = "0";
            else {
                versions.sort(BagUtils.versionComparator());
                ver = versions.get(versions.size() - 1);
            }
        }

        List<String> bags = pres.listBags(dsid);
        List<FileDescription> info = new ArrayList<FileDescription>(bags.size());
        FileDescription aip = null;
        for (String bag : bags) {
            try {
                aip = pres.getInfo(bag);
                if (!ver.equals(aip.getStringProp("sinceVersion")))
                    continue;
                addDownloadURL(aip, bag);
                info.add(aip);
                versionDescription += aip.name + "," + aip.contentType + "\n";
            } catch (FileNotFoundException ex) {
                logger.error("Unable to get file information for bag, " + bag + " (skipping...)");
            }
        }
        if (info.size() == 0)
            throw ResourceNotFoundException.forID(dsid, ver);

        logger.info(versionDescription);
        return info;
    }

    /**
     * Describe the head AIP (or "head bag") for the given version of the AIP.
     * <p>
     * The head bag contains all of the metadata for the AIP, a listing of the bags
     * that make up its version of the AIP, and a lookup file for finding specific
     * data products.
     * 
     * @param dsid the dataset identifier
     * @param ver  the version string. If ver is null or equals "latest",
     *             information for the latest version will be returned.
     * @return List<FileDescription> - this list of descriptions.
     * @throws ResourceNotFoundException if the given ID does not exist
     * @throws DistributionException     if an internal error occurs
     */
    @Operation(summary = "describe the \"head\" AIP (also called the \"head bag\") for the given version of the AIP",
               description = "The returned JSON object describes the head bag for the dataset, including the URL for downloading it")
    @GetMapping(value = "/{dsid}/_aip/_v/{ver}/_head", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public FileDescription describeHeadAIPForVersion(@PathVariable("dsid") String dsid, @PathVariable("ver") String ver)
        throws ResourceNotFoundException, DistributionException
    {
        checkDatasetID(dsid);

        if (ver == null || ver.equals("latest")) {
            List<String> versions = pres.listVersions(dsid);
            if (versions.size() == 0)
                ver = "0";
            else {
                versions.sort(BagUtils.versionComparator());
                ver = versions.get(versions.size() - 1);
            }
        }
        String headbag = pres.getHeadBagName(dsid, ver);
        logger.info("Describe/version head bag:" + headbag);
        try {
            return pres.getInfo(headbag);
        } catch (FileNotFoundException ex) {
            logger.error("Bad bagname, " + headbag + ", returned for version=" + ver + ", id=" + dsid);
            throw new DistributionException("No info found for head bag, " + headbag);
        }
    }

    /**
     * Describe the head AIP (or "head bag") for the latest version of the AIP.
     * <p>
     * The head bag contains all of the metadata for the AIP, a listing of the bags
     * that make up its version of the AIP, and a lookup file for finding specific
     * data products.
     * 
     * @param dsid the dataset identifier
     * @return List<FileDescription> - this list of descriptions.
     * @throws ResourceNotFoundException if the given ID does not exist
     * @throws DistributionException     if an internal error occurs
     */
    @Operation(summary = "describe the \"head\" AIP (also called the \"head bag\") for the given version of the AIP",
               description = "The returned JSON object describes the head bag for the dataset, including the URL for downloading it")
    @GetMapping(value = "/{dsid}/_aip/_head", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public FileDescription describeLatestHeadAIP(@PathVariable("dsid") String dsid)
        throws ResourceNotFoundException, DistributionException
    {
        logger.info("Describe head bag:" + dsid);
        return describeHeadAIPForVersion(dsid, null);
    }

    private void addDownloadURL(FileDescription aip, String bagname) {
        if (svcbaseurl != null && !svcbaseurl.equals("") && bagname != null) {
            try {
                // set a download URL for the file being described as a property
                URL dlurl = new URL(svcbaseurl);
                dlurl = new URL(dlurl, "_aip/" + bagname);
                aip.setProp("downloadURL", dlurl.toString());
            } catch (MalformedURLException ex) {
                // can happen if svcbaseurl is malformed
                logger.error("Not interpretable as a base URL: " + svcbaseurl);
            }
        }
    }

    /**
     * dwonload a data file (via its full ARK ID).
     *
     * @param dsid     the dataset identifier
     * @param naan     the ARK ID's naming authority number (NAAN)
     * @param request  the input HTTP request object
     * @param response the output HTTP response object, used to write the output
     *                 data
     * @throws ResourceNotFoundException if the given ID does not exist
     * @throws FileNotFoundException     if the file does not exist in the dataset
     *                                   with given ID
     * @throws DistributionException     if an internal service error occurs
     * @throws IOException               if an error occurs while streaming the data
     *                                   to the client
     */
    @Operation(summary = "stream the data product with the given name",
               description = "This supports using the full ARK ID as the dataset identifier")
    @GetMapping(value = "/ark:/{naan:\\d+}/{dsid}/**", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public void downloadFileViaARK(@PathVariable("dsid") String dsid, @PathVariable("naan") String naan,
                                   @Parameter(hidden = true) HttpServletRequest request,
                                   @Parameter(hidden = true) HttpServletResponse response,
                                   @RequestParam Optional<String> requestId)
        throws ResourceNotFoundException, FileNotFoundException, DistributionException, IOException
    {
        logger.debug("Matched ARK ID for download: ark:/" + naan + "/" + dsid);

        String filepath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        filepath = filepath.substring("/ds/ark:/".length() + naan.length() + dsid.length() + 2);

        String ver = null;
        if (filepath.startsWith("_v/")) {
            filepath = filepath.substring(3);
            int i = filepath.indexOf('/');
            if (i >= 0)
                filepath = filepath.substring(i + 1);
        }

        downloadFile(dsid, filepath, ver, response);
    }

    /**
     * download a data file information (via header fields). This method responds to
     * HEAD requests on a downloadable file.
     * 
     * @param dsid     the dataset identifier
     * @param naan     the ARK ID's naming authority number (NAAN)
     * @param request  the input HTTP request object
     * @param response the output HTTP response object, used to write the output
     *                 data
     * @throws ResourceNotFoundException if the given ID does not exist
     * @throws FileNotFoundException     if the file does not exist in the dataset
     *                                   with given ID
     * @throws DistributionException     if an internal service error occurs
     * @throws IOException               if an error occurs while streaming the data
     *                                   to the client
     */
    @Operation(summary = "return info (via the HTTP header) about a downloadable file with a given ARK ID",
               description = "Like all HEAD requests, this returns only the header that would be returned by a GET call to the given path")
    @RequestMapping(value = "/ark:/{naan:\\d+}/{dsid}/**", method = RequestMethod.HEAD, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public void downloadFileInfoViaARK(@PathVariable("dsid") String dsid, @PathVariable("naan") String naan,
                                       @Parameter(hidden = true) HttpServletRequest request,
                                       @Parameter(hidden = true) HttpServletResponse response)
        throws ResourceNotFoundException, FileNotFoundException, DistributionException
    {
        logger.debug("Matched ARK ID for info: ark:/" + naan + "/" + dsid);

        String filepath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        filepath = filepath.substring("/ds/ark:/".length() + naan.length() + dsid.length() + 2);

        String ver = null;
        if (filepath.startsWith("_v/")) {
            filepath = filepath.substring(3);
            int i = filepath.indexOf('/');
            if (i >= 0)
                filepath = filepath.substring(i + 1);
        }

        downloadFileInfo(dsid, filepath, ver, response);
    }

    /**
     * Handles download requests for a specific file or folder within a dataset.
     * <p>
     * If the requested path ends with a slash, it is treated as a folder and an
     * HTML index page is returned listing the contents of that folder.
     * If the path refers to a file, the file is streamed directly to the client.
     * </p>
     *
     * @param dsid     the dataset identifier
     * @param request  the incoming HTTP request (used to extract the full file path
     *                 and version)
     * @param response the HTTP response used to stream the file content if
     *                 applicable
     * @param model    the Spring MVC model used for directory index rendering
     * @throws ResourceNotFoundException if the dataset ID or file is not found
     * @throws FileNotFoundException     if the file is missing within the dataset
     * @throws DistributionException     if an internal service or IO error occurs
     * @throws IOException               if an error occurs during streaming or file
     *                                   resolution
     */
    @Operation(summary = "Download a data file or list a dataset folder's contents", 
               description = "Streams the requested data file from a dataset, or returns an HTML folder listing if a directory is requested (path ends with a slash).")
    @GetMapping("/{dsid:[^a][^r][^k][^:].*}/**")
    public Object downloadFile(@PathVariable("dsid") String dsid,
                               HttpServletRequest request,
                               HttpServletResponse response,
                               @RequestParam Optional<String> requestId,
                               Model model)
        throws ResourceNotFoundException, FileNotFoundException, DistributionException, IOException
    {

        // Reconstruct raw path from the URI (after /ds/{dsid})
        String requestURI = request.getRequestURI(); // e.g. /od/ds/mds1491/folder/
        String contextPath = request.getContextPath(); // e.g. /od
        String basePath = contextPath + "/ds/" + dsid;
        String fullPath = requestURI.substring(basePath.length()); // e.g. folder/ or folder/file.txt

        if (fullPath.startsWith("/"))
            fullPath = fullPath.substring(1);

        String version = null;
        if (fullPath.startsWith("_v/")) {
            fullPath = fullPath.substring(3);
            int i = fullPath.indexOf('/');
            if (i >= 0) {
                version = fullPath.substring(0, i);
                fullPath = fullPath.substring(i + 1);
            }
        }

        // CASE 1: Request is for a directory → return HTML index page
        if (requestURI.endsWith("/")) {
            // Load nerdm.json
            JsonNode components = loadComponents(dsid);

            // List entries
            String subdir = fullPath.endsWith("/") ? fullPath.substring(0, fullPath.length() - 1) : fullPath;
            List<FileInfo> entries = listDirectoryEntries(components, subdir);
            long dirs = entries.stream().filter(FileInfo::isDirectory).count();
            long files = entries.size() - dirs;

            model.addAttribute("files", entries);
            model.addAttribute("currentPath", fullPath.isEmpty() ? "" : fullPath + "/");
            model.addAttribute("dirCount", dirs);
            model.addAttribute("fileCount", files);

            return "rclone-index"; // Thymeleaf template
        }

        // CASE 2: Request is for a file → stream it
        downloadFile(dsid, fullPath, version, response);
        return null; // file streamed directly
    }

    /**
     * Populate a list of FileInfo objects representing the contents of a directory
     * in a dataset. The list is sorted by name (case insensitive).
     *
     * @param datasetDir   the directory where the dataset is stored
     * @param components   the list of components in the dataset
     * @param subdirectory the subdirectory within the dataset to list (or empty
     *                     string for root)
     * @return a list of FileInfo objects, each representing a file or subdirectory
     *         in the given subdirectory. The list is sorted by name (case
     *         insensitive).
     */
    private List<FileInfo> listDirectoryEntries(JsonNode components, String subdirectory) {
        String prefix = subdirectory == null || subdirectory.isEmpty() ? "" : subdirectory + "/";
        TreeMap<String, FileInfo> entriesMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (JsonNode component : components) {
            // Only include downloadable files (skip restricted or incomplete metadata)
            if (!hasType(component, "DataFile") || component.get("downloadURL") == null)
                continue;

            String filepath = component.path("filepath").asText(null);
            if (filepath == null || !filepath.startsWith(prefix))
                continue;

            String remainder = filepath.substring(prefix.length());
            if (remainder.isEmpty())
                continue;

            boolean isSubcollection = hasType(component, "Subcollection");

            String entryName;
            boolean isDirectory;

            if (isSubcollection) {
                entryName = remainder.endsWith("/") ? remainder : remainder + "/";
                isDirectory = true;
            } else if (remainder.contains("/")) {
                entryName = remainder.substring(0, remainder.indexOf("/") + 1);
                isDirectory = true;
            } else {
                entryName = remainder;
                isDirectory = false;
            }

            // Deduplicate
            entriesMap.computeIfAbsent(entryName, nameKey -> {
                String href = isDirectory ? nameKey : component.path("downloadURL").asText();
                return new FileInfo(nameKey, isDirectory, "", "", href); // no size/modified info
            });
        }

        return new ArrayList<>(entriesMap.values());
    }

    private boolean hasType(JsonNode component, String shortType) {
        for (JsonNode t : component.path("@type")) {
            String txt = t.asText();
            int idx = txt.lastIndexOf(':');
            String local = idx >= 0 ? txt.substring(idx + 1) : txt;
            if (shortType.equals(local)) {
                return true;
            }
        }
        return false;
    }

    private JsonNode loadComponents(String dsid)
        throws ResourceNotFoundException, DistributionException
    {

        JsonNode doc = fetchNerdmDoc(dsid);
        if (doc == null)
            throw new ResourceNotFoundException("NERDm metadata not found for " + dsid);

        JsonNode comps = doc.path("components");
        return comps.isArray() ? comps : mapper.createArrayNode();
    }

    /**
     * Fetch the NERDm document for the specified dataset identifier.
     *
     * @param dsid the dataset identifier
     * @return the NERDm document as a {@link JsonNode}
     * @throws ResourceNotFoundException if the dataset is not found
     * @throws DistributionException     if an error occurs during fetching or
     *                                   parsing the NERDm document
     */

    private JsonNode fetchNerdmDoc(String dsid)
            throws ResourceNotFoundException, DistributionException {
        try {
            return nerdmService.fetchNerdm(dsid);
        } catch (IOException e) {
            throw new DistributionException("Error fetching or parsing NERDm for " + dsid, e);
        }
    }

    public static class FileInfo {
        private final String name;
        private final boolean directory;
        private final String size;
        private final String modified;
        private final String href;

        public FileInfo(String name, boolean isDirectory, String size, String modified, String href) {
            this.name = name;
            this.directory = isDirectory;
            this.size = size;
            this.modified = modified;
            this.href = href;
        }

        public String getName() {
            return name;
        }

        public boolean isDirectory() {
            return directory;
        }

        public String getSize() {
            return size;
        }

        public String getModified() {
            return modified;
        }

        public String getHref() {
            return href;
        }
    }

    /**
     * download a data file
     * 
     * @param dsid     the dataset identifier
     * @param filepath the path to the file within the dataset
     * @param version  the version of the dataset desired; if null, the latest
     *                 version is downloaded
     * @param response the output HTTP response object, used to write the output
     *                 data
     * @throws ResourceNotFoundException if the given ID does not exist
     * @throws FileNotFoundException     if the file does not exist in the dataset
     *                                   with given ID
     * @throws DistributionException     if an internal service error occurs
     * @throws IOException               if an error occurs while streaming the data
     *                                   to the client
     */
    public void downloadFile(String dsid, String filepath, String version, HttpServletResponse response)
        throws ResourceNotFoundException, FileNotFoundException, DistributionException, IOException
    {
        checkDatasetID(dsid);
        checkFilePath(filepath);

        if (logger.isInfoEnabled()) {
            String msg = "Data File requested: " + dsid + "/" + filepath;
            if (version != null)
                msg += " (version " + version + ")";
            logger.info(msg);
        }

        StreamHandle sh = null;
        try {
            try {
                CacheEnabledFileDownloadService cdls = (CacheEnabledFileDownloadService) downl;
                CacheObject co = cdls.findCachedObject(dsid, filepath, version);
                if (co != null && co.volume != null) {
                    URL redirect = cdls.redirectFor(co);
                    if (redirect != null) {
                        logger.info("Data File delivered via redirect: {},{}/{},{}",
                                    dsid, dsid, filepath, co.getSize());
                        response.sendRedirect(redirect.toString()); // sends as 302 FOUND
                        return;
                    }
                    sh = cdls.openStreamFor(co);
                }
            }
            catch (ClassCastException ex) { /* fall back on direct read */ }
            catch (CacheManagementException ex) {
                
                String file = filepath;
                if (version != null) file += "#" + version;
                logger.error("Trouble searching cache for data file: {}/{}: {}",
                             dsid, file, ex.getMessage());
                // pass through to fallback
            }
            catch (IOException ex) {
                // this can only come from sendRedirect()
                String file = filepath;
                if (version != null) file += "#" + version;
                logger.error("Trouble sending redirect response for {}/{}: {}",
                             dsid, file, ex.getMessage());
                return; 
            }

            if (sh == null)
                // fallback on direct download
                sh = downl.getDataFile(dsid, filepath, version);

            /*
             * Need encodeDigest implementation that converts hex to base64
             *
             * if (sh.getInfo().checksum != null) response.setHeader("Digest",
             * encodeDigest(sh.getInfo().checksum));
             */
            response.setHeader("Content-Length", Long.toString(sh.getInfo().contentLength));
            response.setHeader("Content-Type", sh.getInfo().contentType);
            response.setHeader("Content-Disposition",
                    "attachment;filename=\"" + Pattern.compile("/+").matcher(filepath).replaceAll("_") + "\"");

            int len;
            byte[] buf = new byte[100000];
            OutputStream out = response.getOutputStream();

            try {
                while ((len = sh.dataStream.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
                logger.info("Data File delivered: " + dsid + "," + dsid + "/" + filepath + "," +
                        Long.toString(sh.getInfo().contentLength));
                response.flushBuffer();
            } catch (org.apache.catalina.connector.ClientAbortException ex) {
                logger.info("Data File client canceled: " + filepath + "," + Long.toString(sh.getInfo().contentLength));
                // response.flushBuffer();
            } catch (IOException ex) {
                logger.info("Data File IOException: " + filepath + "," + Long.toString(sh.getInfo().contentLength));
                logger.debug("IOException type: " + ex.getClass().getName());

                // "Connection reset by peer" gets thrown if the user cancels
                // the download
                if (ex.getMessage().contains("Connection reset by peer")) {
                    logger.info("Client cancelled download");
                } else {
                    logger.error("IO error while sending file, " + filepath + ": " + ex.getMessage());
                    throw ex;
                }
            }
        } finally {
            if (sh != null)
                sh.close();
        }
    }

    /*
     * trigger an error response. This is normally disabled (commented out); it is
     * engaged only during development.
     * 
     * @param request the input HTTP request object
     * 
     * @param response the output HTTP response object, used to write the output
     * data
     * 
     * @throws IllegalStateException always
     * 
     * @Operation(value = "return (via the HTTP header) an HTTP error response",
     * nickname = "return error")
     * 
     * @RequestMapping(value = "/_error/**", method=RequestMethod.GET) public void
     * testErrorHandling(@Paramter(hidden=true) HttpServletRequest request,
     * 
     * @Paramter(hidden=true) HttpServletResponse response) throws ResourceNotFoundException,
     * FileNotFoundException, DistributionException { throw new
     * IllegalStateException("fake state"); }
     */

    /**
     * download a data file information (via header fields). This method responds to
     * HEAD requests on a downloadable file. In this implementation, the path to the
     * requested file and the desired version are parsed from the given request
     * object.
     * 
     * @param dsid     the dataset identifier
     * @param request  the input HTTP request object
     * @param response the output HTTP response object, used to write the output
     *                 data
     * @throws ResourceNotFoundException if the given ID does not exist
     * @throws FileNotFoundException     if the file does not exist in the dataset
     *                                   with given ID
     * @throws DistributionException     if an internal service error occurs
     * @throws IOException               if an error occurs while streaming the data
     *                                   to the client
     */
    @Operation(summary = "return info (via the HTTP header) about a downloadable file with a given name",
               description = "Like all HEAD requests, this returns only the header that would be returned by a GET call to the given path")
    @RequestMapping(value = "/{dsid:[^a][^r][^k][^:].*}/**", method = RequestMethod.HEAD, produces = MediaType.APPLICATION_JSON_VALUE)
    public void downloadFileInfo(@PathVariable("dsid") String dsid,
                                 @Parameter(hidden = true) HttpServletRequest request,
                                 @Parameter(hidden = true) HttpServletResponse response)
        throws ResourceNotFoundException, FileNotFoundException, DistributionException
    {
        String filepath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        filepath = filepath.substring("/ds/".length() + dsid.length() + 1);
        String ver = null;
        if (filepath.startsWith("_v/")) {
            filepath = filepath.substring(3);
            int i = filepath.indexOf('/');
            if (i >= 0)
                filepath = filepath.substring(i + 1);
        }

        downloadFileInfo(dsid, filepath, ver, response);
    }

    /**
     * download a data file information (via header fields). This method responds to
     * HEAD requests on a downloadable file.
     * 
     * @param dsid     the dataset identifier
     * @param filepath the path to the file within the dataset
     * @param version  the version of the dataset desired; if null, the latest
     *                 version is downloaded
     * @param response the output HTTP response object, used to write the output
     *                 data
     * @throws ResourceNotFoundException if the given ID does not exist
     * @throws FileNotFoundException     if the file does not exist in the dataset
     *                                   with given ID
     * @throws DistributionException     if an internal service error occurs
     * @throws IOException               if an error occurs while streaming the data
     *                                   to the client
     */
    public void downloadFileInfo(String dsid, String filepath, String version, HttpServletResponse response)
            throws ResourceNotFoundException, FileNotFoundException, DistributionException {
        checkDatasetID(dsid);
        checkFilePath(filepath);

        if (logger.isInfoEnabled()) {
            String msg = "Data fileinfo requested: " + dsid + "/" + filepath;
            if (version != null)
                msg += " (version " + version + ")";
            logger.info(msg);

        }
        FileDescription fi = downl.getDataFileInfo(dsid, filepath, version);
        logger.info("HeadRequest:" + dsid + "/" + filepath + "," + " (version " + version + "),"
                    + Long.toString(fi.contentLength));

        /*
         * Need encodeDigest implementation that converts hex to base64
         *
         * if (sh.getInfo().checksum != null) response.setHeader("Digest",
         * encodeDigest(sh.getInfo().checksum));
         */
        response.setHeader("Content-Length", Long.toString(fi.contentLength));
        response.setHeader("Content-Type", fi.contentType);
        response.setHeader("Content-Disposition",
                           "filename=\"" + Pattern.compile("/+").matcher(filepath).replaceAll("_") + "\"");

    }

    static Pattern baddsid = Pattern.compile("[\\s]");
    static Pattern badpath = Pattern.compile("(^\\.)|(/\\.)");

    /**
     * validate the dataset id, checking for illegal characters or sequences. If an
     * illegal form is detected a ServiceSyntaxException is raised
     */
    public void checkDatasetID(String dsid) {
        if (baddsid.matcher(dsid).find())
            throw new ServiceSyntaxException("dsid", dsid);
    }

    /**
     * validate the dataset id, checking for illegal characters or sequences. If an
     * illegal form is detected a ServiceSyntaxException is raised
     */
    public void checkFilePath(String path) {
        if (badpath.matcher(path).find())
            throw new ServiceSyntaxException("filepath", path);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorInfo> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest req) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON); // Ensure JSON response

        ErrorInfo errorInfo;
        if (ex.version == null) {
            errorInfo = createErrorInfo(req, 404, "Resource ID not found", "Non-existent resource requested: ", ex.getMessage());
        } else {
            errorInfo = createErrorInfo(req, 404, "Requested version of resource not found",
                                        "Non-existent resource version requested: ", ex.getMessage());
        }
        return new ResponseEntity<>(errorInfo, headers, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(FileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorInfo> handleFileNotFoundException(FileNotFoundException ex, HttpServletRequest req) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ErrorInfo errorInfo = createErrorInfo(req, 404, "File not found in requested dataset",
                                              "Non-existent file requested from resource: ", ex.getMessage());
        return new ResponseEntity<>(errorInfo, headers, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DistributionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorInfo> handleInternalError(DistributionException ex, HttpServletRequest req) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ErrorInfo errorInfo = createErrorInfo(req, 500, "Internal Server Error",
                "Failure processing request:", ex.getMessage());
        return new ResponseEntity<>(errorInfo, headers, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ServiceSyntaxException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorInfo> handleServiceSyntaxException(ServiceSyntaxException ex, HttpServletRequest req) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        logger.info("Malformed input detected in " + req.getRequestURI() + "\n  " + ex.getMessage());

        ErrorInfo errorInfo = createErrorInfo(req, 400, "Malformed input", "Invalid syntax: ", ex.getMessage());
        return new ResponseEntity<>(errorInfo, headers, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorInfo> handleStreamingError(IOException ex, HttpServletRequest req) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ErrorInfo errorInfo = createErrorInfo(req, 500, "Internal Server Error",
                                              "Streaming failure during request: ", ex.getMessage());
        return new ResponseEntity<>(errorInfo, headers, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorInfo> handleUnexpectedError(RuntimeException ex, HttpServletRequest req) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ErrorInfo errorInfo = createErrorInfo(req, 500, "Unexpected Server Error",
                                              "Unexpected failure during request: ", ex.getMessage());
        return new ResponseEntity<>(errorInfo, headers, HttpStatus.INTERNAL_SERVER_ERROR);
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
