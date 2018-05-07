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
import gov.nist.oar.ds.exception.IDNotFoundException;

/**
 * Service interface to get Perservation bags and information
 * @author Deoyani Nandrekar-Heinis
 *
 */
public interface PreservationBagService {
  /**
   * Returns the List of bags with name starting with given identifier
   * @param identifier
   * @return List<String>
   * @throws IDNotFoundException
   */
  List<String> listBags(String identifier) throws IDNotFoundException;
  /**
   * Returns the head bag name for given identifier
   * @param identifier
   * @return String
   * @throws IDNotFoundException
   */
  String getHeadBagName(String identifier) throws IDNotFoundException ;
  /**
   * Returns the head bag for given identifier and a version of bags.
   * @param identifier
   * @param version
   * @return String
   * @throws IDNotFoundException
   */
  String getHeadBagName(String identifier, String version) throws IDNotFoundException;
  /**
   * Returns the bag  for given complete bag file name
   * @param bagfile
   * @return StreamHandle
   * @throws FileNotFoundException
   */
  StreamHandle getBag(String bagfile) throws FileNotFoundException;
  /**
   * Returns the information of the bag for given bag file name
   * @param bagfile
   * @return StreamHandle
   * @throws FileNotFoundException
   */
  StreamHandle getInfo(String bagfile) throws FileNotFoundException;
}
