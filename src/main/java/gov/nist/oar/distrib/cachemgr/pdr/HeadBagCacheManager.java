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
 * 
 * @author: Raymond Plante
 */
package gov.nist.oar.distrib.cachemgr.pdr;

import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.cachemgr.BasicCache;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.BasicCacheManager;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.IntegrityMonitor;
import gov.nist.oar.distrib.cachemgr.Restorer;
import gov.nist.oar.distrib.cachemgr.RestorationTargetNotFoundException;
import gov.nist.oar.distrib.cachemgr.VolumeStatus;
import gov.nist.oar.clients.OARServiceException;
import gov.nist.oar.clients.ResourceResolver;
import gov.nist.oar.bags.preservation.BagUtils;
import gov.nist.oar.bags.preservation.ZipBagUtils;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.List;
import java.io.IOException;
import java.text.ParseException;

/**
 * a specialized cache specifically for head bags on local disk.  
 * <p>
 * The NIST Public Data Repository (PDR) preserves its data into a preservation format that consists of 
 * aggregations of files conforming the to the BagIt standard using the NIST PDR BagIt profile.  The 
 * profile itself is an extenstion of the more general Multibag Profile.  This latter profile defines the 
 * concept of a head bag that provides a directory for all data in the aggregation; in the PDR extension
 * profile, the complete metadata is also stored in the head bag.  In the PDR, preservation bag files are 
 * stored in an AWS S3 bucket which has some access overheads associated with it; thus, it is helpful to 
 * cache head bags on local disk for access to the metadata.  
 */
public class HeadBagCacheManager extends BasicCacheManager implements PDRConstants {

    HeadBagDB db = null;
    BagStorage ltstore = null;
    final String arknaan;
    final Pattern ARK_PAT;

    public HeadBagCacheManager(BasicCache cache, HeadBagDB hbdb, BagStorage store,
                               Restorer restorer, String naan)
    {
        super(cache, restorer);
        db = hbdb;
        ltstore = store;
        arknaan = naan;
        ARK_PAT = Pattern.compile("^ark:/"+naan+"/");
    }

    /**
     * create a name for a data object within a particular {@link gov.nist.oar.distrib.cachemgr.CacheVolume}.  
     */
    protected String determineCacheObjectName(String volname, String id) {
        return id;
    }

    /**
     * return the NERDm resource record for the dataset with the given AIPID, as extracted from the 
     * dataset's headbag.
     * @param aipid    the AIP ID for the dataset of interest.  This ID is the first field of the 
     *                 dataset's head bag name.  It is either an old-style EDI ID (&gt; 30-character
     *                 string) or the local part of the PDR ARK ID (e.g. mds2-2101).
     * @param version  the desired version.  If null or empty, the latest version is retrieved.  
     * @throws ResourceNotFoundException -- if the AIP cannot be located in the long-term bag storage
     * @throws CacheManagementException -- if an error occurs while trying to cache the source head bag
     *                                         or reading its contents
     */
    public JSONObject resolveAIPID(String aipid, String version) throws CacheManagementException {
        int p = aipid.indexOf("/");
        if (p >= 0)
            aipid = aipid.substring(0, p);
        if (aipid.length() == 0)
            return null;
        try {
            String headbagname = ltstore.findHeadBagFor(aipid, version);
            cache(headbagname);

            // make sure the head bag for it is in the cache and return a handle for it
            List<CacheObject> headbags = db.findObject(headbagname, VolumeStatus.VOL_FOR_GET);
            if (headbags.size() == 0)
                return null;
            headbags.get(0).volume = theCache.getVolume(headbags.get(0).volname);
            
            if (! headbags.get(0).name.endsWith(".zip"))
                throw new CacheManagementException("Unsupported serialization type on bag: " +
                                                   headbags.get(0).name);

            String bagname = headbags.get(0).name.substring(0, headbags.get(0).name.length()-4);
            return ZipBagUtils.getResourceMetadata(BagUtils.multibagVersionOf(headbags.get(0).name),
                                                   headbags.get(0).volume.getStream(headbags.get(0).name),
                                                   bagname);
        }
        catch (ResourceNotFoundException ex) {
            return null;
        }
        catch (IOException ex) {
            throw new CacheManagementException("Failed to read metadata for id="+aipid+": "+ex.getMessage(),
                                               ex);
        }
        catch (StorageVolumeException ex) {
            throw new CacheManagementException("Failed to resolve id="+aipid+": "+ex.getMessage(), ex);
        }
    }

    /**
     * return a NERDm component metadata record corresponding to the given component distribut identifier
     * (i.e., AIPID/filepath), or null if no record exists with this identifier.
     * @param distid   the distribution ID of the desired component which is nominally of the form, 
     *                 AIPID/filepath (where AIPID is the AIP ID, and filepath is the filepath to the 
     *                 desired component).  This ID can optionally be prefixed with the ark: prefix.  
     * @param version  the desired version.  If null or empty, the latest version is retrieved.  
     * @throws OARServiceException  if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolveDistID(String distid, String version) throws CacheManagementException {
        Matcher m = ARK_PAT.matcher(distid);
        if (m.find())
            distid = m.replaceFirst("");
        String[] aipid = distid.split("/", 2);
        if (aipid.length < 2)
            return null;
        return resolveDistribution(aipid[0], aipid[1], version);
    }

    /**
     * return a NERDm component metadata record corresponding to the given component distribut identifier
     * (i.e., AIPID/filepath), or null if no record exists with this identifier.
     * @param distid   the distribution ID of the desired component which is nominally of the form, 
     *                 AIPID/filepath (where AIPID is the AIP ID, and filepath is the filepath to the 
     *                 desired component).  This ID can optionally be prefixed with the ark: prefix.  
     * @param version  the desired version.  If null or empty, the latest version is retrieved.  
     * @throws CacheManagementException  if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolveDistribution(String distid, String filepath, String version)
        throws CacheManagementException
    {
        JSONObject resmd = resolveAIPID(distid, version);
        if (resmd == null)
            return null;
        return findComponentByFilepath(resmd, filepath);
    }

    /**
     * given a NERDm Resource record, extract the component metadata by matching its filepath property.  
     * Return null if the filepath is not found. 
     */
    public static JSONObject findComponentByFilepath(JSONObject resmd, String filepath) {
        JSONArray cmps = resmd.optJSONArray("components");
        if (cmps == null || cmps.length() == 0)
            return null;

        for(Object cmpo : cmps) {
            try {
                JSONObject cmp = (JSONObject) cmpo;
                if (filepath.equals(cmp.optString("filepath", "")))
                    return cmp;
            }
            catch (ClassCastException ex) { }
        }
        return null;
    }

    /**
     * given a NERDm Resource record, extract the component metadata by matching its filepath property.  
     * Return null if the filepath is not found. 
     */
    public static JSONObject findComponentByID(JSONObject resmd, String subID) {
        JSONArray cmps = resmd.optJSONArray("components");
        if (cmps == null || cmps.length() == 0)
            return null;

        for(Object cmpo : cmps) {
            try {
                JSONObject cmp = (JSONObject) cmpo;
                if (subID.equals(cmp.optString("@id", "")))
                    return cmp;
            }
            catch (ClassCastException ex) { }
        }
        return null;
    }

    /**
     * return an IntegrityMonitor instance that is attached to this manager's cache and that can be used 
     * to test the integrity of objects in that cache against a specific list of checks.
     */
    public IntegrityMonitor getIntegrityMonitor(List<CacheObjectCheck> checks) {
        return ((BasicCache) theCache).getIntegrityMonitor(checks);
    }

    /**
     * add additional metadata information about the data object with the given ID from an external
     * source to be stored within the cache inventory database.  This method will get called within 
     * the default {@link #cache(String,boolean)} method.  This implementation adds nothing, but subclasses 
     * should override this to add additional metadata (after calling <code>super.enrichMetadata()</code>).
     * 
     * @param id       the identifier for the data object
     * @param mdata    the metadata container to add information into
     * @throws JSONException   if a failure occurs while writing to the metadata object
     */
    protected void enrichMetadata(String id, JSONObject mdata) throws CacheManagementException {
        super.enrichMetadata(id, mdata);

        String ediid = null;
        try {
            ediid = BagUtils.parseBagName(id).get(0);
        } catch (ParseException ex) { }

        if (ediid != null) {
            if (! ediid.startsWith("ark:/") && ediid.length() < 30) {
                ediid = "ark:/"+arknaan+"/"+ediid;
                mdata.put("pdrid", ediid);
            }
            mdata.put("ediid", ediid);
        }
    }

       

    /*
     * NOTE:  a ResourceResolver implementation is not really possible
     */

    /*
     * return a NERDm metadata record corresponding to the given ID.  The implementation should attempt
     * to recognize the type of identifier provided and return the appropriate corresponding metadata.
     * If no record exists with the identifier, null is returned.
     * @throws AmbiguousIDException  if the identifier cannot be resolved because its type is ambiguous
     * @throws OARServiceException   if something goes wrong with the interaction with resolving service.
    public JSONObject resolve(String id) throws OARServiceException {
        JSONObject resmd = null;
        Matcher m = ARK_PAT.matcher(id);
        if (m.find()) {
            String[] aipid = id.split("/", 3);
            if (aipid.length < 3)
                return resolveResourceID(id);
            else
                return resolveComponentID(id);
        }
        else {
            if (id.startsWith("ark:/"))
                return null;

            // this could be an AIP ID (or an EDI ID)
            String[] aipid = id.split("/", 2);
            resmd = resolveAIPID(aipid[0]);
            if (resmd == null)
                return null;
            else if (aipid.length < 2)
                return resmd;
            else
                return findComponentByFilepath(resmd, aipid[1]);
        }
    }
     */

    /*
     * return a NERDm resource metadata record corresponding to the given PDR identifier, or 
     * null if no record exists with this identifier.
     * @throws OARServiceException  if something goes wrong with the interaction with resolving service.
    public JSONObject resolveResourceID(String id) throws OARServiceException {
        // determine the AIP ID for the PDR ID
        Matcher m = ARK_PAT.matcher(id);
        if (! m.find())
            return null;
        return resolveAIPID(m.replaceFirst(""));
    }
     */

    /*
     * return a NERDm component metadata record corresponding to the given PDR Component identifier, or 
     * null if no record exists with this identifier.
     * @throws OARServiceException  if something goes wrong with the interaction with resolving service.
    public JSONObject resolveComponentID(String id) throws OARServiceException {
        Matcher m = ARK_PAT.matcher(id);
        if (! m.find())
            return null;
        String[] aipid = id.substring(m.end()).split("/", 2);
        JSONObject resmd = resolveAIPID(aipid[0]);
        if (resmd == null || aipid.length < 2)
            return null;
        return findComponentByID(resmd, aipid[1]);
    }
     */

    /*
     * return a NERDm resource metadata record corresponding to the given (NIST) EDI identifier, or 
     * null if no record exists with this identifier.
     * @throws OARServiceException  if something goes wrong with the interaction with resolving service.
    public JSONObject resolveEDIID(String ediid) throws OARServiceException {
        String aipid = ediid;
        Matcher m = ARK_PAT.matcher(aipid);
        if (m.find())
            aipid = m.replaceFirst("");
        else if (aipid.length() < 30)
            return null;

        return resolveAIPID(aipid);
    }
    */
}
