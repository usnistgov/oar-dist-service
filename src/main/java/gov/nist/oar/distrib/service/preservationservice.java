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
 * @author Deoyani Nandrekar-Heinis
 *
 */
public interface preservationservice {
  
  List<String> listBags(String identifier) throws IDNotFoundException;
  
  String getHeadBagName(String identifier) throws IDNotFoundException ;

  String getHeadBagName(String identifier, String version) throws IDNotFoundException;
  
  StreamHandle getBag(String bagfile) throws FileNotFoundException;
  
  StreamHandle getInfo(String bagfile) throws FileNotFoundException;
}
