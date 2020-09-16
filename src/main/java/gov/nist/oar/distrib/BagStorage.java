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
 */
package gov.nist.oar.distrib;

import java.util.List;
import java.util.Collection;
import java.io.FileNotFoundException;

/**
 * an extended interface for a {@link LongTermStorage} used to store bags that serve as parts of 
 * <i>archive information packages</I> (AIPs).
 * <p>
 * This extension to the {@link LongTermStorage} interface assumes that an AIP is identified by a
 * repository-unique AIP identifier.  An implementation of this interface is expected to be based 
 * on assumptions for how bags are named and organized in the storage or, otherwise, on infrastructure 
 * for finding the bag files within the storage.  That is, to retrieve a bag, one must learn what it's 
 * file name is; methods like {@link #findBagsFor(String) findBagsFor()} and 
 * {@link #findHeadBagFor(String, String) findHeadBagFor()} can map an AIP ID to bag names.  In 
 * particular, each version of an AIP has, among its bags, a designated "head bag", which contains 
 * important AIP metadata and information about the other bags that are part of the AIP.  
 * The {@link #findHeadBagFor(String, String) findHeadBagFor()} will return the filename for the 
 * serialized head bag for the AIP with a given identifier and version.  To determine the name of other 
 * (non-bag) types of files, one must have an "out-of-band" mechanism.
 * <p>
 * The {@link gov.nist.oar.distrib.storage} sub-package provides implementations based on assumptions 
 * of how the NIST Public Data Repository (PDR) stores bag files in long-term storage (via 
 * {@lnk gov.nist.gov.oar.distrib.storage.PDRBagStorage}.  
 * 
 */
public interface BagStorage extends LongTermStorage {

    /**
     * Return all the bags associated with the given ID
     * @param identifier  the AIP identifier for the desired data collection 
     * @return List<String>, the file names for all bags associated with given ID
     * @throws ResourceNotFoundException   if there exist no bags with the given identifier
     */
    public List<String> findBagsFor(String identifier) throws ResourceNotFoundException, StorageVolumeException;

    /**
     * Return the head bag associated with the given ID
     * @param identifier  the AIP identifier for the desired data collection 
     * @return String, the head bag's file name
     * @throws ResourceNotFoundException   if there exist no bags with the given identifier
     */
    public String findHeadBagFor(String identifier) throws ResourceNotFoundException, StorageVolumeException; 

    /**
     * Return the name of the head bag for the identifier for given version
     * @param identifier  the AIP identifier for the desired data collection 
     * @param version     the desired version of the AIP
     * @return String, the head bag's file name
     * @throws ResourceNotFoundException   if there exist no bags with the given identifier or version
     */
    public String findHeadBagFor(String identifier, String version)
        throws ResourceNotFoundException, StorageVolumeException;

    /**
     * Return the names of the serialized bag files for the given bag name.  The input name is the bag's 
     * root directory, and each name in the output collection will be (typically) be the root directory 
     * name appended with a file extension indicating the serialization method that applied to the bag.
     * Normally, the output collection will contain only one filename; however, in principle, multiple,
     * different serializations may be available for a bag. 
     */
    public Collection<String> getSerializationsForBag(String bagname)
        throws FileNotFoundException, StorageVolumeException;
}
    
