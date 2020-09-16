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
 * @author: Deoyani Nandrekar-Heinis
 */
package gov.nist.oar.distrib.storage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.StorageStateException;
import gov.nist.oar.bags.preservation.BagUtils;

/**
 * a abstract base class that implements the {@link gov.nist.oar.distrib.BagStorage} interface, 
 * assuming the bag naming conventions of the NIST Public Data Repository (PDR).  These 
 * conventions are encapsulated in the {@link gov.nist.oar.bags.preservation.BagUtils BagUtils} class.  
 */
public abstract class PDRBagStorageBase implements BagStorage {

    /** the logger instance to use */
    protected Logger logger = null;

    private String _name = null;

    /**
     * initialize the base class with a class-specific logger
     */
    public PDRBagStorageBase(String name) {
        this(name, null);
    }

    /**
     * initialize the base class with a given logger
     * @param log      a Logger to use; if null, a default is created.
     */
    public PDRBagStorageBase(String name, Logger log) {
        _name = name;
        if (log == null)
            log = LoggerFactory.getLogger(getClass());
        logger = log;
    }

    /**
     * return a name for the storage system.  This is used primarily for enhancing error messages
     * by indicating which storage system produced the error.
     */
    @Override
    public String getName() {  return _name;  }

    /**
     * read the hash from an open hash file.  
     * <p>
     * A hash file should contain the hexidecimal hash as the first space-delimited word in the file.
     * (This keeps it compatible with hash files created by the *nix sha256sum command.)  The caller 
     * is responsible for closing the Reader stream.  
     * @param hashfile    a Reader opened to the beginn
     */
    protected String readHash(Reader hashfile) throws StorageStateException {
        // file contains a string (hexidecimal) hash as its first word.
        BufferedReader hs = null;

        try {
            hs = new BufferedReader(hashfile);
            String out = hs.readLine();
            if (out == null || out.length() == 0)
                throw new StorageStateException("Hash missing form checksum file: "+hashfile);
            out = out.split("\\s")[0];
            if (out.length() == 0)
                throw new StorageStateException("Hash missing form checksum file: "+hashfile);
            return out;
        }
        catch (IOException ex) {
            throw new StorageStateException("Unexpected IO error: " + ex.getMessage(), ex);
        }
    }

    /**
     * Return the head bag associated with the given ID
     * @param identifier  the AIP identifier for the desired data collection 
     * @return String, the head bag's file name
     * @throws ResourceNotFoundException   if there exist no bags with the given identifier
     */
    @Override
    public String findHeadBagFor(String identifier)
        throws ResourceNotFoundException, StorageVolumeException
    {
        return BagUtils.findLatestHeadBag(this.findBagsFor(identifier));
    }

    /**
     * Return the name of the head bag for the identifier for given version
     * @param identifier  the AIP identifier for the desired data collection 
     * @param version     the desired version of the AIP
     * @return String, the head bag's file name, or null if version is not found
     * @throws ResourceNotFoundException   if there exist no bags with the given identifier or version
     */
    @Override
    public String findHeadBagFor(String identifier, String version)
        throws ResourceNotFoundException, StorageVolumeException
    {
        List<String> bags = BagUtils.selectVersion(findBagsFor(identifier), version);
        if (bags.size() == 0)
            throw ResourceNotFoundException.forID(identifier, version);
        return BagUtils.findLatestHeadBag(bags);
    }

    /**
     * Return the names of the serialized bag files for the given bag name.  The input name is the bag's 
     * root directory, and each name in the output collection will be (typically) be the root directory 
     * name appended with a file extension indicating the serialization method that applied to the bag.
     * Normally, the output collection will contain only one filename; however, in principle, multiple,
     * different serializations may be available for a bag. 
     * <p>
     * This implementation requires that the input name be a legal bagname or it will throw a 
     * FileNotFoundException.  
     */
    @Override
    public Collection<String> getSerializationsForBag(String bagname)
        throws FileNotFoundException, StorageVolumeException
    {
        if (! BagUtils.isLegalBagName(bagname))
            throw new FileNotFoundException(bagname+" (not a legal bag name)");
        try {
            // we happen to know that subclass implementations of findBagsFor() will work for this, too.
            return findBagsFor(bagname);
        }
        catch (ResourceNotFoundException ex) {
            throw new FileNotFoundException(bagname);
        }
    }
}
