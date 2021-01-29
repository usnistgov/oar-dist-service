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
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
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

    Logger log = LoggerFactory.getLogger(DatasetAccessController.class);

    PDRCacheManager mgr = null;

    @Autowired
    public CacheManagementController(CacheManagerProvider provider) throws ConfigurationException {
        if (provider != null && provider.managerAvailable())
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
     * return a listing of the files from a particular dataset known to the cache
     */
    @ApiOperation(value="List objects from a dataset collection", nickname="list dataset",
                  notes="Each item describes a dataset which may or may not currently exist in the cache")
    @GetMapping(value="/objects/{dsid}")
    public List<Object> listObjectsFor(@PathVariable("dsid") String dsid)
        throws ResourceNotFoundException, StorageVolumeException, NotOperatingException, CacheManagementException
    {
        _checkForManager();
        List<CacheObject> files = mgr.selectDatasetObjects(dsid, mgr.VOL_FOR_INFO);
        if (files.size() == 0)
            throw new ResourceNotFoundException(dsid);
        List<Object> out = new ArrayList<Object>(files.size());
        for (CacheObject co : files) 
            out.add(toJSONObject(co).toMap());
        return out;
    }

    /**
     * return a listing of the files from a particular dataset that exists in the cache under a 
     * given operational status.  The desired operation is the trailing path field, which can be 
     * of the following: 
     * <ul>
     *   <li> <code>:cached</code> -- files that exist in the cache </li>
     *   <li> <code>:checked</code> -- files that exist in the cache and whose integrity status can be 
     *            checked and updated. </li>
     * </ul>
     */
    @ApiOperation(value="List objects from a dataset collection in the cache", nickname="list cached",
                  notes="Each item describes a dataset which currently exists in the cache")
    @GetMapping(value="/objects/{dsid}/{opstat}")
    public ResponseEntity<List<Object>> listObjectsFor(@PathVariable("dsid") String dsid,
                                                       @PathVariable("dsid") String opstat)
        throws ResourceNotFoundException, StorageVolumeException, NotOperatingException, CacheManagementException
    {
        _checkForManager();
        int purpose = mgr.VOL_FOR_GET;
        if (":checked".equals(opstat))
            purpose = mgr.VOL_FOR_UPDATE;
        else if (! ":cached".equals(opstat))
            return new ResponseEntity<List<Object>>( (List<Object>) null, HttpStatus.NOT_FOUND );
        
        List<CacheObject> files = mgr.selectDatasetObjects(dsid, purpose);
        if (files.size() == 0)
            throw new ResourceNotFoundException(dsid);
        List<Object> out = new ArrayList<Object>(files.size());
        for (CacheObject co : files) 
            out.add(toJSONObject(co).toMap());
        return new ResponseEntity<List<Object>>(out, HttpStatus.OK);
    }

    static final Pattern OP_PATH_FIELD = Pattern.compile("/(:\\w+)$");

    /**
     * return information about a particular cache object.  If the filepath is appended with "/:cached",
     * the object is only returned if the object is currently in the cache.
     */
    @ApiOperation(value="Return a description of an object in the cache", nickname="Summarize object",
                  notes="The returned object describes what is known about the object, including a flag indicating whether is cached.")
    @GetMapping(value="/objects/{dsid}/**")
    public ResponseEntity<Map<String,Object>> describeObject(@PathVariable("dsid") String dsid,
                                                             @ApiIgnore HttpServletRequest request)
        throws InventoryException, StorageVolumeException, NotOperatingException, CacheManagementException
    {
        _checkForManager();
	String filepath=(String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        filepath = filepath.substring("/cache/objects/".length()+dsid.length()+1);
        String opstat = null;

        Matcher opmatch = OP_PATH_FIELD.matcher(filepath);
        if (opmatch.find()) {
            filepath = filepath.substring(0, opmatch.start());
            opstat = opmatch.group(1);
        }

        int purpose = mgr.VOL_FOR_INFO;
        if (":checked".equals(opstat))
            purpose = mgr.VOL_FOR_UPDATE;
        CacheObject co = mgr.describeObject(dsid, filepath, purpose);
        if (co == null || (opstat != null && ! co.cached))
            return new ResponseEntity<Map<String,Object>>( (Map<String,Object>) null, HttpStatus.NOT_FOUND );

        return new ResponseEntity<Map<String,Object>>(toJSONObject(co).toMap(), HttpStatus.OK);
    }

    static JSONObject toJSONObject(CacheObject co) {
        JSONObject info = co.exportMetadata();
        info.put("cached", co.cached);
        info.put("volume", co.volume);
        info.put("size", co.getSize());
        info.put("score", co.score);
        info.put("id", co.id);
        info.put("name", co.name);
        return info;
    }

    /**
     * ensure all the objects in a dataset are cached.  The returned message is the same as 
     * {@link #listObjectsFor(String,String)}.  
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
        String opstat = null;

        Matcher opmatch = OP_PATH_FIELD.matcher(filepath);
        if (opmatch.find()) {
            filepath = filepath.substring(0, opmatch.start());
            opstat = opmatch.group(1);
        }
        if (":cached".equals(opmatch)) 
            return cacheDataFile(dsid, filepath, null);
        else if (":checked".equals(opmatch))
            return checkDataFile(dsid, filepath, null);
        else
            return new ResponseEntity<String>("Unrecognized operation", HttpStatus.NOT_FOUND);
    }

    public ResponseEntity<String> checkDataFile(String dsid, String filepath, String version)
        throws CacheManagementException
    {
        String id = dsid + "/" + filepath;
        if (version != null)
            id += "#"+version;

        return null;
    }

    public ResponseEntity<String> cacheDataFile(String dsid, String filepath, String version)
        throws CacheManagementException, StorageVolumeException
    {
        String id = dsid + "/" + filepath;
        if (version != null)
            id += "#"+version;
        try {
            mgr.queueCache(id);
            return new ResponseEntity("Cache target queued", HttpStatus.ACCEPTED);
        }
        catch (ResourceNotFoundException ex) {
            return new ResponseEntity("Resource ID not found", HttpStatus.NOT_FOUND);
        }
        catch (RuntimeException ex) {
            throw new CacheManagementException("Unexpected internal error: "+ex.getMessage());
        }
    }    

    /**
     * ensure all the objects in a dataset are cached.  The returned message is the same as 
     * {@link #listObjectsFor(String,String)}.  
     */
    @ApiOperation(value="Ensure all objects from a dataset collection are in the cache",
                  nickname="cache dataset",
                  notes="The list returned is the same as with GET; it may take a while for all objects to be cached.")
    @PutMapping(value="/objects/{dsid}/:cached")
    public ResponseEntity<String> cacheDataset(@PathVariable("dsid") String dsid)
        throws CacheManagementException, StorageVolumeException, NotOperatingException
    {
        _checkForManager();
        try {
            mgr.queueCache(dsid);
            return new ResponseEntity("Cache target queued", HttpStatus.ACCEPTED);
        }
        catch (ResourceNotFoundException ex) {
            return new ResponseEntity("Resource ID not found", HttpStatus.NOT_FOUND);
        }
        catch (RuntimeException ex) {
            throw new CacheManagementException("Unexpected internal error: "+ex.getMessage());
        }
    }

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
