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
package gov.nist.oar.distrib.service;

import gov.nist.oar.distrib.FileDescription;
import gov.nist.oar.distrib.BagDescription;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.TreeSet;
import java.text.ParseException;
import javax.activation.MimetypesFileTypeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.StreamHandle;
import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.service.PreservationBagService;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.bags.preservation.BagUtils;

/**
 * A PreservationBagService implementation that leverages default AIP storage and naming conventions.  
 * <p>
 * In the OAR PDR model, an <em>archive information package</em> (AIP) is made up of one or more files.  
 * Together, these make up the AIP.  In the default storage model, the AIP files are serialized BagIt
 * bags that conform to the Multibag BagIt Profile.  These bags are stored in a single 
 * {@link gov.nist.oar.distrib.LongTermStorage LongTermStorage} system using a naming convention 
 * encapsulated in the {@link gov.nist.oar.bags.preservation.BagUtils BagUtils} class.  
 * <p>
 * This implementation leverages the naming conventions to provide otherwise generic access to 
 * AIP files.  It is agnostic as to how the files are stored, as it is given a 
 * {@link gov.nist.oar.distrib.LongTermStorage LongTermStorage} to use. 
 */
public class DefaultPreservationBagService implements PreservationBagService {

    protected static Logger logger = LoggerFactory.getLogger(PreservationBagService.class);
    protected LongTermStorage storage = null;

    protected MimetypesFileTypeMap typemap = null;

    /**
     * create the service instance
     */
    public DefaultPreservationBagService(LongTermStorage stor, MimetypesFileTypeMap mimemap) {
        if (stor == null)
            throw new IllegalArgumentException("DefaultPreservationBagService: stor cannot be null");
        storage = stor;
        if (mimemap == null) {
            InputStream mis = getClass().getResourceAsStream("/mime.types");
            mimemap = (mis == null) ? new MimetypesFileTypeMap()
                                    : new MimetypesFileTypeMap(mis);
        }
        typemap = mimemap;
    }

    /**
     * create the service instance
     */
    public DefaultPreservationBagService(LongTermStorage stor) {
        this(stor, null);
    }

    /**
     * Returns the List of bag names associated with the AIP having the given identifier
     * @param identifier     identifier for the AIP 
     * @return List<String>, list of bags names available starting with identifier entered
     * @throws ResourceNotFoundException  if no bags are found associated with the given ID
     * @throws DistributionException      if there is unexpected, internal error
     */
    @Override
    public List<String> listBags(String identifier)
        throws ResourceNotFoundException, DistributionException
    {
        logger.debug("Get List of bags for given identifier:"+identifier);
        return  storage.findBagsFor(identifier);   
    }

    /**
     * Return the version strings for the versions available for an AIP with the given identifier
     * @param identifier     identifier for the AIP 
     * @return List<String>, list of versions available for the AIP
     * @throws ResourceNotFoundException  if no bags are found associated with the given ID
     * @throws DistributionException      if there is unexpected, internal error
     */
    public List<String> listVersions(String identifier)
        throws ResourceNotFoundException, DistributionException
    {
        List<String> bags = listBags(identifier);
        TreeSet<String> versions = new TreeSet<String>(BagUtils.versionComparator());
        String ver = null;
        for (String bag : bags) {
            try {
                ver = BagUtils.parseBagName(bag).get(1);
                if (ver.length() == 0) ver = "0";
                ver = String.join(".", ver.split("_"));
                versions.add(ver);
            }
            catch (ParseException ex) {
                logger.warn("Unexpected error while parsing version from bag name, " + 
                            bag + "; skipping");
            }
        }

        return new ArrayList<String>(versions);
    }

    /**
     * Returns the head bag name for given identifier
     * @param identifier     identifier for the AIP 
     * @return String,       the name of the matching head bag
     * @throws ResourceNotFoundException  if no bags are found associated with the given ID
     * @throws DistributionException      if there is unexpected, internal error
     */
    @Override
    public String getHeadBagName(String identifier)
        throws ResourceNotFoundException, DistributionException
    {
        logger.debug("GetHeadbag for :"+identifier);
        return storage.findHeadBagFor(identifier);
    }

    /**
     * Returns the head bag for given identifier and a version of bags.
     * @param identifier     identifier for the AIP 
     * @param version        the desired version of the AIP
     * @return String,       the name of the matching head bag
     * @throws ResourceNotFoundException  if no bags are found associated with the given ID
     * @throws DistributionException      if there is unexpected, internal error
     */
    @Override
    public String getHeadBagName(String identifier, String version)
        throws ResourceNotFoundException, DistributionException
    {
        logger.debug("GetHeadbag for :"+identifier +" and version:"+version);
        return storage.findHeadBagFor(identifier, version);
    }

    /**
     * Returns the bag for given complete bag file name
     * @param bagfile        the name of the serialized bag
     * @return StreamHandle, a container for an open stream ready to present the bag
     * @throws FileNotFoundException  if no bags are found associated with the given ID
     * @throws DistributionException      if there is unexpected, internal error
     */
    @Override
    public StreamHandle getBag(String bagfile) throws FileNotFoundException, DistributionException {
        logger.debug("Get StreamHandle for bagfile:"+bagfile);
        long size = storage.getSize(bagfile);
        Checksum hash = storage.getChecksum(bagfile);
        String ct = getDefaultContentType(bagfile);
        return new StreamHandle(storage.openFile(bagfile), size, bagfile, ct, hash);
    }

    /**
     * Returns the information of the bag for given bag file name.  If the filename follows the bag
     * naming conventions understood by the {@link BagUtils BagUtils} class, the returned object will
     * include extra property information gleaned from the name.
     * @param bagfile        the name of the serialized bag
     * @return FileDescription, a container for an open stream ready to present the bag
     * @throws FileNotFoundException  if no bags are found associated with the given ID
     * @throws DistributionException      if there is unexpected, internal error
     */
    @Override
    public FileDescription getInfo(String bagfile) throws FileNotFoundException, DistributionException {
        logger.debug("Get StreamHandle info for bagfile:"+bagfile);
        String ct = getDefaultContentType(bagfile);
        return new BagDescription(bagfile, storage.getSize(bagfile), ct,
                                  storage.getChecksum(bagfile));
    }

    /**
     * return a default content type based on the given file name.  This implementation determines
     * the content type based on the file name's extension.  
     */
    public String getDefaultContentType(String filename) {
        return typemap.getContentType(filename);
    }

}
