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
package gov.nist.oar.distrib.cachemgr.pdr;

import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.cachemgr.Restorer;
import gov.nist.oar.distrib.cachemgr.restore.FileCopyRestorer;
import gov.nist.oar.distrib.cachemgr.RestorationException;
import gov.nist.oar.distrib.cachemgr.Reservation;
import gov.nist.oar.bags.preservation.BagUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.FileNotFoundException;

import org.json.JSONObject;
import org.json.JSONException;

/**
 * a Restorer specifically for retrieving PDR head bags from one or more long-term bag stores.
 *
 * The model for this implementation assumes that head bags for different versions of a dataset can be 
 * distributed across multiple long-term storage locations.  This enables support for restricted public 
 * data in which some head bags will be in a restricted location and some may be in a public bag store. 
 * In the PDR, each bag is stored as a file that can be simply copied from long-term store into a 
 * cache location.  
 */
public class HeadBagRestorer implements Restorer {

    List<BagStorage> bagstores = null;

    /**
     * wrap the restorer around a list of storage locations.  The order of the stores in the given 
     * list will be the order they will be searched for head bags.
     */
    public HeadBagRestorer(List<BagStorage> stores) {
        bagstores = stores;
    }

    /**
     * wrap the restorer around a single storage location
     */
    public HeadBagRestorer(BagStorage store) {
        this(Arrays.asList(store));
    }

    /**
     * wrap the restorer around a pair of storage locations
     */
    public HeadBagRestorer(BagStorage first, BagStorage second) {
        this(Arrays.asList(first, second));
    }

    /**
     * return the name of the head bag for the latest version of the dataset with the given identifier 
     */
    public String findHeadBagFor(String aipid) 
        throws ResourceNotFoundException, StorageVolumeException
    {
        ArrayList<String> bagnames = new ArrayList<String>(2);
        
        ResourceNotFoundException notfound = null;
        for (BagStorage store : bagstores) {
            try {
                bagnames.add(store.findHeadBagFor(aipid));
            }
            catch (ResourceNotFoundException ex) { /* continue */ }
        }
        if (bagnames.size() == 0)
            throw ResourceNotFoundException.forID(aipid, null);

        return BagUtils.findLatestHeadBag(bagnames);
    }

    /**
     * return the name of the head bag for the dataset with the given identifier and version
     */
    public String findHeadBagFor(String aipid, String version)
        throws ResourceNotFoundException, StorageVolumeException
    {
        if (version == null || version.length() == 0)
            return findHeadBagFor(aipid);

        for (BagStorage store : bagstores) {
            try {
                return store.findHeadBagFor(aipid, version);
            }
            catch (ResourceNotFoundException ex) { /* continue */ }
        }
        throw ResourceNotFoundException.forID(aipid, version);
    }

    protected Restorer getRestorerFor(BagStorage store) {
        return new FileCopyRestorer(store);
    }

    /**
     * return the long-term storage that contains the head bag with the given identifier and version
     * @throws ObjectNotFoundException  if the named bag file does not exist in any of the stores.
     */
    protected BagStorage findStorageContaining(String bagfilename) throws StorageVolumeException {
        for (BagStorage store : bagstores) {
            if (store.exists(bagfilename))
                return store;
        }
        throw new ObjectNotFoundException(bagfilename);
    }

    @Override
    public boolean doesNotExist(String bagfilename) throws StorageVolumeException {
        for (LongTermStorage store : bagstores) {
            if (store.exists(bagfilename))
                return false;
        }
        return true;
    }

    @Override
    public Checksum getChecksum(String bagfilename) throws StorageVolumeException {
        try {
            return findStorageContaining(bagfilename).getChecksum(bagfilename);
        }
        catch (FileNotFoundException ex) {
            // should not happen as findStorageContaining() will raise ObjectNotFoundException
            throw new RuntimeException(bagfilename+": Failed to find bag in store that owns it.");
        }
    }

    @Override
    public long getSizeOf(String bagfilename) throws StorageVolumeException {
        try {
            return findStorageContaining(bagfilename).getSize(bagfilename);
        }
        catch (FileNotFoundException ex) {
            // should not happen as findStorageContaining() will raise ObjectNotFoundException
            throw new RuntimeException(bagfilename+": Failed to find bag in store that owns it.");
        }
    }

    @Override
    public String nameForObject(String bagfilename) {
        return bagfilename;
    }

    @Override
    public void restoreObject(String bagfilename, Reservation resv, String name, JSONObject metadata)
        throws StorageVolumeException, RestorationException, JSONException
    {
        BagStorage store = findStorageContaining(bagfilename);
        getRestorerFor(store).restoreObject(bagfilename, resv, name, metadata);
    }
}
