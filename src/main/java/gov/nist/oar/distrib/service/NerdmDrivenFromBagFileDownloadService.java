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
import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.StreamHandle;
import gov.nist.oar.clients.rmm.ComponentInfoCache;
import gov.nist.oar.bags.preservation.BagUtils;
import gov.nist.oar.bags.preservation.ZipBagUtils;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.Arrays;
import javax.activation.MimetypesFileTypeMap;

/**
 * A variation on the 
 * {@link gov.nist.oar.distrib.service.FromBagFileDownloadService FromBagFileDownloadService}
 * implementation of the {@link gov.nist.oar.distrib.service.FileDownloadService FileDownloadService}
 * interface where data file information is pulled via queries to the RMM database.
 */
public class NerdmDrivenFromBagFileDownloadService extends FromBagFileDownloadService {

    ComponentInfoCache compcache = null;

    static final private int defSzLim  = 1000;    // # of components cached
    static final private long defTmLim = 300000;  // time limit of 5 minutes

    private void setComponentCache(int cacheSizeLimit, long cacheExpireTimeSecs) {
        compcache = new ComponentInfoCache(cacheSizeLimit, cacheExpireTimeSecs,
                                           Arrays.asList("nrdp:DataFile", "nrdp:DownloadableFile"),
                                           Arrays.asList("nrd:Hidden"), 10);
    }

    /**
     * create the service instance.  
     * 
     * @param svc      an instance of a PreservationBagService to use to access bags.
     * @param typemap  the map to use for determining content types from filename extensions; 
     *                 if null, a default will be used.  
     */
    public NerdmDrivenFromBagFileDownloadService(PreservationBagService svc, MimetypesFileTypeMap mimemap) {
        super(svc, mimemap);
        setComponentCache(defSzLim, defTmLim);
    }

    /**
     * create the service instance.  
     * 
     * @param svc   an instance of a PreservationBagService to use to access bags.
     */
    public NerdmDrivenFromBagFileDownloadService(PreservationBagService svc) {
        super(svc);
        setComponentCache(defSzLim, defTmLim);
    }

    /**
     * create the service instance.  
     * 
     * @param bagstore   a LongTermStorage instance repesenting the storage holding the 
     *                   preservation bags
     * @param typemap  the map to use for determining content types from filename extensions; 
     *                 if null, a default will be used.  
     */
    public NerdmDrivenFromBagFileDownloadService(LongTermStorage bagstore, MimetypesFileTypeMap mimemap) {
        super(bagstore, mimemap);
        setComponentCache(defSzLim, defTmLim);
    }

    /**
     * create the service instance.  
     * 
     * @param bagstore   a LongTermStorage instance repesenting the storage holding the 
     *                   preservation bags
     */
    public NerdmDrivenFromBagFileDownloadService(LongTermStorage bagstore) {
        super(bagstore);
        setComponentCache(defSzLim, defTmLim);
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
    public FileDescription getDataFileInfo(String dsid, String filepath, String version)
        throws ResourceNotFoundException, DistributionException, FileNotFoundException
    {
        JSONObject cmp = null;
        if (version != null) {
            // if we are not sure we are looking for the latest (i.e. version is non-null),
            // do not rely on the cache; read it directly from the head bag.
            cmp = getFileMetadata(dsid, filepath, version);
        }
        else {
            
            // look for the component metadata in our in-memory cache
            String cmpid = dsid + "/cmps/" + filepath;
            cmp = compcache.get(cmpid, true);
            cmpid = "cmps/"+filepath;

            // if not in cache, extract the info from the head bag and cache it.  This may raise
            // a ResourceNotFoundException
            if (cmp == null) {
                logger.info("metadata cache miss: "+dsid+"/"+cmpid);
                cmp = compcache.cacheResource(getResourceMetadata(dsid, version), false, cmpid, dsid);
            }
            else {
                logger.info("metadata cache hit!: "+dsid+"/"+cmpid);
            }
                
        }
        if (cmp == null)
            throw new FileNotFoundException(filepath);

        if (! cmp.has("size"))
            // returning a size is considered critical; use back-up approach
            return super.getDataFileInfo(dsid, filepath, version);

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
     * return the NERDm metadata object describing the resource component with given filepath in the resource 
     * with a given ID.
     * @param dsid      the dataset identifier 
     * @param filepath  the path within the dataset to the desired file
     * @param version   the version of the dataset.  If null, the latest version is returned.
     */
    public JSONObject getFileMetadata(String dsid, String filepath, String version)
        throws ResourceNotFoundException, DistributionException, FileNotFoundException
    {
        // find the head bag for the requested version
        String headbag = (version == null) ? pres.getHeadBagName(dsid)
                                           : pres.getHeadBagName(dsid, version);
        if (! headbag.endsWith(".zip"))
            throw new DistributionException("Bag uses unsupported serialization: " + headbag);
        String bagname = headbag.substring(0, headbag.length()-4);

        JSONObject nerdm = null;
        try (StreamHandle sh = pres.getBag(headbag)) {
            nerdm = ZipBagUtils.getFileMetadata(filepath, sh.dataStream, bagname);
        }
        catch (IOException ex) {
            throw new DistributionException("Trouble extracting metadata from head bag: "+
                                            ex.getMessage(),ex);
        }

        return nerdm;
    }

    /**
     * return the NERDm metadata object describing the resource with a given ID.
     * @param dsid      the dataset identifier 
     * @param version   the version of the dataset.  If null, the latest version is returned.
     */
    public JSONObject getResourceMetadata(String dsid, String version)
        throws ResourceNotFoundException, DistributionException, FileNotFoundException
    {
        // find the head bag for the requested version
        String headbag = (version == null) ? pres.getHeadBagName(dsid)
                                           : pres.getHeadBagName(dsid, version);
        if (! headbag.endsWith(".zip"))
            throw new DistributionException("Bag uses unsupported serialization: " + headbag);
        String bagname = headbag.substring(0, headbag.length()-4);
        String bv = BagUtils.multibagVersionOf(bagname);

        JSONObject nerdm = null;
        try (StreamHandle sh = pres.getBag(headbag)) {
            nerdm = ZipBagUtils.getResourceMetadata(bv, sh.dataStream, bagname);
        }
        catch (IOException ex) {
            throw new DistributionException("Trouble extracting metadata from head bag: "+
                                            ex.getMessage(),ex);
        }

        return nerdm;
    }
}
