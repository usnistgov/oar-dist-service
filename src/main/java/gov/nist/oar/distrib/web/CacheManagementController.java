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

import gov.nist.oar.distrib.cachemgr.VolumeStatus;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheManager;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.VolumeNotFoundException;
import gov.nist.oar.distrib.cachemgr.pdr.CacheOpts;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * a web service controller that provides access to the distribution cache--its contents, its status,
 * and its operation--via its CacheManager.  
 */
@RestController
@Tag(name="Cache Manager API",
     description=" These API endpoints provide information on the contents and status of the data cache as well as control of the data monitor")
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
    @Operation(summary="Return the status of the cache manager",
               description="200 is returned if the cache manager is in operation, 404 otherwise.")
    @GetMapping(value="/")
    public ErrorInfo getStatus(@Parameter(hidden=true) HttpServletRequest req) throws NotOperatingException {
        _checkForManager();
        return new ErrorInfo(req.getRequestURI(), 200, "Cache Manager in Use");
    }

    /**
     * return a summary of the cache volumes
     */
    @Operation(summary="List summaries of the volumes in the cache", 
               description="Each item in the returned JSON array summarizes the state of a volume within the cache")
    @GetMapping(value="/volumes/")
    public List<Object> summarizeVolumes() throws InventoryException, NotOperatingException {
        _checkForManager();
        return mgr.summarizeVolumes().toList();
    }

    /**
     * return a summary of a cach volume's metadata and stats
     */
    @Operation(summary="Return a summary of a volume in the cache", 
               description="Returns metadata and statistics about a volume and its contents")
    @GetMapping(value="/volumes/**")
    public Map<String, Object> summarizeVolume(@Parameter(hidden=true) HttpServletRequest request)
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
    @Operation(summary="Summarize the contents of the cache", 
               description="Each item describes a dataset, some portion of which is cached")
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
    @Operation(summary="List objects from a dataset collection", 
               description="Each item describes a dataset which may or may not currently exist in the cache")
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
    @Operation(summary="Return a description of an object in the cache", 
               description="The returned object describes what is known about the object, including a flag indicating whether is cached.")
    @GetMapping(value="/objects/{dsid}/**")
    public void describeDatasetComp(@PathVariable("dsid") String dsid, @Parameter(hidden=true) HttpServletRequest request,
                                    @Parameter(hidden=true) HttpServletResponse response)
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

        if (filepath.length() > 0)
            files = mgr.selectFileObjects(dsid, filepath, purpose);
        else if (purpose != mgr.VOL_FOR_INFO)
            files = mgr.selectDatasetObjects(dsid, purpose);

        // Ensure that a ResourceNotFoundException is thrown if the files list is empty
        // after all the selection logic has been applied
        if (files.size() == 0)
            throw new ResourceNotFoundException(dsid);

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
    @Operation(summary="Ensure all objects from a dataset collection are in the cache",
               description="The list returned is the same as with GET; it may take a while for all objects to be cached.")
    @PutMapping(value="/objects/{dsid}/**")
    public ResponseEntity<String> updateDataFile(@PathVariable("dsid") String dsid,
                                                 @Parameter(hidden=true) HttpServletRequest request)
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
        boolean recache = ! (recachep == null || "0".equals(recachep) || "false".equals(recachep));
        String seq = request.getParameter("seq");
        
        if (":cached".equals(selector)) {
            try {
                mgr.queueCache(id, recache, seq);
                log.info("Queued for caching: {} {}", id, ((seq==null) ? "" : "seq="+seq));
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
     * Removes a dataset or specific files within a dataset from the cache based on the provided dataset identifier (dsid).
     * This endpoint supports selective removal using the ":cached" selector in the URL path, allowing for more granular
     * control over cache management.
     *
     * If the ":cached" selector is present, and no specific file path is provided, the entire dataset identified by the dsid
     * is removed from the cache. If a specific file path is provided, only the specified file within the dataset will be removed.
     *
     * @param dsid the dataset identifier
     * @param request used to extract the optional file path from the URL
     * @return ResponseEntity with the result of the operation
     */
    @DeleteMapping(value="/objects/{dsid}/**")
    public ResponseEntity<String> removeFromCache(@PathVariable("dsid") String dsid, HttpServletRequest request) {
        try {

            _checkForManager();
            log.debug("Attempting to remove files from cache for Dataset ID: {}", dsid);

            // Extract the optional file path from the request URL
            String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            String prefix = "/cache/objects/" + dsid;
            String filepath = path.startsWith(prefix) ? path.substring(prefix.length()) : "";

            String selector = null;
            Matcher selmatch = SEL_PATH_FIELD.matcher(filepath);
            if (selmatch.find()) {
                selector = selmatch.group(1);
                filepath = filepath.substring(0, selmatch.start());
            }
            if (filepath.startsWith("/")) {
                filepath = filepath.substring(1); // Remove leading slash
            }

            if (":cached".equals(selector)) {
                if (filepath.isEmpty() || filepath.equals("/")) {
                    log.debug("Removing entire dataset from cache for ID: {}", dsid);
                    List<CacheObject> files = mgr.selectDatasetObjects(dsid, VolumeStatus.VOL_FOR_UPDATE);
                    for (CacheObject file : files) {
                        log.debug("Uncaching file: {}", file.id);
                        mgr.uncache(file.id);
                    }
                    return ResponseEntity.ok("Dataset " + dsid + " removed from cache");
                } else {
                    log.debug("Removing file(s) from cache for dataset ID: {} and path: {}", dsid, filepath);
                    List<CacheObject> files = mgr.selectFileObjects(dsid, filepath, VolumeStatus.VOL_FOR_UPDATE);
                    for (CacheObject file : files) {
                        log.debug("Uncaching file: {}", file.id);
                        mgr.uncache(file.id);
                    }
                    return ResponseEntity.ok("File(s) " + filepath + " in dataset " + dsid + " removed from cache");
                }
            } else {
                log.warn("Operation not allowed: URL does not contain ':cached' selector");
                return new ResponseEntity<String>("Operation not allowed on URL without :cached selector", HttpStatus.METHOD_NOT_ALLOWED);
            }

        } catch (NotOperatingException e) {
            log.error("Cache manager is not operational", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Cache manager is not operational");
        } catch (CacheManagementException e) {
            log.error("Error processing cache removal request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing cache removal request: " + e.getMessage());
        }
    }

    /**
     * return status information about the caching queue.  The caching queue is a queue of data items 
     * waiting to be cached
     */
    @Operation(summary="Return status information about the cache queue",
               description="The caching queue is a queue of data items waiting to be cached")
    @GetMapping(value="/queue/")
    public ResponseEntity<Map<String,Object>> getCacheQueueStatus() throws CacheManagementException {
        return new ResponseEntity<Map<String,Object>>(mgr.getCachingQueueStatus().toMap(), HttpStatus.OK);
    }

    /**
     * trigger the start of the caching process.  The caching thread will start to process any items 
     * found in the queue
     */
    @Operation(summary="Start caching",
               description="The caching process will start to process the data items found in the queue.")
    @PutMapping(value="/queue/")
    public ResponseEntity<Map<String,Object>> startCaching() throws CacheManagementException {
        boolean started = mgr.startCaching();
        JSONObject out = mgr.getCachingQueueStatus();
        out.put("message", (started) ? "Cacher started" : "Cacher is already running");
        return new ResponseEntity<Map<String,Object>>(out.toMap(), HttpStatus.OK);
    }

    /**
     * request that a data item be cached.  The item is added to the current queue and the caching 
     * process is started, if necessary.  The provided data item identifier can either be a dataset
     * identiifer or a file identifier.
     */
    @Operation(summary="Cache a data item",
               description="The caching process will start to process the data items found in the queue.")
    @PutMapping(value="/queue/**")
    public ResponseEntity<Map<String,Object>> queueCache(@Parameter(hidden=true) HttpServletRequest request)
        throws CacheManagementException, StorageVolumeException
    {
	String id=(String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        id = id.substring("/cache/queue/".length());

        String recachep = request.getParameter("recache");
        boolean recache = ! ("0".equals(recachep) || "false".equals(recachep));
        String version = request.getParameter("version");
        String message = null;
        
        if (version != null)
            id += "#"+version;

        try {
            mgr.queueCache(id, recache);
            message = "Requested resource has been queued";
        }
        catch (ResourceNotFoundException ex) {
            message = "Resource not found";
        }
        
        JSONObject out = mgr.getCachingQueueStatus();
        out.put("message", message);
        return new ResponseEntity<Map<String,Object>>(out.toMap(), HttpStatus.OK);
    }

    /**
     * return status information about integrity monitoring 
     */
    @Operation(summary="Return status information about cache file integrity checking",
               description="The properties in the returned JSON Object describe results from the last integrity check.")
    @GetMapping(value="/monitor/")
    public ResponseEntity<Map<String,Object>> getMonitorStatus() throws CacheManagementException {
        return new ResponseEntity<Map<String,Object>>(mgr.getMonitorStatus().toMap(), HttpStatus.OK);
    }
    
    /**
     * start integrity monitoring.  Set <code>repeat=true</code> as a query parameter to have monitoring cycle 
     * continuously on the configured schedule.
     */
    @Operation(summary="Starts integrity monitoring",
               description="Use repeat=true to check repeatably on configured schedule")
    @PutMapping(value="/monitor/running")
    public ResponseEntity<String> startMonitor(@Parameter(hidden=true) HttpServletRequest request)
        throws CacheManagementException
    {
        String repeatp = request.getParameter("repeat");
        if (repeatp != null) repeatp = repeatp.toLowerCase();
        boolean repeat = repeatp != null && ! ("false".equals(repeatp) || "0".equals(repeatp));

        PDRCacheManager.MonitorThread mt = mgr.getMonitorThread();
        mt.setContinuous(repeat);
        if (mt.isAlive()) 
            return new ResponseEntity<String>("Monitor is already running\n", HttpStatus.ACCEPTED);

        mt.start();
        return new ResponseEntity<String>("Monitor started\n", HttpStatus.CREATED);
    }

    /**
     * start integrity monitoring.  Set <code>repeat=true</code> as a query parameter to have monitoring cycle 
     * continuously on the configured schedule.
     */
    @Operation(summary="Report whether integrity monitoring is currently running")
    @GetMapping(value="/monitor/running")
    public ResponseEntity<String> isMonitorRunning()
        throws CacheManagementException
    {
        PDRCacheManager.MonitorThread mt = mgr.getMonitorThread();
        if (mt.isAlive()) 
            return new ResponseEntity<String>("True\n", HttpStatus.OK);

        return new ResponseEntity<String>("False\n", HttpStatus.NOT_FOUND);
    }

    /**
     * stop integrity monitoring after the current cycle completes
     */
    @Operation(summary="Stops integrity monitoring", 
               description="If running, the monitor will stop after the current cycle")
    @DeleteMapping(value="/monitor/running")
    public ResponseEntity<String> stopMonitor() {
        PDRCacheManager.MonitorThread mt = mgr.getMonitorThread();
        if (mt.isAlive()) {
            mt.setContinuous(false);
            return new ResponseEntity<String>("Monitor will end after next cycle", HttpStatus.ACCEPTED);
        }

        return new ResponseEntity<String>("Monitor is not running", HttpStatus.NOT_FOUND);
    }
    

    /**
     * ensure all the objects in a dataset are cached.  The returned message is the same as 
     * {@link #listObjectsFor(String,String)}.  

    @Operation(summary="Ensure all objects from a dataset collection are deleted from the cache",
               description="")
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
