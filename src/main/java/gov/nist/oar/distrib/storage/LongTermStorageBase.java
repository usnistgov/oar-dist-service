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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageStateException;
import gov.nist.oar.bags.preservation.BagUtils;

/**
 * An abstract base class collecting common method implementations for the LongTermStorage interface.
 * It assumes the bag naming conventions that are encapsulted in the 
 * {@link gov.nist.oar.bags.preservation.BagUtils BagUtils} class to implement the functions for
 * finding head bags.  
 * <p>
 * This sets a Logger instance to use that is based on the implementing class (not this base class).
 *
 * @see gov.nist.oar.distrib.LongTermStorage
 * @author Raymond Plante
 */
public abstract class LongTermStorageBase implements LongTermStorage {

    protected Logger logger = null;

    /**
     * initialize the base class with a class-specific logger
     */
    public LongTermStorageBase() {
        logger = LoggerFactory.getLogger(getClass());
    }

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
     * calculate the SHA-256 checksum of a file.  This is normally only called when the checksum is not cached.
     *
     * @param filename    the name of the file within the storage whose checksum is desired.
     */
    protected String calcSHA256(String filename) throws StorageStateException, IOException {
        MessageDigest md = null;
        
        try (InputStream ds = openFile(filename)) {
            md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[50000];
            int nr = 0;
            while ( (nr = ds.read(buf)) >= 0 ) 
                md.update(buf, 0, nr);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("Unexpected Java configuration: SHA-256 algorithm not supported!");
        }

        return bytesToHex(md.digest());
    }

    private static String bytesToHex(byte[] hash) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Return the head bag associated with the given ID
     * @param identifier  the AIP identifier for the desired data collection 
     * @return String, the head bag's file name
     * @throws ResourceNotFoundException   if there exist no bags with the given identifier
     */
    @Override
    public String findHeadBagFor(String identifier)
        throws ResourceNotFoundException, StorageStateException
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
        throws ResourceNotFoundException, StorageStateException
    {
        List<String> bags = BagUtils.selectVersion(findBagsFor(identifier), version);
        if (bags.size() == 0)
            throw ResourceNotFoundException.forID(identifier, version);
        return BagUtils.findLatestHeadBag(bags);
    }
}

    

    
