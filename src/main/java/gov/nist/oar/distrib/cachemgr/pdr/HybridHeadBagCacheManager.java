package gov.nist.oar.distrib.cachemgr.pdr;

import gov.nist.oar.bags.preservation.BagUtils;
import gov.nist.oar.bags.preservation.ZipBagUtils;
import gov.nist.oar.clients.OARServiceException;
import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.BasicCache;
import gov.nist.oar.distrib.cachemgr.BasicCacheManager;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.IntegrityMonitor;
import gov.nist.oar.distrib.cachemgr.Restorer;
import gov.nist.oar.distrib.cachemgr.VolumeStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HybridHeadBagCacheManager extends BasicCacheManager implements PDRConstants {

    private BagStorage publicLtstore;
    private BagStorage restrictedLtstore;
    private HeadBagDB db;
    private BasicCache cache;
    private Restorer restorer;
    private final String arknaan;
    private final Pattern ARK_PAT;

    public HybridHeadBagCacheManager(BasicCache cache, HeadBagDB hbdb, BagStorage publicStore,
                                     BagStorage restrictedStore, Restorer restorer, String naan) {
        super(cache, restorer);
        this.cache = cache;
        this.db = hbdb;
        this.publicLtstore = publicStore;
        this.restrictedLtstore = restrictedStore;
        this.restorer = restorer;
        this.arknaan = naan;
        this.ARK_PAT = Pattern.compile("^ark:/" + naan + "/");
    }

    /**
     * return the NAAN this class will use to recognize new-style MIDAS-assigned EDI identifiers
     */
    public String getARKNAAN() {
        return arknaan;
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
     *
     * @param aipid   the AIP ID for the dataset of interest.  This ID is the first field of the
     *                dataset's head bag name.  It is either an old-style EDI ID (&gt; 30-character
     *                string) or the local part of the PDR ARK ID (e.g. mds2-2101).
     * @param version the desired version.  If null or empty, the latest version is retrieved.
     * @throws ResourceNotFoundException -- if the AIP cannot be located in the long-term bag storage
     * @throws CacheManagementException  -- if an error occurs while trying to cache the source head bag
     *                                   or reading its contents
     */
    public JSONObject resolveAIPID(String aipid, String version)
            throws CacheManagementException, ResourceNotFoundException {

        JSONObject metadata;
        try {
            // First, try resolving from restricted storage
            metadata = tryResolveAIPIDFromStorage(aipid, version, restrictedLtstore);
        } catch (ResourceNotFoundException e) {
            // If not found in restricted storage, try public storage
            metadata = tryResolveAIPIDFromStorage(aipid, version, publicLtstore);
        }

        if (metadata == null) {
            throw new ResourceNotFoundException("AIPID " + aipid + " not found in any storage");
        }

        return metadata;
    }

    private JSONObject tryResolveAIPIDFromStorage(String aipid, String version, BagStorage storage)
            throws CacheManagementException, ResourceNotFoundException {

        try {
            String headbagname = storage.findHeadBagFor(aipid, version);
            cache(headbagname);

            // Ensure the head bag for it is in the cache and return a handle for it
            List<CacheObject> headbags = db.findObject(headbagname, VolumeStatus.VOL_FOR_GET);
            if (headbags.size() == 0) {
                throw new ResourceNotFoundException(aipid);
            }
            headbags.get(0).volume = theCache.getVolume(headbags.get(0).volname);

            if (!headbags.get(0).name.endsWith(".zip")) {
                throw new CacheManagementException("Unsupported serialization type on bag: " + headbags.get(0).name);
            }

            String bagname = headbags.get(0).name.substring(0, headbags.get(0).name.length() - 4);
            try (InputStream is = headbags.get(0).volume.getStream(headbags.get(0).name)) {
                return ZipBagUtils.getResourceMetadata(BagUtils.multibagVersionOf(headbags.get(0).name), is, bagname);
            }
        } catch (IOException ex) {
            throw new CacheManagementException("Failed to read metadata for id=" + aipid + ": " + ex.getMessage(), ex);
        } catch (StorageVolumeException ex) {
            throw new CacheManagementException("Failed to resolve id=" + aipid + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * return a NERDm component metadata record corresponding to the given component distribut identifier
     * (i.e., AIPID/filepath), or null if no record exists with this identifier.
     *
     * @param distid  the distribution ID of the desired component which is nominally of the form,
     *                AIPID/filepath (where AIPID is the AIP ID, and filepath is the filepath to the
     *                desired component).  This ID can optionally be prefixed with the ark: prefix.
     * @param version the desired version.  If null or empty, the latest version is retrieved.
     * @throws OARServiceException if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolveDistID(String distid, String version)
            throws CacheManagementException, ResourceNotFoundException, FileNotFoundException {

        Matcher m = ARK_PAT.matcher(distid);
        if (m.find())
            distid = m.replaceFirst("");
        String[] aipid = distid.split("/", 2);
        if (aipid.length < 2)
            return null;

        JSONObject metadata = null;
        try {
            // First, try resolving from restricted storage
            metadata = tryResolveDistIDFromStorage(aipid[0], aipid[1], version, restrictedLtstore);
        } catch (ResourceNotFoundException | FileNotFoundException e) {
            // If not found in restricted storage, try public storage
            metadata = tryResolveDistIDFromStorage(aipid[0], aipid[1], version, publicLtstore);
        }

        if (metadata == null) {
            throw new FileNotFoundException("Distribution ID " + distid + " not found in any storage");
        }

        return metadata;
    }

    private JSONObject tryResolveDistIDFromStorage(String aipid, String filepath, String version, BagStorage storage)
            throws CacheManagementException, ResourceNotFoundException, FileNotFoundException {

        JSONObject resmd = tryResolveAIPIDFromStorage(aipid, version, storage);
        JSONObject out = findComponentByFilepath(resmd, filepath);
        if (out == null) {
            throw new FileNotFoundException(filepath + ": file component not found in " + aipid);
        }
        return out;
    }

    /**
     * return a NERDm component metadata record corresponding to the given AIP-ID and filepath,
     * or null if no record exists with this identifier.
     *
     * @param aipid    the AIP-ID of the desired component's dataset.
     * @param filepath the filepath to desired component
     * @param version  the desired version.  If null or empty, the latest version is retrieved.
     * @throws CacheManagementException if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolveDistribution(String aipid, String filepath, String version)
            throws CacheManagementException, ResourceNotFoundException, FileNotFoundException {

        JSONObject metadata = null;
        try {
            // First, try resolving from restricted storage
            metadata = tryResolveDistributionFromStorage(aipid, filepath, version, restrictedLtstore);
        } catch (ResourceNotFoundException | FileNotFoundException e) {
            // If not found in restricted storage, try public storage
            metadata = tryResolveDistributionFromStorage(aipid, filepath, version, publicLtstore);
        }

        if (metadata == null) {
            throw new FileNotFoundException(filepath + ": file component not found in " + aipid);
        }

        return metadata;
    }

    private JSONObject tryResolveDistributionFromStorage(String aipid, String filepath, String version,
                                                         BagStorage storage)
            throws CacheManagementException, ResourceNotFoundException, FileNotFoundException {

        JSONObject resmd = tryResolveAIPIDFromStorage(aipid, version, storage);
        JSONObject out = findComponentByFilepath(resmd, filepath);
        if (out == null) {
            throw new FileNotFoundException(filepath + ": file component not found in " + aipid);
        }
        return out;
    }

    /**
     * given a NERDm Resource record, extract the component metadata by matching its filepath property.
     * Return null if the filepath is not found.
     */
    public static JSONObject findComponentByFilepath(JSONObject resmd, String filepath) {
        JSONArray cmps = resmd.optJSONArray("components");
        if (cmps == null || cmps.length() == 0)
            return null;

        for (Object cmpo : cmps) {
            try {
                JSONObject cmp = (JSONObject) cmpo;
                if (filepath.equals(cmp.optString("filepath", "")))
                    return cmp;
            } catch (ClassCastException ex) {
            }
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

        for (Object cmpo : cmps) {
            try {
                JSONObject cmp = (JSONObject) cmpo;
                if (subID.equals(cmp.optString("@id", "")))
                    return cmp;
            } catch (ClassCastException ex) {
            }
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
     * source to be stored within the cache inventory database.  This method gets called within
     * the default {@link #cache(String, int, boolean)} method.  This implementation adds PDR-specific
     * metadata.
     *
     * @param id    the identifier for the data object
     * @param mdata the metadata container to add information into
     * @throws JSONException if a failure occurs while writing to the metadata object
     */
    protected void enrichMetadata(String id, JSONObject mdata) throws CacheManagementException {
        super.enrichMetadata(id, mdata);

        String ediid = null;
        try {
            ediid = BagUtils.parseBagName(id).get(0);
        } catch (ParseException ex) {
        }

        if (ediid != null) {
            if (!ediid.startsWith("ark:/") && ediid.length() < 30) {
                ediid = "ark:/" + arknaan + "/" + ediid;
                mdata.put("pdrid", ediid);
            }
            mdata.put("ediid", ediid);
        }
    }

}
