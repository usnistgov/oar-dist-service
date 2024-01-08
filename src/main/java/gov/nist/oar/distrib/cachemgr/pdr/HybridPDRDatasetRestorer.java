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
import gov.nist.oar.distrib.cachemgr.BasicCache;
import gov.nist.oar.distrib.cachemgr.Cache;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.RestorationException;
import gov.nist.oar.distrib.cachemgr.InventoryException;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
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
public class HybridPDRDatasetRestorer extends PDRDatasetRestorer {
    BagStorage restrictedLtstore = null;

    /**
     * create the restorer
     * @param publicLtstore      the public long-term storage where the head bags are stored
     * @param restrictedLtstore  the restricted long-term storage
     * @param headbagcache  the cache to use cache the head bags
     */
    public HybridPDRDatasetRestorer(BagStorage publicLtstore, BagStorage restrictedLtstore,
                                    HeadBagCacheManager headbagcache) {
        super(publicLtstore, headbagcache, -1L);
        this.restrictedLtstore = restrictedLtstore;
    }

    /**
     * create the restorer
     * @param publicLtstore the public long-term storage where the head bags are stored
     * @param restrictedLtstore  the restricted long-term storage
     * @param headbagcache  the cache to use cache the head bags
     * @param smallsizelim  a data file size limit: files smaller than this will preferentially go
     *                        into volumes marked for small files.
     */
    public HybridPDRDatasetRestorer(BagStorage publicLtstore, BagStorage restrictedLtstore,
                                    HeadBagCacheManager headbagcache, long smallsizelim) {
        super(publicLtstore, headbagcache, smallsizelim);
        this.restrictedLtstore = restrictedLtstore;
    }

    /**
     * create the restorer
     * @param publicLtstore  the long term storage where the head bags are stored
     * @param restrictedLtstore  the restricted long-term storage
     * @param headbagcache  the cache to use cache the head bags
     * @param smallsizelim  a data file size limit: files smaller than this will preferentially go
     *                        into volumes marked for small files.
     * @param logger        the logger to use for messages.
     */
    public HybridPDRDatasetRestorer(BagStorage publicLtstore, BagStorage restrictedLtstore, HeadBagCacheManager headbagcache, long smallsizelim,
                              Logger logger)
    {
        super(publicLtstore, headbagcache, smallsizelim, logger);
        this.restrictedLtstore = restrictedLtstore;
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
            this.restrictedLtstore.findBagsFor(parts[0]);
            // if no exception is thrown, the object exists in the restricted storage
            return false;
        } catch (ResourceNotFoundException e) {
            // if object not found in the restricted storage, check the public storage
            try {
                this.ltstore.findBagsFor(parts[0]);
                // if no exception is thrown, the object exists in the public storage
                return false;
            } catch (ResourceNotFoundException ex) {
                // object not found in either storage
                return true;
            }
        }
    }

    /**
     * restore the identified object to the CacheVolume associated with the given Reservation,
     * first trying the restricted storage, then the public storage.
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
            throws RestorationException, StorageVolumeException, JSONException {

        try {
            restoreObjectFromStore(id, resv, name, metadata, restrictedLtstore);
        } catch (ObjectNotFoundException ex) {
            restoreObjectFromStore(id, resv, name, metadata, ltstore);
        }
    }

    @Override
    protected InputStream openBag(String bagfilename) throws FileNotFoundException, StorageVolumeException {
        try {
            return ltstore.openFile(bagfilename);
        } catch (FileNotFoundException ex) {
            return restrictedLtstore.openFile(bagfilename);
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
     * @param version  the version of the AIP to cache.  If null, the latest is cached.
     * @param into     the Cache to save the files to
     * @param recache  if false and a file is already in the cache, the file will not be rewritten;
     *                    otherwise, it will be.
     * @return Set<String> -- a list of the filepaths for files that were cached
     */
    @Override
    public Set<String> cacheDataset(String aipid, String version, Cache into, boolean recache, int prefs, String target)
            throws StorageVolumeException, ResourceNotFoundException, CacheManagementException {

        Set<String> cachedFiles;

        try {
            cachedFiles = cacheDatasetFromStore(aipid, version, into, recache, prefs, target,
                                                restrictedLtstore);
        } catch (ResourceNotFoundException ex) {
            cachedFiles = cacheDatasetFromStore(aipid, version, into, recache, prefs, target,
                                                ltstore);
        }

        return cachedFiles;
    }

    protected int getDefaultPrefs(boolean versionSpecific) {
        return (versionSpecific) ? ROLE_OLD_RESTRICTED_DATA : ROLE_RESTRICTED_DATA;
    }

    @Override
    protected void cacheFromBag(String bagfile, Collection<String> need, Collection<String> cached,
                                JSONObject resmd, int defprefs, String forVersion, Cache into,
                                boolean recache, String target)
            throws StorageVolumeException, FileNotFoundException, CacheManagementException
    {
        try {
            cacheFromBagUsingStore(bagfile, need, cached, resmd, defprefs, forVersion, into, recache,
                                   target, restrictedLtstore);
        } catch (FileNotFoundException e) {
            // If the bag file is not found in the restricted storage, try the public storage.
            cacheFromBagUsingStore(bagfile, need, cached, resmd, defprefs, forVersion, into, recache,
                                   target, ltstore);
        }
    }
}
