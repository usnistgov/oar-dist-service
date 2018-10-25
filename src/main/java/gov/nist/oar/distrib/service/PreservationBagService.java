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

import java.io.FileNotFoundException;
import java.util.List;

import gov.nist.oar.distrib.StreamHandle;
import gov.nist.oar.distrib.FileDescription;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.ResourceNotFoundException;

/**
 * Service interface for accessing preservation bags (AIP files) and information about them.
 * <p>
 * In the OAR PDR model, an <em>archive information package</em> (AIP) is made up of one or more files.  
 * Together, these make up the AIP.  In the default storage model, the AIP files are serialized BagIt
 * bags that conform to the Multibag BagIt Profile.  This interface provides a means for identifying
 * and accessing the bag files.  
 * 
 * @author Deoyani Nandrekar-Heinis
 */
public interface PreservationBagService {

    /**
     * Return the names of the bags having the given AIP identifier
     * @param identifier     identifier for the AIP 
     * @return List<String>, list of names of available bags for the AIP 
     * @throws ResourceNotFoundException  if no bags are found associated with the given ID
     * @throws DistributionException      if there is unexpected, internal error
     */
    public List<String> listBags(String identifier)
        throws ResourceNotFoundException, DistributionException;

    /**
     * Return the version strings for the versions available for an AIP with the given identifier
     * @param identifier     identifier for the AIP 
     * @return List<String>, list of versions available for the AIP
     * @throws ResourceNotFoundException  if no bags are found associated with the given ID
     * @throws DistributionException      if there is unexpected, internal error
     */
    public List<String> listVersions(String identifier)
        throws ResourceNotFoundException, DistributionException;

    /**
     * Returns the head bag name for given identifier
     * @param identifier     identifier for the AIP 
     * @return String,       the name of the matching head bag
     * @throws ResourceNotFoundException  if no bags are found associated with the given ID
     * @throws DistributionException      if there is unexpected, internal error
     */
    public String getHeadBagName(String identifier)
        throws ResourceNotFoundException, DistributionException;

    /**
     * Returns the head bag for given identifier and a version of bags.
     * @param identifier     identifier for the AIP 
     * @param version        the desired version of the AIP
     * @return String,       the name of the matching head bag
     * @throws ResourceNotFoundException  if no bags are found associated with the given ID
     * @throws DistributionException      if there is unexpected, internal error
     */
    public String getHeadBagName(String identifier, String version)
        throws ResourceNotFoundException, DistributionException;

    /**
     * Returns the bag for given complete bag file name.  The bag data is provided as an 
     * open InputStream in the returned StreamHandle container.  The 
     * @param bagfile        the name of the serialized bag
     * @return StreamHandle, a container for an open stream ready to present the bag
     * @throws FileNotFoundException  if no bags are found associated with the given ID
     * @throws DistributionException      if there is unexpected, internal error
     */
    public StreamHandle getBag(String bagfile) throws FileNotFoundException, DistributionException;

    /**
     * Returns the information of the bag for given bag file name
     * @param bagfile        the name of the serialized bag
     * @return StreamHandle, a container for an open stream ready to present the bag
     * @throws FileNotFoundException  if no bags are found associated with the given ID
     * @throws DistributionException      if there is unexpected, internal error
     */
    public FileDescription getInfo(String bagfile) throws FileNotFoundException, DistributionException;
}
