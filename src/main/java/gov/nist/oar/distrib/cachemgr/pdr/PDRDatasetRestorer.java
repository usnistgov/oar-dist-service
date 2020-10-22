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

import gov.nist.oar.bags.preservation.BagUtils;
import gov.nist.oar.bags.preservation.ZipBagUtils;
import gov.nist.oar.bags.preservation.HeadBagUtils;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.cachemgr.Restorer;
import gov.nist.oar.distrib.cachemgr.Reservation;
import gov.nist.oar.distrib.cachemgr.IntegrityMonitor;
import gov.nist.oar.distrib.cachemgr.Cache;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.RestorationException;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.text.ParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.json.JSONObject;
import org.json.JSONException;

import org.apache.commons.io.FilenameUtils;

/**
 * A {@link gov.nist.oar.distrib.cachemgr.Restorer} for restoring PDR datasets and dataset files according 
 * to PDR conventions for long-term storage of datasets in BagIt bags.  
 * <p>
 * The NIST Public Data Repository (PDR) preserves its data into a preservation format that consists of 
 * aggregations of files conforming the to the BagIt standard using the NIST PDR BagIt profile.  The 
 * profile itself is an extenstion of the more general Multibag Profile.  This latter profile defines the 
 * concept of a head bag that provides a directory for all data in the aggregation; in the PDR extension
 * profile, the complete metadata is also stored in the head bag.  In the PDR, preservation bag files are 
 * stored in an AWS S3 bucket which has some access overheads associated with it; thus, it is helpful to 
 * cache head bags on local disk for access to the metadata; thus, this 
 * {@link gov.nist.oar.distrib.cachemgr.Restorer} implementation makes use of such a cache (via a 
 * {@link HeadBagCacheManager}).  
 * <p>
 * Individual files can restored to cache using the {@link gov.nist.oar.distrib.cachemgr.Restorer} interface;
 * however, whole datasets can be efficiently cached as well via its extended interface.  
 */
public class PDRDatasetRestorer implements Restorer, PDRConstants, PDRCacheRoles {

    BagStorage ltstore = null;
    HeadBagCacheManager hbcm = null;
    long smszlim = 100000000L;  // 100 MB
    Logger log = null;

    /**
     * create the restorer
     * @param bagstore      the long term storage where the head bags are stored
     * @param headbagcache  the cache to use cache the head bags
     */
    public PDRDatasetRestorer(BagStorage bagstore, HeadBagCacheManager headbagcache) {
        this(bagstore, headbagcache, -1L);
    }

    /**
     * create the restorer
     * @param bagstore      the long term storage where the head bags are stored
     * @param headbagcache  the cache to use cache the head bags
     * @param smallsizelim  a data file size limit: files smaller than this will preferentially go 
     *                        into volumes marked for small files.
     */
    public PDRDatasetRestorer(BagStorage bagstore, HeadBagCacheManager headbagcache, long smallsizelim) {
        this(bagstore, headbagcache, smallsizelim, null);
    }

    /**
     * create the restorer
     * @param bagstore      the long term storage where the head bags are stored
     * @param headbagcache  the cache to use cache the head bags
     * @param smallsizelim  a data file size limit: files smaller than this will preferentially go 
     *                        into volumes marked for small files.
     * @param logger        the logger to use for messages.
     */
    public PDRDatasetRestorer(BagStorage bagstore, HeadBagCacheManager headbagcache, long smallsizelim,
                              Logger logger)
    {
        ltstore = bagstore;
        hbcm = headbagcache;
        if (smallsizelim >= 0L)
            smszlim = smallsizelim;
        if (logger == null)
            logger = LoggerFactory.getLogger(getClass());
        log = logger;
    }

    String[] parseId(String id) {
        String[] out = new String[3];
        String[] parts = null;

        // version
        out[2] = null;
        if (id.contains("#")) {
            parts = id.split("#", 2);
            out[2] = parts[1];
            id = parts[0];
        }

        out[0] = id;
        out[1] = "";
        if (id.contains("/")) {
            parts = id.split("/", 2);
            out[0] = parts[0];
            out[1] = parts[1];
        }

        return out;
    }

    /**
     * return true if an object does <i>not</i> exist in the long term storage system.  Returning 
     * true indicates that the object <i>may</i> exist, but it is not guaranteed.  These semantics
     * are intended to allow the implementation to be fast and without large overhead. 
     * @param id   the storage-independent identifier for the data object
     */
    @Override
    public boolean doesNotExist(String id) throws StorageVolumeException, CacheManagementException {
        String[] parts = parseId(id);
        try {
            ltstore.findBagsFor(parts[0]);
            return false;
        } catch (ResourceNotFoundException ex) {
            return true;
        }
    }

    /**
     * return the size of the object with the given identifier in bytes or -1L if unknown
     * @param id   the distribution ID for the object
     * @throws ObjectNotFoundException    if the object can not be found in the underlying storage
     * @throws UnsupportedOperationException   if due to implementation limitations, this Restorer is 
     *             unable to return sizes for any objects it knows about.  
     */
    @Override
    public long getSizeOf(String id)
        throws StorageVolumeException, CacheManagementException, UnsupportedOperationException
    {
        String[] parts = parseId(id);
        JSONObject cmpmd = hbcm.resolveDistribution(parts[0], parts[1], parts[2]);
        if (cmpmd == null)
            throw new ObjectNotFoundException(id);
        return cmpmd.optLong("size", -1L);
    }

    /**
     * return the checksum hash of the object with the given identifier or null if unknown.  
     * @param id   the distribution ID for the object
     * @throws ObjectNotFoundException    if the object can not be found in the underlying storage
     * @throws UnsupportedOperationException   if due to implementation limitations, this Restorer is 
     *             unable to return sizes for any objects it knows about.  
     */
    @Override
    public Checksum getChecksum(String id)
        throws StorageVolumeException, CacheManagementException, UnsupportedOperationException
    {
        String[] parts = parseId(id);
        JSONObject cmpmd = hbcm.resolveDistribution(parts[0], parts[1], parts[2]);
        if (cmpmd == null)
            throw new ObjectNotFoundException(id);
        JSONObject chksum = cmpmd.optJSONObject("checksum");
        if (chksum == null)
            return null;
        JSONObject alg = chksum.optJSONObject("algorithm");
        if (alg == null) {
            alg = new JSONObject();
            alg.put("tag", "unknown");
        }
        return new Checksum(chksum.optString("hash", ""), alg.optString("tag", "unknown"));
    }

    /**
     * restore the identified object to the CacheVolume associated with the given Reservation
     * @param id        the storage-independent identifier for the data object
     * @param resv      the reservation for space in a CacheVolume where the object should be restored to.
     * @param name      the name to assign to the object within the volume.  
     * @param metadata  the metadata to associate with this restored object.  This will get merged with 
     *                    metadata determined as part of the restoration.  Can be null.
     * @throws RestorationException  if there is an error while accessing the source data for the object
     * @throws StorageVolumeException  if there is an error while accessing the cache volume while writing
     * @throws ObjectNotFoundException    if the object can not be found in the underlying storage
     * @throws JSONException         if there is a problem accessing the information in the provided metadata
     */
    @Override
    public void restoreObject(String id, Reservation resv, String name, JSONObject metadata)
        throws RestorationException, StorageVolumeException, JSONException
    {
        String[] idparts = parseId(id);
        String headbag = null;
        JSONObject cachemd = null;
        try {
            headbag = ltstore.findHeadBagFor(idparts[0], idparts[2]);
            cachemd = getCacheMDFromHeadBag(headbag, idparts[1]);
        }
        catch (ResourceNotFoundException ex) {
            String dataset = idparts[0];
            if (idparts[2] != null) dataset += "#"+idparts[2];
            throw new ObjectNotFoundException("Resource "+dataset+" not found", dataset);
        }
        catch (CacheManagementException ex) {
            throw new RestorationException("Failed to cache head bag, "+headbag+": "+ex.getMessage(), ex);
        }
        if (cachemd == null) 
            throw new ObjectNotFoundException("Filepath, "+idparts[1]+", not found in " + idparts[0], id);
        if (metadata != null) {
            for (String prop : JSONObject.getNames(metadata)) {
                if (! cachemd.has(prop))
                    cachemd.put(prop, metadata.get(prop));
            }
        }

        String srcbag = findBagFor(headbag, idparts[1], id);
        if (! srcbag.endsWith(".zip"))
            throw new RestorationException("Unsupported serialization type on bag: " + srcbag);
        String bagname = srcbag.substring(0, srcbag.length()-4);

        InputStream bstrm = null;
        try {
            bstrm = ltstore.openFile(srcbag);
            ZipBagUtils.OpenEntry ntry = ZipBagUtils.openDataFile(bstrm, bagname, idparts[1]);
            resv.saveAs(ntry.stream, id, name, cachemd);
        }
        catch (FileNotFoundException ex) {
            throw new RestorationException(id+": Data file missing from source bag: "+ex.getMessage(), ex);
        }
        catch (IOException ex) {
            throw new RestorationException(id+": Trouble reading data file: "+ex.getMessage(), ex);
        }
        catch (CacheManagementException ex) {
            throw new RestorationException(id+": Trouble restoring data file: "+ex.getMessage(), ex);
        }
        finally {
            if (bstrm != null) {
                try { bstrm.close(); }
                catch (IOException ex) { }
            }
        }
    }

    /**
     * consult the given head bag and return the name of the bag that contains the file indicated by 
     * the given filepath.
     * <p>
     * A head bag provides descriptive information about the contents of a particular data collection. 
     * It includes a lookup table for determining bag file that contains a particular file from the 
     * collection.
     * @param headbag    the name of the headbag file to examine to determin file location
     * @param filepath   the path to the file within the collection described by the head bag.
     * @param id         the identifier for the collection or file within it; used in exception messages.
     * @throws ObjectNotFoundException -- if the head bag cannot be found
     * @throws RestorationException -- if there is a problem with contents of the head bag
     * @throws StorageVolumeException -- if there is a failure reading the head bag
     */
    public String findBagFor(String headbag, String filepath, String id)
        throws RestorationException, ObjectNotFoundException, StorageVolumeException
    {
        // find the cached head bag
        CacheObject hbco = null;
        try {
            hbco = hbcm.getObject(headbag);
        }
        catch (CacheManagementException ex) {
            throw new RestorationException(headbag+": Trouble retrieving headbag: "+ex.getMessage(), ex);
        }

        if (! hbco.name.endsWith(".zip"))
            throw new RestorationException("Unsupported serialization type on bag: " + hbco.name);
        String bagname = hbco.name.substring(0, hbco.name.length()-4);
        String mbagver = BagUtils.multibagVersionOf(bagname);

        // look up the bag that contains our data file
        String srcbag = null;
        InputStream hbstrm = hbco.volume.getStream(hbco.name);
        try {
            ZipBagUtils.OpenEntry ntry = ZipBagUtils.openFileLookup(mbagver, hbstrm, bagname);
            srcbag = HeadBagUtils.lookupFile(mbagver, ntry.stream, "data/"+filepath);
            if (srcbag == null) 
                throw new ObjectNotFoundException("Filepath, "+filepath+", not available from id="+id,
                                                  filepath, (String) null);
        }
        catch (IOException ex) {
            throw new RestorationException(id+": Trouble looking up file via headbag, "+headbag+": "+
                                           ex.getMessage(), ex);
        }
        finally {
            try { hbstrm.close(); }
            catch (IOException ex) {
                log.warn("Trouble closing bag file, {}: {}", hbco.name, ex.getMessage());
            }
        }
            
        return srcbag+".zip";
    }

    /**
     * cache all data that is part of a the latest version of the archive information package (AIP).
     * @param aipid    the identifier for the AIP.  
     * @return Set<String> -- a list of the filepaths for files that were cached
     */
    public Set<String> cacheDataset(String aipid, String version, Cache into)
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        // find the head bag in the bag store
        String headbag = ltstore.findHeadBagFor(aipid, version);  // throws exc if does not exist
        if (! headbag.endsWith(".zip"))
            throw new CacheManagementException("Unsupported serialization type on bag: " + headbag);
        String bagname = headbag.substring(0, headbag.length()-4);
        String mbagver = BagUtils.multibagVersionOf(bagname);
        int prefs = (version == null) ? ROLE_GENERAL_PURPOSE : ROLE_OLD_VERSIONS;

        // pull out the NERDm resource metadata record
        JSONObject resmd = hbcm.resolveAIPID(aipid, version);
        if (resmd == null) {
            if (version != null) aipid += "#"+version;
            throw new RestorationException("Failed to retrieve resource metadata for aipid="+aipid);
        }

        // pull out the mulibag file lookup
        Map<String,String> lu = null;
        CacheObject hbo = hbcm.getObject(headbag);
        InputStream hbs = hbo.volume.getStream(headbag);
        try {
            ZipBagUtils.OpenEntry ntry = ZipBagUtils.openFileLookup(mbagver, hbs, bagname);
            lu = HeadBagUtils.getFileLookup(mbagver, ntry.stream);
        }
        catch (FileNotFoundException ex) {
            throw new RestorationException(headbag + ": Head bag is missing multibag metadata!", ex);
        }
        catch (IOException ex) {
            throw new RestorationException(headbag + ": Trouble extracting multibag metadata: "+
                                           ex.getMessage(), ex);
        }

        // turn lookup into a reverse lookup
        HashMap<String, Set<String>> revlu = new HashMap<String, Set<String>>();
        for (Map.Entry<String,String> pair : lu.entrySet()) {
            if (! pair.getKey().startsWith("data/") || pair.getKey().endsWith(".sha256"))
                // only care about data files
                continue;
            if (! revlu.containsKey(pair.getValue()))
                revlu.put(pair.getValue(), new HashSet<String>());
            revlu.get(pair.getValue()).add(pair.getKey().replaceFirst("^data/", ""));
        }

        // loop through the member bags and extract the data files
        Set<String> cached = new HashSet<String>(lu.size());
        Set<String> missing = new HashSet<String>();
        for (String bagfile : revlu.keySet()) {
            Set<String> need = new HashSet<String>(revlu.get(bagfile));
            if (! bagfile.endsWith(".zip"))
                bagfile += ".zip";
            try {
                cacheFromBag(bagfile, need, cached, resmd, prefs, version, into);
            }
            catch (FileNotFoundException ex) {
                log.error("Member bag not found in store (skipping): "+bagfile);
            }
            finally {
                if (need.size() > 0) missing.addAll(need);
            }
        }

        // warn about missing files
        if (missing.size() > 0 && log.isErrorEnabled()) {
            StringBuilder sb = new StringBuilder("Failed to cache ");
            sb.append(Integer.toString(missing.size())).append(" data file");
            if (missing.size() > 1) sb.append("s");
            if (missing.size() > 5)
                sb.append(", including");
            sb.append(":");

            int i=0;
            for(String fp : missing) {
                if (++i > 5) break;   // limit listing to 5 files
                sb.append("\n   ").append(fp);
            }
            
            log.error(sb.toString());
        }

        return cached;
    }

    /**
     * cache all data found in the specified bag file
     * @param bagfile  the name the bag in the bag storage to unpack and cache
     * @param files    an editable set of names of data files to be extract from the associated bag.  The
     *                    files are specified as filepaths relative to the data directory.  This method will 
     *                    remove members from the set as the files are cached such that when this method 
     *                    returns, the set will only contain names of files that were not found in the bag.  
     *                    If null, all data files found in the bag will be extracted.
     * @param forVersion  If non-null, append this label to the cache object's ID (as in 
     *                    <code>"#"+forVersion</code>) to indicate that this file was request for a particular
     *                    version of the dataset.  This will also affect where the file gets cached.
     * @param into        The cache to cache the files into
     * @return Set<String>   a listing of data files that were cached to disk.  
     */
    public Set<String> cacheFromBag(String bagfile, Collection<String> files, String forVersion, Cache into)
        throws StorageVolumeException, FileNotFoundException, CacheManagementException
    {
        String aipid = null, version = null;
        try {
            List<String> parts = BagUtils.parseBagName(bagfile);
            aipid = parts.get(0);
            version = parts.get(1);
        }
        catch (ParseException ex) {
            throw new RestorationException("Illegal bagfile name: "+bagfile);
        }
        if (version.length() == 0)
            version = "1";

        JSONObject resmd = hbcm.resolveAIPID(aipid, version);
        int cap = 5;
        if (files != null)
            cap = files.size();
        HashSet<String> cached = new HashSet<String>(cap);
        int prefs = (forVersion == null) ? ROLE_GENERAL_PURPOSE : ROLE_OLD_VERSIONS;

        cacheFromBag(bagfile, files, cached, resmd, prefs, forVersion, into);
        return cached;
    }

    protected void cacheFromBag(String bagfile, Collection<String> need, Collection<String> cached,
                                JSONObject resmd, int defprefs, String forVersion, Cache into)
        throws StorageVolumeException, FileNotFoundException, CacheManagementException
    {
        if (! bagfile.endsWith(".zip"))
            throw new RestorationException("Unsupported serialization type on bag: " + bagfile);
        String aipid = null, version = null;
        try {
            List<String> parts = BagUtils.parseBagName(bagfile);
            aipid = parts.get(0);
            version = parts.get(1).replaceAll("_", ".");
        }
        catch (ParseException ex) {
            throw new RestorationException("Illegal bagfile name: "+bagfile);
        }
        if (version.length() == 0)
            version = "1";
        String id = null;

        int errcnt = 0, errlim = 5;
        Path fname = null;
        int prefs = defprefs;
        CacheObject co = null;

        // open up the bagfile from long-term storage (e.g AWS S3)
        InputStream fs = null;
        if (hbcm.isCached(bagfile)) {
            // it's a head bag that we have locally
            co = hbcm.getObject(bagfile);
            fs = co.volume.getStream(bagfile);
        }
        else
            fs = ltstore.openFile(bagfile);
        
        try {
            // cycle throught the contents of the zip file
            ZipInputStream zipstrm = new ZipInputStream(fs);
            ZipEntry ze = null;
            while ((ze = zipstrm.getNextEntry()) != null) {
                if (ze.isDirectory())
                    continue;
                fname = Paths.get(ze.getName()).normalize();
                prefs = defprefs;
                if ((prefs & ROLE_OLD_VERSIONS) == 0 && ze.getSize() <= smszlim)
                    prefs = ROLE_SMALL_OBJECTS;

                // pay attention only to data files under the data subdirectory
                if (! fname.subpath(1, 2).toString().equals("data") || fname.toString().endsWith(".sha256"))
                    continue;

                String filepath = fname.subpath(2, fname.getNameCount()).toString();
                if (need != null && ! need.contains(filepath))
                    continue;
                id = aipid+"/"+filepath;
                if (forVersion != null)
                    id += "#"+forVersion;

                // extract the file's metadata; convert it for storage in cache
                JSONObject md = hbcm.findComponentByFilepath(resmd, filepath);
                if (md == null) {
                    log.warn("Unable to find metadata for filepath: {}", filepath);
                    md = new JSONObject();
                    md.put("size", ze.getSize());
                }
                md = getCacheMDFrom(md);
                md.put("aipid", aipid);
                md.put("version", version);
                md.put("bagfile", bagfile);
                if (resmd.has("@id"))
                    md.put("pdrid", resmd.get("@id"));
                if (resmd.has("ediid"))
                    md.put("ediid", resmd.get("ediid"));

                // find space in the cache, and copy the data file into it
                Reservation resv = into.reserveSpace(ze.getSize(), prefs);
                co = resv.saveAs(zipstrm, id, nameForObject(aipid, filepath, forVersion, prefs), md);
                log.info("Cached "+id);

                if (need != null)
                    need.remove(filepath);
                cached.add(filepath);
            }
        }
        catch (IOException ex) {
            throw new RestorationException(bagfile+": Trouble reading bag contents: "+ex.getMessage(), ex);
        }
        finally {
            try { fs.close(); }
            catch (IOException ex) {
                log.warn("Trouble closing bag file, {}: {}", bagfile, ex.getMessage());
            }
        }
    }

    private JSONObject getCacheMDFrom(JSONObject nerdcmp) {
        JSONObject out = new JSONObject();

        if (nerdcmp.has("filepath"))
            out.put("filepath", nerdcmp.get("filepath"));
        if (nerdcmp.has("mediaType"))
            out.put("contentType", nerdcmp.get("mediaType"));
        if (nerdcmp.has("size"))
            out.put("size", nerdcmp.get("size"));
        if (nerdcmp.has("checksum") && nerdcmp.opt("checksum") != null &&
            nerdcmp.getJSONObject("checksum").has("hash") &&
            nerdcmp.getJSONObject("checksum").has("algorithm") &&
            Checksum.SHA256.equals(nerdcmp.getJSONObject("checksum")
                                          .getJSONObject("algorithm").optString("tag")))
        {
            out.put("checksum", nerdcmp.getJSONObject("checksum").getString("hash"));
            out.put("checksumAlgorithm", nerdcmp.getJSONObject("checksum")
                                                .getJSONObject("algorithm").optString("tag"));
        }
        return out;
    }

    private JSONObject getCacheMDFromHeadBag(String headbag, String filepath)
        throws ResourceNotFoundException, StorageVolumeException, CacheManagementException
    {
        if (! headbag.endsWith(".zip"))
            throw new CacheManagementException("Unsupported serialization type on bag: " + headbag);
        String bagname = headbag.substring(0, headbag.length()-4);

        try {
            CacheObject hbo = hbcm.getObject(headbag);
            JSONObject cmpmd = ZipBagUtils.getFileMetadata(filepath, hbo.volume.getStream(headbag), bagname);
                                                           
            return getCacheMDFrom(cmpmd);
        }
        catch (FileNotFoundException ex) {
            throw new RestorationException("file metadata for "+filepath+" not found in headbag, "+headbag);
        }
        catch (IOException ex) {
            throw new RestorationException(headbag+": Trouble reading file metadata for "+filepath+
                                           ex.getMessage(), ex);
        }
    }

    /**
     * return a recommended name for the object with the given id that can be used as its name in a 
     * cache volume.  
     * @throws StorageVolumeException -- if an exception occurs while consulting the underlying storage system
     * @throws RestorationException -- if some other error occurs while (e.g. the ID is not valid)
     */
    @Override
    public String nameForObject(String id) {
        String[] idparts = parseId(id);
        int prefs = 0;
        if (idparts[2] != null)
            prefs = ROLE_OLD_VERSIONS;
        return nameForObject(id, prefs);
    }
    
    /**
     * return a recommended name for the object with the given id that can be used as its name in a 
     * cache volume.  
     * @throws StorageVolumeException -- if an exception occurs while consulting the underlying storage system
     * @throws RestorationException -- if some other error occurs while (e.g. the ID is not valid)
     */
    public String nameForObject(String id, int prefs) {
        String[] idparts = parseId(id);
        return nameForObject(idparts[0], idparts[1], idparts[2], prefs);
    }
    
    /**
     * return a recommended name for the object with the given id that can be used as its name in a 
     * cache volume.  
     * @throws StorageVolumeException -- if an exception occurs while consulting the underlying storage system
     * @throws RestorationException -- if some other error occurs while (e.g. the ID is not valid)
     */
    public String nameForObject(String aipid, String filepath, String version, int prefs) {
        if ((prefs & ROLE_OLD_VERSIONS) > 0)
            return getNameForOldVerCache(aipid, filepath, version);
        return aipid + "/" + filepath;
    }
    
    final String getNameForOldVerCache(String aipid, String filepath, String version) {
        String ext = FilenameUtils.getExtension(filepath);
        String base = filepath.substring(0, filepath.length()-ext.length()-1);
        String exttp = ext.toLowerCase();
        if (exttp.equals("gz") || exttp.equals("bz") || exttp.equals("xz")) {
            String ext2 = FilenameUtils.getExtension(base);
            if (ext2.length() > 0 && ext2.length() <= 4) {
                base = base.substring(0, base.length()-ext2.length()-1);
                ext = ext2+"."+ext;
            }
        }
        filepath = base + "-v" + version + "." + ext;
        return nameForObject(aipid, filepath, version, 0);
    }

    /**
     * return an IntegrityMonitor instance that is attached to the internal head bag cache and that can 
     * be used to test the integrity of objects in that cache against a specific list of checks.
     */
    public IntegrityMonitor getIntegrityMonitor(List<CacheObjectCheck> checks) {
        return hbcm.getIntegrityMonitor(checks);
    }
}
