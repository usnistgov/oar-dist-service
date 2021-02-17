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

import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheManager;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.VolumeNotFoundException;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.json.JSONObject;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Api;
import springfox.documentation.annotations.ApiIgnore;

/**
 * a web service controller that provides access to the distribution cache--its contents, its status,
 * and its operation--via its CacheManager.  
 */
@RestController
@Api
@RequestMapping(value="/cache")
public class CacheManagementController {

    Logger log = LoggerFactory.getLogger(CacheManagementController.class);

    PDRCacheManager mgr = null;

    @Autowired
    public CacheManagementController(CacheManagerProvider provider) throws ConfigurationException {
        if (provider != null && provider.canProvideManager())
            mgr = provider.getPDRCacheManager();
    }

    /**
     * a specialized exception indicating that a CacheManager is not currently in use.  This is used 
     * to inform clients to such if they try to access endpoints provided by this class.
     */
    class NotOperatingException extends CacheManagementException {
        NotOperatingException(String message) {
            super(message);
        }
        NotOperatingException() {
            this("A cache manager is not in operation at this time");
        }
    }

    private void _checkForManager() throws NotOperatingException {
        if (mgr == null)
            throw new NotOperatingException();
    }

    /**
     * indicate whether the cache manager is operating.  200 is returned if it is, 404 if it isn't.
     * @param req    the request object (from which the request URL is retrieved)
     */
    @ApiOperation(value="Return the status of the cache manager", nickname="is cache manager in use?",
                  notes="200 is returned if the cache manager is in operation, 404 otherwise.")
    @GetMapping(value="/")
    public ErrorInfo getStatus(@ApiIgnore HttpServletRequest req) throws NotOperatingException {
        _checkForManager();
        return new ErrorInfo(req.getRequestURI(), 200, "Cache Manager in Use");
    }

    /**
     * return a summary of the cache volumes
     */
    @ApiOperation(value="List summaries of the volumes in the cache", nickname="Summarize volumes",
                  notes="Each item in the returned JSON array summarizes the state of a volume within the cache")
    @GetMapping(value="/volumes/")
    public List<Object> summarizeVolumes() throws InventoryException, NotOperatingException {
        _checkForManager();
        return mgr.summarizeVolumes().toList();
    }

    /**
     * return a summary of a cach volume's metadata and stats
     */
    @ApiOperation(value="Return a summary of a volume in the cache", nickname="Summarize volume",
                  notes="Returns metadata and statistics about a volume and its contents")
    @GetMapping(value="/volumes/**")
    public Map<String, Object> summarizeVolume(@ApiIgnore HttpServletRequest request)
        throws VolumeNotFoundException, InventoryException, NotOperatingException
    {
        _checkForManager();
	String volname=(String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        volname = volname.substring("/cache/volumes/".length());
        return mgr.summarizeVolume(volname).toMap();
    }

    /**
     * return a summary of the contents of the cache
     */
    @ApiOperation(value="Summarize the contents of the cache", nickname="Summarize contents",
                  notes="Each item describes a dataset, some portion of which is cached")
    @GetMapping(value="/objects/")
    public List<Object> summarizeContents()
        throws InventoryException, StorageVolumeException, NotOperatingException, CacheManagementException
    {
        _checkForManager();
        return mgr.summarizeContents(null).toList();
    }

    /**
     * return a listing of the files from a particular dataset known to the cache.  This will include 
     * files for which the manager has metadata but are not currently stored in the cache (because they 
     * were deleted).  
     */
    @ApiOperation(value="List objects from a dataset collection", nickname="list dataset",
                  notes="Each item describes a dataset which may or may not currently exist in the cache")
    @GetMapping(value="/objects/{dsid}")
    public Map<String,Object> summarizeDataset(@PathVariable("dsid") String dsid)
        throws ResourceNotFoundException, StorageVolumeException, NotOperatingException, CacheManagementException
    {
        _checkForManager();
        JSONObject summary = mgr.summarizeDataset(dsid);
        if (summary == null)
            throw new ResourceNotFoundException(dsid);
        JSONArray files = new JSONArray();
        List<CacheObject> filelist = mgr.selectDatasetObjects(dsid, mgr.VOL_FOR_INFO);
        if (filelist.size() > 0) {
            for (CacheObject co : filelist) 
                files.put(toJSONObject(co));
        }
        summary.put("files", files);
        return summary.toMap();
    }

    static final Pattern SEL_PATH_FIELD = Pattern.compile("/?(:\\w+)$");

    /**
     * return information about selected portion of a dataset: a particular file, the list of cached files, entries 
     * in the inventory, etc.  This method handles several sub-endpoints of a dataset resource endpoint; the path
     * following the dataset identifier can be one of the following:
     * <ul>
     *  <li> the filepath to a file in the dataset:  this returns a description of the file from the inventory 
     *       (including a flag, "cached", indicating whether the file currently exists in the cache) </li>
     *  <li> one of selectors:
     *    <dl>
     *      <dt> <code>:files</code> -- a list of file descriptions for all of the files in the dataset known to 
     *           the inventory, whether currently cached or not. </dt>
     *      <dt> <code>:cached</code> -- a list of file descriptions for only those files currently stored in the 
     *           tha cache. </dt>
     *      <dt> <code>:checked</code> -- the subset of the files listed by <code>:cached</code>, including only 
     *           those files in read-write volumes that are subject to integrity checking. </dt>
     *    </dl> </li>
     *  <li> the filepath followed by one of the above selectors:  if the file matches the selector's criterion,
     *       a descriptions of the file are returned in a list (as the inventory may have more than one entry).
     *       If no files match (e.g. <code>:cached</code> is used but the file is not currently cached), the 
     *       list will be empty.</li>
     * </ul>
     * If a filepath is given and no information exists about it in the inventory, 404 is returned.  
     */
    @ApiOperation(value="Return a description of an object in the cache", nickname="Summarize object",
                  notes="The returned object describes what is known about the object, including a flag indicating whether is cached.")
    @GetMapping(value="/objects/{dsid}/**")
    public void describeDatasetComp(@PathVariable("dsid") String dsid, @ApiIgnore HttpServletRequest request,
                                    @ApiIgnore HttpServletResponse response)
        throws ResourceNotFoundException, StorageVolumeException, NotOperatingException, CacheManagementException
    {
        // ResponseEntity<Map<String,Object>>
        _checkForManager();
	String filepath=(String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        filepath = filepath.substring("/cache/objects/".length()+dsid.length()+1);
        String selector = null;

        Matcher selmatch = SEL_PATH_FIELD.matcher(filepath);
        if (selmatch.find()) {
            selector = selmatch.group(1);
            if (filepath.length() == selector.length() || filepath.charAt(selmatch.start()) == '/')
                filepath = filepath.substring(0, selmatch.start());
            else 
                selector = null;
        }

        int purpose = mgr.VOL_FOR_INFO;
        if (selector != null) {
            if (":checked".equals(selector))
                purpose = mgr.VOL_FOR_UPDATE;
            else if (":cached".equals(selector))
                purpose = mgr.VOL_FOR_GET;
        }

        List<CacheObject> files = mgr.selectDatasetObjects(dsid, mgr.VOL_FOR_INFO);
        if (files.size() == 0)
            throw new ResourceNotFoundException(dsid);

        if (filepath.length() > 0)
            files = mgr.selectFileObjects(dsid, filepath, purpose);
        else if (purpose != mgr.VOL_FOR_INFO)
            files = mgr.selectDatasetObjects(dsid, purpose);

        if (selector == null && filepath.length() > 0) {
            // return a single JSON object; get the one that's cached
            List<CacheObject> use = files.stream().filter(c -> c.cached).collect(Collectors.toList());
            if (use.size() == 0)
                // or get last accessed
                use.add(files.stream().max(Comparator.comparingLong(this::lastAccessTime)).get());
            sendJSON(response, toJSONObject(use.get(0)).toString(2));
        }
        else {
            // return a JSON array of objects
            JSONArray out = new JSONArray();
            for(CacheObject co : files) 
                out.put(toJSONObject(co));
            sendJSON(response, out.toString(2));
        }
    }

    static JSONObject toJSONObject(CacheObject co) {
        JSONObject info = co.exportMetadata();
        info.put("cached", co.cached);
        info.put("volume", co.volname);
        info.put("size", co.getSize());
        info.put("score", co.score);
        info.put("id", co.id);
        info.put("name", co.name);
        return info;
    }

    private long lastAccessTime(CacheObject co) {
        return co.getMetadatumLong("since", 0L);
    }

    private void sendJSON(HttpServletResponse response, String jsonstr) throws CacheManagementException {
        response.setContentLength(jsonstr.length()+1);
        response.setContentType("application/json");
        try (PrintStream out = new PrintStream(response.getOutputStream())) {
            out.println(jsonstr);
        }
        catch (IOException ex) {
            log.error("Trouble sending back JSON data: {}", ex.getMessage());
            if (! response.isCommitted())
                throw new CacheManagementException("Trouble sending JSON data: "+ex.getMessage());
        }
    }

    /**
     * ensure all the objects in a dataset are cached.  
     */
    @ApiOperation(value="Ensure all objects from a dataset collection are in the cache",
                  nickname="cache dataset",
                  notes="The list returned is the same as with GET; it may take a while for all objects to be cached.")
    @PutMapping(value="/objects/{dsid}/**")
    public ResponseEntity<String> updateDataFile(@PathVariable("dsid") String dsid,
                                                 @ApiIgnore HttpServletRequest request)
        throws CacheManagementException, StorageVolumeException, NotOperatingException
    {
        _checkForManager();
	String filepath=(String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        filepath = filepath.substring("/cache/objects/".length()+dsid.length()+1);
        String selector = null;

        Matcher selmatch = SEL_PATH_FIELD.matcher(filepath);
        if (selmatch.find()) {
            selector = selmatch.group(1);
            if (filepath.length() == selector.length() || filepath.charAt(selmatch.start()) == '/')
                filepath = filepath.substring(0, selmatch.start());
            else 
                selector = null;
        }

        String version = request.getParameter("version");
        String id = dsid;
        if (filepath.length() > 0)
            id += "/"+filepath;
        if (version != null)
            id += "#"+version;
        String recachep = request.getParameter("recache");
        boolean recache = ! ("0".equals(recachep) || "false".equals(recachep));
        
        if (":cached".equals(selector)) {
            try {
                mgr.queueCache(id, recache);
                return new ResponseEntity<String>("Cache target queued", HttpStatus.ACCEPTED);
            } catch (ResourceNotFoundException ex) {
                return new ResponseEntity<String>("Resource not found", HttpStatus.NOT_FOUND);
            }
        }
        else if (":checked".equals(selector))
            return new ResponseEntity<String>("check trigger not yet implemented", HttpStatus.METHOD_NOT_ALLOWED);
        else
            return new ResponseEntity<String>("Method not allowed on URL", HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * ensure all the objects in a dataset are cached.  The returned message is the same as 
     * {@link #listObjectsFor(String,String)}.  

    @ApiOperation(value="Ensure all objects from a dataset collection are deleted from the cache",
                  nickname="uncache dataset",
                  notes="")
    @DeleteMapping(value="/objects/{dsid}/:cached")
    public ResponseEntity<String> cacheDataset(@PathVariable("dsid") String dsid)
        throws CacheManagementException, StorageVolumeException, NotOperatingException
    {
        _checkForManager();
    }
     */
    @ExceptionHandler(NotOperatingException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorInfo handleNotOperatingException(NotOperatingException ex, HttpServletRequest req) {
        log.warn("Request to non-engaged Cache Manager: " + req.getRequestURI() + "\n  " + ex.getMessage());
        return new ErrorInfo(req.getRequestURI(), 404, "Cache Management is not in operation");
    }

    @ExceptionHandler(VolumeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorInfo handleVolumeNotFoundException(VolumeNotFoundException ex, HttpServletRequest req) {
        log.warn("Non-existent volume requested: " + req.getRequestURI() + "\n  " + ex.getMessage());
        return new ErrorInfo(req.getRequestURI(), 404, "Volume name not found");
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorInfo handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest req) {
        log.warn("Non-existent resource requested: " + req.getRequestURI() + "\n  " + ex.getMessage());
        return new ErrorInfo(req.getRequestURI(), 404, "Resource ID not found");
    }
    
    @ExceptionHandler(CacheManagementException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorInfo handleInternalError(DistributionException ex, HttpServletRequest req) {
	log.warn("Failure processing request: " + req.getRequestURI() + "\n  " + ex.getMessage());
	return new ErrorInfo(req.getRequestURI(), 500, "Internal Server Error");
    }

}
