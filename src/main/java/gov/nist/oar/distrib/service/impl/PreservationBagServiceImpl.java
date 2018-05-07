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
package gov.nist.oar.distrib.service.impl;

import java.io.FileNotFoundException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.StreamHandle;
import gov.nist.oar.distrib.service.PreservationBagService;
//import gov.nist.oar.distrib.storage.AWSS3LongTermStorage;
//import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;
import gov.nist.oar.ds.exception.IDNotFoundException;

/**
 * Service implementation to get Preservation bags and information
 * @author Deoyani Nandrekar-Heinis
 *
 */
@Service()

public class PreservationBagServiceImpl implements PreservationBagService {

  private static Logger logger = LoggerFactory.getLogger(PreservationBagServiceImpl.class);
  
  @Autowired
  LongTermStorage storage;

  /**
   * Returns the List of bags with name starting with given identifier
   * @param identifier
   * @return List<String>
   * @throws IDNotFoundException
   */
  @Override
  public List<String> listBags(String identifier) throws IDNotFoundException {
   
    logger.info("Get List of bags for given identifier:"+identifier);
    return  storage.findBagsFor(identifier);   
  }

  /**
   * Returns the head bag name for given identifier
   * @param identifier
   * @return String
   * @throws IDNotFoundException
   */
  @Override
  public String getHeadBagName(String identifier) throws IDNotFoundException {
    
    logger.info("GetHeadbag for :"+identifier);
    return storage.findHeadBagFor(identifier);
  }

  /**
   * Returns the head bag for given identifier and a version of bags.
   * @param identifier
   * @param version
   * @return String
   * @throws IDNotFoundException
   */
  @Override
  public String getHeadBagName(String identifier, String version) throws IDNotFoundException {
    logger.info("GetHeadbag for :"+identifier +" and version:"+version);
    return storage.findHeadBagFor(identifier, version);
  }

  /**
   * Returns the bag  for given complete bag file name
   * @param bagfile
   * @return StreamHandle
   * @throws FileNotFoundException
   */
  @Override
  public StreamHandle getBag(String bagfile) throws FileNotFoundException {
   
    logger.info("Get StreamHandle  for bagfile:"+bagfile);
    return new StreamHandle(storage.openFile(bagfile), storage.getSize(bagfile), bagfile, "",storage.getChecksum(bagfile));
  }

  /**
   * Returns the information of the bag for given bag file name
   * @param bagfile
   * @return StreamHandle
   * @throws FileNotFoundException
   */
  @Override
  public StreamHandle getInfo(String bagfile) throws FileNotFoundException {
    logger.info("Get StreamHandle info for bagfile:"+bagfile);
    
    return new StreamHandle(null, storage.getSize(bagfile), bagfile, "",storage.getChecksum(bagfile));
  }

}
