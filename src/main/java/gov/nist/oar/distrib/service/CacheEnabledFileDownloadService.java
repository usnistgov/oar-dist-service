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
package gov.nist.oar.distrib.service;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.FileDescription;
import gov.nist.oar.distrib.StreamHandle;
import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheManager;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagCacheManager;
import gov.nist.oar.clients.rmm.ComponentInfoCache;
import gov.nist.oar.bags.preservation.BagUtils;
import gov.nist.oar.bags.preservation.ZipBagUtils;
import gov.nist.oar.bags.preservation.HeadBagUtils;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import javax.activation.MimetypesFileTypeMap;

import org.json.JSONObject;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A download service that leverages a data cache, NERDm metadata, and preservation bags in long-term storage 
 * to deliver data files from the repository.  
 * <p>
 * This {@link FilesDownloadService} implementation has access to a data cache that temporarily houses 
 * individual files from repository datasets.  When a user requests a data file from this service, the service 
 * will first look in the cache for the file; if it is not found there, it can stream the file directly from 
 * long-term storage.  In the PDR repository model, data files are stored long-term in "preservation bags"--
 * zipped containers (which may be in AWS S3 storage); so, delivering the data from a cache will always be more 
 * efficient.  Migration of data from long-term storage largely happens outside of this class; however, under 
 * certain circumstances, this can be triggered transparently as files are requested.  
 * <p>
 * Data file information is retrieved from the NERDm metadata extracted from the dataset's "head bag".  
 */
public class CacheEnabledFileDownloadService implements FileDownloadService {

    FileDownloadService srcsvc = null;
    CacheManager cmgr = null;
    HeadBagCacheManager hbcmgr = null;
    PreservationBagService pres = null;
    MimetypesFileTypeMap typemap = null;

    ComponentInfoCache compcache = null;

    static final private int defMDCSzLim  = 1000;    // # of components cached
    static final private long defMDCTmLim = 300;     // time limit of 5 minutes

    private void setComponentCache(int cacheSizeLimit, long cacheExpireTimeSecs) {
        compcache = new ComponentInfoCache(cacheSizeLimit, cacheExpireTimeSecs,
                                           Arrays.asList("nrdp:DataFile", "nrdp:DownloadableFile"),
                                           Arrays.asList("nrd:Hidden"), true, 10);
    }

    protected static Logger logger = LoggerFactory.getLogger(FileDownloadService.class);

    /**
     * create the service instance, wrapping a CacheManager
     * @param bagService       the service to use to access bags from long-term storage
     * @param cachemgr         the CacheManager to use to locate files in the cache.  If null, the 
     *                           downloadSvc will always be used.  
     * @param headbagcachemgr  the HeadBagCacheManager used for managing head bags and the metadata 
     *                            they contain.  This must always be provided. 
     * @param typemap          the map to use for determining content types from filename extensions; 
     *                           if null, a default will be used.  
     */
    public CacheEnabledFileDownloadService(PreservationBagService bagService, CacheManager cachemgr,
                                           HeadBagCacheManager headbagcachemgr, MimetypesFileTypeMap mimemap)
    {
        this(new FromBagFileDownloadService(bagService, mimemap), bagService, cachemgr, headbagcachemgr, mimemap);
    }

    /**
     * create the service instance, wrapping a FromBagFileDownloadService instance and a CacheManager
     * @param srcService       the service to use to deliver files when the files are not in the cache.
     * @param bagService       the service to use to access bags from long-term storage
     * @param cachemgr         the CacheManager to use to locate files in the cache.  If null, the 
     *                           downloadSvc will always be used.  
     * @param headbagcachemgr  the HeadBagCacheManager used for managing head bags and the metadata 
     *                           they contain.  This must always be provided (otherwise, just use 
     *                           the downloadSvc instance instead).
     * @param mimemap          the map to use for determining content types from filename extensions; 
     *                           if null, a default will be used.  
     */
    public CacheEnabledFileDownloadService(FileDownloadService srcService, PreservationBagService bagService, 
                                           CacheManager cachemgr, HeadBagCacheManager headbagcachemgr,
                                           MimetypesFileTypeMap mimemap)
    {
        pres = bagService;
        srcsvc = srcService;
        cmgr = cachemgr;
        hbcmgr = headbagcachemgr;
        if (hbcmgr == null)
            throw new NullPointerException("CacheEnabledFileDownloadService: A HeadBagCacheManager must " +
                                           "always be provided.");
        if (mimemap == null) {
            InputStream mis = getClass().getResourceAsStream("/mime.types");
            mimemap = (mis == null) ? new MimetypesFileTypeMap()
                                    : new MimetypesFileTypeMap(mis);
        }
        typemap = mimemap;
        setComponentCache(defMDCSzLim, defMDCTmLim);
    }

    /**
     * create the service instance, wrapping a FromBagFileDownloadService instance and a CacheManager
     * @param srcService       the service to use to deliver files when the files are not in the cache.
     * @param bagService       the service to use to access bags from long-term storage
     * @param cachemgr         the CacheManager to use to locate files in the cache.  If null, the 
     *                           downloadSvc will always be used.  
     * @param headbagcachemgr  the HeadBagCacheManager used for managing head bags and the metadata 
     *                           they contain.  This must always be provided (otherwise, just use 
     *                           the downloadSvc instance instead).
     * @param typemap          the map to use for determining content types from filename extensions; 
     *                           if null, a default will be used.  
     */
    public CacheEnabledFileDownloadService(FileDownloadService srcService, PreservationBagService bagService, 
                                           CacheManager cachemgr, HeadBagCacheManager headbagcachemgr)
    {
        this(srcService, bagService, cachemgr, headbagcachemgr, null);
    }

    private String cacheid(String dsid, String filepath, String version) {
        String id = dsid + "/" + filepath;
        if (version != null && version.length() > 0)
            id += "#" + version;
        return id;
    }

    /**
     * Return the filepaths of data files available from the dataset with a given identifier
     *
     * @param dsid      the dataset identifier for the desired dataset
     * @param version   the version of the dataset.  If null, the latest version is returned.
     * @throws ResourceNotFoundException   if the dsid is not recognized or there is no such version
     *                                        available for the dataset with dsid.
     * @throws DistributionException       if an internal error has occurred
     */
    public List<String> listDataFiles(String dsid, String version) 
        throws ResourceNotFoundException, DistributionException
    {
        String headbag = (version == null) ? pres.getHeadBagName(dsid)
                                           : pres.getHeadBagName(dsid, version);
        if (! headbag.endsWith(".zip"))
            throw new DistributionException("Bag uses unsupported serialization: " + headbag);
        String bagname = headbag.substring(0, headbag.length()-4);
        String bv = BagUtils.multibagVersionOf(bagname);
        
        CacheObject co = hbcmgr.getObject(headbag);  // this will automatically cache bag file
        try (InputStream is = co.volume.getStream(co.name)) {
            return HeadBagUtils.listDataFiles(bv, ZipBagUtils.openFileLookup(bv, is, bagname).stream);
        }
        catch (FileNotFoundException ex) {
            throw new DistributionException(headbag +
                                            ": file-lookup.tsv not found (is this a head bag?)", ex);
        }
        catch (IOException ex) {
            throw new DistributionException("Error accessing file-lookup.tsv: " + ex.getMessage(), ex);
        }
    }

    /**
     * return a default content type based on the given file name.  This implementation determines
     * the content type based on the file name's extension.  
     */
    public String getDefaultContentType(String filename) {
        return typemap.getContentType(filename);
    }

    /**
     * Describe the data file with the given filepath.  The returned information includes the 
     * file size, type, and checksum information.  
     *
     * @param dsid      the dataset identifier 
     * @param filepath  the path within the dataset to the desired file
     * @param version   the version of the dataset.  If null, the latest version is returned.
     * @throws ResourceNotFoundException   if the dsid is not recognized or there is no such version
     *                                        available for the dataset with dsid.
     * @throws FileNotFoundException       if the filepath is not found in the requested version of 
     *                                        the identified dataset
     * @throws DistributionException       if an internal error has occurred
     */
    @Override
    public FileDescription getDataFileInfo(String dsid, String filepath, String version)
        throws ResourceNotFoundException, DistributionException, FileNotFoundException
    {
        if (cmgr != null) {
            // look for the file in the cache
            CacheObject co = cmgr.findObject(cacheid(dsid, filepath, version));
            if (co != null)
                return cacheObject2FileDesc(co);
        }

        JSONObject cmp = null;
        try {
            if (version != null) {
                // if we are not sure we are looking for the latest (i.e. version is non-null),
                // do not rely on the cache; read it directly from the head bag.
                cmp = hbcmgr.resolveDistribution(dsid, filepath, version);
            }
            else {
                // look for the component metadata in our in-memory cache
                String cmpid = dsid + "/" + filepath;
                cmp = compcache.get(cmpid, true);
                cmpid = cmpid.substring(dsid.length()+1);

                // if not in cache, extract the info from the head bag and cache it.  This may raise
                // a ResourceNotFoundException
                if (cmp == null) {
                    logger.debug("metadata cache miss: {}/{}", dsid, cmpid);
                    logger.debug("Cache status: size={}.", Integer.toString(compcache.size()));
                    cmp = compcache.cacheResource(hbcmgr.resolveAIPID(dsid, null), false, cmpid, dsid);
                }
                else {
                    logger.debug("metadata cache hit!: {}/{}", dsid, cmpid);
                }
            }
        }
        catch (CacheManagementException ex) {
            throw new DistributionException("Failed to get distribution metadata: "+ex.getMessage(), ex);
        }
        if (cmp == null)
            throw new FileNotFoundException(filepath);

        if (! cmp.has("size")) 
            // returning a size is considered critical; use back-up approach
            return srcsvc.getDataFileInfo(dsid, filepath, version);

        // convert the component metadata into a FileDescription 
        FileDescription out = new FileDescription(filepath, cmp.getLong("size"),
                                                  cmp.optString("mediaType", null));
        out.aipid = dsid;
        if (out.contentType == null)
            out.contentType = getDefaultContentType(filepath);
        if (cmp.has("checksum")) {
            JSONObject cs = cmp.getJSONObject("checksum");
            out.checksum = new Checksum(cmp.optString("hash", null), cmp.optString("algorithm", null));
        }
        // FUTURE: add additional metadata as properties?
        
        return out;
    }

    /**
     * return CacheObject corresponding to a file in the cache matching the given identifiers.  
     * @param dsid    the dataset identifier for the desired dataset
     * @param filepath  the path within the dataset to the desired file
     * @param version   the version of the dataset.  If null, the latest version is returned.
     * @return CacheObject if the file is available in the cache; null, otherwise.  
     */
    public CacheObject findCachedObject(String dsid, String filepath, String version)
        throws CacheManagementException
    {
        if (cmgr != null) {
            try {
                CacheObject out = cmgr.findObject(cacheid(dsid, filepath, version));
                if (out != null && out.volume == null) out = null;
                if (out != null)
                    return out;
            }
            catch (CacheManagementException ex) {
                logger.error("Failure while searching cache: {} (skipping)", ex.getMessage());
                logger.warn("Ignoring cached version due to error");
            }
        }
                
        return null;
    }

    /**
     * given a {@link gov.nist.oar.distrib.cachemgr.CacheObject}, return a redirect URL for accessing the 
     * underlying object or null, if such a URL is not available.  Using this function on a 
     * {@link gov.nist.oar.distrib.cachemgr.CacheObject} returned by {@link #findCachedObject(String,String,String)}
     * is more efficient than calling {@link #getDataFileRedirect(String,String,String)} followed possibly 
     * by a call to  {@link #getDataFile(String,String,String)} as the latter will repeat the search of the 
     * cache inventory.  
     */
    public static URL redirectFor(CacheObject co) {
        if (co == null || co.volume == null)
            return null;

        try {
            return co.volume.getRedirectFor(co.name);
        }
        catch (UnsupportedOperationException ex) { }
        catch (StorageVolumeException ex) {
            String name = co.volume.getName();
            if (name == null) name = "??";
            logger.error("Failure while accessing volume, {}: {} (skipping)", name, ex.getMessage());
            logger.warn("Ignoring redirect cached version of file");
        }

        return null;
    }

    /**
     * given a {@link gov.nist.oar.distrib.cachemgr.CacheObject}, return a {@link gov.nist.oar.distrib.StreamHandle}
     * to the object.  This is a convenience function to be used in the use case described for {@link #redirectFor}:
     * if that function returns null--i.e. there is no redirect URL available--one can open a stream to the
     * object in the cache via this function.
     */
    public static StreamHandle openStreamFor(CacheObject co) throws StorageVolumeException {
        return cacheObject2StreamHandle(co);
    }
    
    /**
     * Download the data file with the given filepath.
     * <p>
     * The caller is responsible for closing the return stream.
     *
     * @param dsid    the dataset identifier for the desired dataset
     * @param filepath  the path within the dataset to the desired file
     * @param version   the version of the dataset.  If null, the latest version is returned.
     * @return StreamHandle - an open stream to the file data accompanied by file metadata (like 
     *                  content length, type, checksum).
     * @throws ResourceNotFoundException   if the dsid is not recognized or there is no such version
     *                                        available for the dataset with dsid.
     * @throws FileNotFoundException       if the filepath is not found in the requested version of 
     *                                        the identified dataset
     * @throws DistributionException       if an internal error has occurred
     */
    @Override
    public StreamHandle getDataFile(String dsid, String filepath, String version)
        throws ResourceNotFoundException, DistributionException, FileNotFoundException
    {
        // check cache
        if (cmgr != null) {
            try {
                CacheObject co = findCachedObject(dsid, filepath, version);
                if (co != null && co.volume != null) 
                    return cacheObject2StreamHandle(co);
            }
            catch (CacheManagementException ex) {
                logger.error("Trouble searching cache for {}: {}", cacheid(dsid, filepath, version),
                             ex.getMessage());
            }
            catch (StorageVolumeException ex) {
                logger.error("Error opening cache object for {}: {}", cacheid(dsid, filepath, version),
                             ex.getMessage());
                logger.warn("Falling back on direct extraction due to cache error");
            }
        }

        // last resort: straight from long-term storage
        return srcsvc.getDataFile(dsid, filepath, version);
    }

    /**
     * return a URL where the identified file can be downloaded directly from.  This implementation 
     * always returns null.  
     *
     * @param dsid    the dataset identifier for the desired dataset
     * @param filepath  the path within the dataset to the desired file
     * @param version   the version of the dataset.  If null, the latest version is returned.
     * @return null, always
     */
    @Override
    public URL getDataFileRedirect(String dsid, String filepath, String version)
        throws ResourceNotFoundException, DistributionException, FileNotFoundException
    {
        try {
            // check cache
            return redirectFor(findCachedObject(dsid, filepath, version));
        }
        catch (CacheManagementException ex) {
            logger.error("Trouble searching cache for {} (for redirect): {}", 
                         cacheid(dsid, filepath, version), ex.getMessage());
        }
        return srcsvc.getDataFileRedirect(dsid, filepath, version);
    }

    private static FileDescription cacheObject2FileDesc(CacheObject co) {
        String ct = getMetadatumString(co, "contentType", null);
        String hash = getMetadatumString(co, "checksum", null);
        Checksum cs = null;
        if (hash != null)
            cs = new Checksum(hash, getMetadatumString(co, "checksumAlgorithm", null));
        String name = co.name;
        int p = name.indexOf('/');
        if (p >= 0)
            name = name.substring(p+1);
        return new FileDescription(name, co.getSize(), ct, cs);
    }

    private static String getMetadatumString(CacheObject co, String mdname, String defval) {
        try {
            return co.getMetadatumString(mdname, defval);
        }
        catch (JSONException ex) {
            logger.error("Trouble accessing {} metadatum for CacheObject, {}: {}",
                         mdname, co.name, ex.getMessage());
        }
        return defval;
    }

    private static StreamHandle cacheObject2StreamHandle(CacheObject co) throws StorageVolumeException {
        FileDescription fd = cacheObject2FileDesc(co);
        return new StreamHandle(co.volume.getStream(co.name), fd);
    }
}
