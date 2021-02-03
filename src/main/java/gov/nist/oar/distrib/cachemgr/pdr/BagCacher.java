/*
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
import gov.nist.oar.bags.preservation.HeadBagUtils;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageStateException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.NoMatchingVolumesException;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.RestorationException;
import gov.nist.oar.distrib.cachemgr.Restorer;
import gov.nist.oar.distrib.cachemgr.Reservation;
import gov.nist.oar.distrib.cachemgr.BasicCache;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.BagStorage;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import java.util.List;
import java.text.ParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONTokener;
import org.json.JSONException;

import org.apache.commons.io.FilenameUtils;

/**
 * A machine for restoring data from archive bags in long-term storage into cache.
 * <p>
 * The typical use is to pass in a dataset's AIP-ID to an instance of this class (via 
 * {@link #cacheDataset(String)}) to have all data files from that dataset pushed into cache.  
 */
public class BagCacher implements PDRCacheRoles {

    BasicCache cache = null;
    BagStorage bagstore = null;
    long smszlim = 100000000L;  // 100 MB
    MetadataCache mdcache = null;
    Logger log = null;

    /**
     * Create the cacher
     */
    public BagCacher(BasicCache cache, BagStorage bagstore, MetadataCache mdcache,
                     long smallsizelim, Logger logger)
    {
        this(cache, bagstore, mdcache, logger);
        smszlim = smallsizelim;
    }

    /**
     * Create the cacher
     */
    public BagCacher(BasicCache cache, BagStorage bagstore, MetadataCache mdcache, Logger logger) {
        this.cache = cache;
        this.bagstore = bagstore;
        this.mdcache = mdcache;
        if (logger == null)
            logger = LoggerFactory.getLogger("BagCacher");
        log = logger;
    }

    /**
     * cache all data that is part of a particular archive information package (AIP).
     * @param aipid    the identifier for the AIP.  
     * @param version  the version of the AIP that should 
     */
    public Set<String> cacheDataset(String aipid, String version)
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        if (version == null)
            // latest requested
            return cacheDataset(aipid);

        int prefs = ROLE_OLD_VERSIONS;
        String headbag = bagstore.findHeadBagFor(aipid, version);
        try {
            List<String> parts = BagUtils.parseBagName(headbag);
            String sertype = parts.get(4);

            return cacheDatasetFromHeadBag(aipid, version, headbag, sertype, prefs);
        } catch (ParseException ex) {
            throw new CacheManagementException("Found head-bag with illegal filename: "+headbag);
        } catch (FileNotFoundException ex) {
            throw new CacheManagementException("Head bag unexpectedly not found in store: "+headbag);
        } 
    }

    /**
     * cache all data that is part of a the latest version of the archive information package (AIP).
     * @param aipid    the identifier for the AIP.  
     */
    public Set<String> cacheDataset(String aipid)
        throws StorageVolumeException, ResourceNotFoundException, CacheManagementException
    {
        int prefs = ROLE_GENERAL_PURPOSE;
        String headbag = bagstore.findHeadBagFor(aipid);  // may throw ResoruceNotFoundException
        try {
            List<String> parts = BagUtils.parseBagName(headbag);
            String version = parts.get(1).replaceAll("_", ".");
            String sertype = parts.get(4);
            mdcache.setLatestVersion(aipid, version);

            return cacheDatasetFromHeadBag(aipid, version, headbag, sertype, prefs);
        } catch (ParseException ex) {
            throw new CacheManagementException("Found head-bag with illegal filename: "+headbag);
        } catch (FileNotFoundException ex) {
            throw new CacheManagementException("Head bag unexpectedly not found in store: "+headbag);
        } catch (IOException ex) {
            throw new CacheManagementException("Problem using metadata cache: "+ex.getMessage(), ex);
        } 
    }

    protected Set<String> cacheDatasetFromHeadBag(String aipid, String version, String headbag, String ser,
                                                  int defprefs)
        throws StorageVolumeException, FileNotFoundException, CacheManagementException
    {
        int prefs = defprefs;

        // start by getting the metadata from the head bag
        InputStream fs = bagstore.openFile(headbag);
        try {
            // we extract only metadata on this visit
            cacheFromBag(aipid, version, fs, ser, 0, null, null);
        }
        finally {
            try { fs.close(); }
            catch (IOException ex) {
                log.warn("Trouble closing headbag file, {}: {}", headbag, ex.getMessage());
            }
        }

        Set<String> cached = new HashSet<String>();
        Set<String> missing = new HashSet<String>(2);
        try {
            // Now cache data files from other member bags
            Collection<String> got = null, need = null;
            Collection<String> bags = mdcache.getMemberBags(aipid, version);
            
            // if (bags.size() < 2 && (defprefs & ROLE_OLD_VERSIONS) == 0)
            //    prefs = ROLE_SMALL_OBJECTS;
                
            for (String member : bags) {
                need = mdcache.getDataFilesInBag(aipid, version, member);
                if (need.size() == 0)
                    continue;
                try {
                    /*
                     * use this to select any supported format
                    member = bagstore.getSerializationsForBag(member).stream()
                        .filter(b -> b.endsWith(".zip"))
                        .findFirst().orElse(null);
                    if (member == null)
                        throw new FileNotFoundException()
                     *
                     * going with simple implementation for now
                     */
                    if (! member.endsWith(".zip"))
                        member = member+".zip";
                    fs = bagstore.openFile(member);
                    got = new HashSet<String>();
                    try {
                        cacheFromBag(aipid, version, fs, ser, prefs, need, got);
                    }
                    finally {
                        try { fs.close(); }
                        catch (IOException ex) {
                            log.warn("Trouble closing bag file, {}: {}", member, ex.getMessage());
                        }
                    }
                }
                catch (FileNotFoundException ex) {
                    log.error("Member bag not found in store (skipping): "+member);
                }
                finally {
                    if (need.size() > 0) missing.addAll(need);
                    updateMetadataFor(aipid, version, got);
                    cached.addAll(got);
                }
            }

            mdcache.forget(aipid, version);
        } catch (InventoryException ex) {
            throw new CacheManagementException("Problem updating inventory with metadata: "+
                                               ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new CacheManagementException("Problem getting info from metadata cache: "+
                                               ex.getMessage(), ex);
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
       
    protected void updateMetadataFor(String aipid, String version, Collection<String> cached)
        throws InventoryException
    {
        StorageInventoryDB db = cache.getInventoryDB();
        for (String filepath : cached) {
            try {
                JSONObject md = mdcache.getMetadataForCache(aipid, filepath, version);
                if (md != null) {
                    String objid = getIdForCache(aipid, filepath, version);
                    List<CacheObject> copies = db.findObject(objid);
                    if (copies.size() > 0) {
                        for (CacheObject obj : copies) 
                            db.updateMetadata(obj.volname, obj.name, md);
                    }
                    else {
                        log.error("Couldn't find data file in cache: "+objid);
                    }
                }
            } catch (IOException ex) {
                log.error("Unable to read metadata from cache for {}/{}#{}: {}",
                          aipid, filepath, version, ex.getMessage());
            } catch (JSONException ex) {
                log.error("Unable to parse metadata from cache for {}/{}#{}: {}",
                          aipid, filepath, version, ex.getMessage());
            } 
        }
    }        

    /**
     * cache all data found in the specified bag file
     * @param bagfile  the name the bag in the bag storage to unpack and cache
     * @param files    an editable set of names of data files to be extract from the associated bag.  The
     *                    files are specified as filepaths relative to the data directory.  This method will 
     *                    remove members from the set as the files are cached such that when this method 
     *                    returns, the set will only contain names of files that were not found in the bag.  
     *                    If null, all data files found in the bag will be extracted.
     * @return Set<String>   a listing of data files that were cached to disk.  
     */
    public Set<String> cacheFromBag(String bagfile, Collection<String> files)
        throws StorageVolumeException, FileNotFoundException, CacheManagementException
    {
        try {
            List<String> baginfo = BagUtils.parseBagName(bagfile);
            String aipid = baginfo.get(0);
            String vers = baginfo.get(1).replaceAll("_", ".");
            if (vers.length() == 0)
                vers = "1.0.0";
            String ser = baginfo.get(4);
            int prefs = ROLE_GENERAL_PURPOSE;

            int cap = 5;
            if (files != null)
                cap = files.size();
            HashSet<String> cached = new HashSet<String>(cap);
            
            InputStream fs = bagstore.openFile(bagfile);
            try {
                cacheFromBag(aipid, vers, fs, ser, prefs, files, cached);
                return cached;
            }
            finally {
                try { fs.close(); }
                catch (IOException ex) {
                    log.warn("Trouble closing bag file, {}: {}", bagfile, ex.getMessage());
                }
            }
        }
        catch (ParseException ex) {
            throw new IllegalArgumentException("Provided file name is not a legal bagfile name: "+bagfile);
        }
    }

    /**
     * cache all data found in the given stream
     * @param aipid    the identifier for the AIP.  
     * @param version  the version of the AIP that should 
     * @param in       the open bag file stream
     * @param sertype  the bag's serialization file extension
     * @param files    an editable set of names of data files to be extract from the associated bag.  The
     *                    files are specified as filepaths relative to the data directory.  This method will 
     *                    remove members from the set as the files are cached such that when this method 
     *                    returns, the set will only contain names of files that were not found in the bag.  
     *                    If null, all data files found in the bag will be extracted.
     * @param got      an editable set of name; this function will add the names of the files successfully
     *                    cached from the bag.
     */
    protected void cacheFromBag(String aipid, String version, InputStream in, String sertype,
                                int defprefs, Collection<String> files, Collection<String> got)
        throws StorageVolumeException, CacheManagementException
    {
        if (sertype.equals("zip"))
            cacheFromZipBag(aipid, version, in, defprefs, files, got);
        else
            throw new CacheManagementException("Unsupported bag serialization for id="+aipid+": "+sertype);
    }

    protected void cacheFromZipBag(String aipid, String version, InputStream in, int defprefs,
                                   Collection<String> need, Collection<String> cached)
        throws StorageVolumeException, CacheManagementException
    {
        int errcnt = 0, errlim = 5;
        Path fname = null;
        int prefs = defprefs;
        try {
            ZipInputStream zipstrm = new ZipInputStream(in);
            ZipEntry ze = zipstrm.getNextEntry();
            while (ze != null) {
                if (! ze.isDirectory()) {
                    fname = Paths.get(ze.getName()).normalize();
                    prefs = defprefs;
                    if ((prefs & ROLE_OLD_VERSIONS) == 0 && ze.getSize() <= smszlim)
                        prefs = ROLE_SMALL_OBJECTS;
                    if (fname.getRoot() != null) 
                        throw new CacheManagementException("Zipped bag file contains unexpected root: "+
                                                           fname.getRoot());
                    try {
                        handleBagFile(aipid, version, fname, ze.getSize(), zipstrm, prefs, need, cached);
                    }
                    catch (JSONException ex) {
                        log.error("Problem parsing JSON file="+fname+" from zipped bag: "+ex.getMessage(), ex);
                        errcnt++;
                    }
                    catch (CacheManagementException ex) {
                        log.error("Problem processing file="+fname+" from zipped bag: "+ex.getMessage(), ex);
                        errcnt++;
                    }
                    if (errcnt >= errlim)
                        throw new CacheManagementException("Too many file processing errors from zipped bag");
                }
                ze = zipstrm.getNextEntry();
            }
        }
        catch (IOException ex) {
            throw new StorageVolumeException("Problem reading bag file: "+ex.getMessage(), ex);
        }
    }

    /**
     * process a file contained in a bag.  The type of processing done depends on the value of 
     * {@code cached}: if null, the caller is requesting just metadata; otherwise, the caller is 
     * requesting data.  
     * @param aipid    the identifier for the AIP.  
     * @param version  the version of the AIP that should 
     * @param in       the open bag file stream
     * @param defprefs caching preferences, given as a bit-ANDed list (see {@link PDRCacheRoles}).
     * @param need     an editable set of names of data files to be extract from the associated bag.  The
     *                    files are specified as filepaths relative to the data directory.  When 
     *                    {@code cached} is not null, this method will remove members from the set as the 
     *                    files are cached such that when this method returns, the set will only contain 
     *                    names of files that were not found in the bag.  If null, all data files found 
     *                    in the bag will be extracted.  This parameter is ignored and left unchanged if 
     *                    {@code cached} is null.
     * @param cached   an editable set of strings.  If not null, the filepaths to data files will be 
     *                    added to this set as they are cached.  When {@code cached} is null, this method
     *                    will assume that only metadata is desired from the bag.  
     */
    protected void handleBagFile(String aipid, String version, Path fname, long size, InputStream in, 
                                 int defprefs, Collection<String> need, Collection<String> cached)
        throws IOException, CacheManagementException
    {
        if (fname.getNameCount() < 2) 
            return;

        if (cached != null) {
            // data files are desired
            if (fname.subpath(1, 2).startsWith("data") && ! fname.toString().endsWith(".sha256")) {
                fname = fname.subpath(2, fname.getNameCount());
                cacheDataFile(aipid, version, fname, size, in, defprefs, need, cached);
            }
        }
        else {
            // only metadata files are desired
            if (fname.subpath(1, 2).startsWith("metadata") && fname.endsWith("nerdm.json")) {
                ingestNERDmFile(aipid, version, fname, in);
            }
            else if (fname.endsWith(HeadBagUtils.FILE_LOOKUP)) {
                ingestFileLookup(aipid, version, in);
            }
            else if (fname.endsWith(HeadBagUtils.FILE_LOOKUP_V02)) {
                ingestFileLookupV02(aipid, version, in);
            }
        }
    }

    protected void ingestFileLookup(String aipid, String version, InputStream in)
        throws IOException
    {
        MetadataCache.FileLookup lu = mdcache.getFileLookup(aipid, version);
        BufferedReader bs = new BufferedReader(new InputStreamReader(in));
        String line = null;
        String[] flds = null;
        Path bagpath = null;
        try {
            while ((line = bs.readLine()) != null) {
                flds = line.split("\t");
                bagpath = Paths.get(flds[0]);
                if (bagpath.startsWith("data") && ! bagpath.toString().endsWith(".sha256")) {
                    bagpath = bagpath.subpath(1, bagpath.getNameCount());
                    lu.map(bagpath.toString(), flds[1]);
                }
            }
        }
        finally {
            lu.close();
        }
    }

    protected void ingestFileLookupV02(String aipid, String version, InputStream in)
        throws IOException
    {
        MetadataCache.FileLookup lu = mdcache.getFileLookup(aipid, version);
        BufferedReader bs = new BufferedReader(new InputStreamReader(in));
        String line = null;
        String[] flds = null;
        Path bagpath = null;
        try {
            while ((line = bs.readLine()) != null) {
                flds = line.split(" +");
                bagpath = Paths.get(flds[0]);
                if (bagpath.startsWith("data") && ! bagpath.toString().endsWith(".sha256")) {
                    bagpath = bagpath.subpath(1, bagpath.getNameCount());
                    lu.map(bagpath.toString(), flds[1]);
                }
            }
        }
        finally {
            lu.close();
        }
    }

    protected void ingestNERDmFile(String aipid, String version, Path filename, InputStream in)
        throws IOException, JSONException
    {
        JSONObject nerd = new JSONObject(new JSONTokener(in));
        if (! nerd.has("@type")) {
            log.warn("NERDm record for id={}#{} is missing @type property (skipping)",
                     aipid, version);
            return;
        }
        if (isDataFileType(nerd)) {
            String file = nerd.optString("filepath");
            if (file == null) {
                log.warn("NERDm DataFile record for id={}#{} ({}) is missing filepath property (skipping)",
                         aipid, version, filename);
                return;
            }

            JSONObject save = new JSONObject();
            if (nerd.has("filepath"))
                save.put("filepath", nerd.get("filepath"));
            if (nerd.has("mediaType"))
                save.put("contentType", nerd.get("mediaType"));
            // if (nerd.has("size"))
            //     save.put("size", nerd.get("size"));
            if (nerd.has("checksum") && nerd.opt("checksum") != null &&
                nerd.getJSONObject("checksum").has("hash") && nerd.getJSONObject("checksum").has("algorithm") &&
                Checksum.SHA256.equals(nerd.getJSONObject("checksum")
                                           .getJSONObject("algorithm").optString("tag")))
            {
                save.put("checksum", nerd.getJSONObject("checksum").getString("hash"));
                save.put("checksumAlgorithm", nerd.getJSONObject("checksum")
                                                  .getJSONObject("algorithm").optString("tag"));
            }
            save.put("aipid", aipid);
            save.put("version", version);
            
            mdcache.cacheFileMetadata(aipid, version, save);
        }
    }

    boolean isDataFileType(JSONObject cmp) {
        JSONArray types = cmp.optJSONArray("@type");
        if (types == null) return false;

        for(Object el : types) {
            try {
                if (((String) el).endsWith(":DataFile"))
                    return true;
            } catch (ClassCastException ex) { }
        }
        return false;
    }

    protected void cacheDataFile(String aipid, String version, Path filepath, long size, InputStream in, 
                                 int prefs, Collection<String> need, Collection<String> cached)
        throws IOException, CacheManagementException
    {
        if (need != null && ! need.contains(filepath.toString())) 
            return;

        String nm = getNameForCache(aipid, filepath.toString(), version, prefs);
        String id = getIdForCache(aipid, filepath.toString(), version);
        JSONObject md = new JSONObject();
        md.put("size", size);

        Reservation resv = null;
        try {
            resv = cache.reserveSpace(size, prefs);
        }
        catch (NoMatchingVolumesException ex) {
            if (prefs == 0 || (prefs & ROLE_GENERAL_PURPOSE) > 0)
                throw ex;
            // try again with looser preferences
            resv = cache.reserveSpace(size, ROLE_GENERAL_PURPOSE);
        }
        resv.saveAs(in, id, nm, md);
        if (need != null)
            need.remove(filepath.toString());
        cached.add(filepath.toString());
    }

    protected final String getNameForCache(String aipid, String filepath, String version, int prefs) {
        if ((prefs & ROLE_OLD_VERSIONS) > 0)
            return getNameForOldVerCache(aipid, filepath, version);
        return aipid + "/" + filepath;
    }

    protected final String getIdForCache(String aipid, String filepath, String version) {
        return aipid + "/" + filepath + "#" + version;
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
        return getNameForCache(aipid, filepath, version, 0);
    }

}
