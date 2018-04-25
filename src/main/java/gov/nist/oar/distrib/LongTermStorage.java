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
package gov.nist.oar.distrib;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

import gov.nist.oar.ds.exception.IDNotFoundException;

/**
 * can search for bags matching an identifier, find head bags, 
 * different versions of bags (with the help of the bags.preservation.BagUtils class)
 * @author  Deoyani Nandrekar-Heinis
 *
 */

public interface LongTermStorage {
  /**
   * Given an exact file name in the storage, return an InputStream open at the start of the file
   * @param filename, Here filename refers to any file/object present in S3 bucket not the file inside compressed package
   * @return InputStream open at the start of the file
   * @throws FileNotFoundException
   */
  InputStream openFile(String filename) throws FileNotFoundException;
  /**
   * return the checksum for the given file
   * @param filename this is name of bag, checksum or any other object present in long term data storage\
   *        This parameter does not reflect the individual data file which might be present in the package/bundle/bag.
   * @return Checksum
   * @throws FileNotFoundException
   */
  Checksum getChecksum(String filename) throws FileNotFoundException;
  /**
   * Return the size of the named file in bytes
   * @param filename Here filename refers to any file/object present in S3 bucket not the file inside compressed package
   * @return long
   * @throws FileNotFoundException
   */
  long getSize(String filename) throws FileNotFoundException;
  /**
   * Return all the bags associated with the given ID
   * @param identifier is the unique record id for given data package
   * @return String 
   * @throws IDNotFoundException
   */
  List<String> findBagsFor(String identifier) throws IDNotFoundException;
  /**
   * Return the head bag associated with the given ID
   * @param identifier is the unique record id for given data package
   * @return String
   * @throws IDNotFoundException
   */
  String findHeadBagFor(String identifier) throws IDNotFoundException; 
  /**
   * Return the name of the head bag for the identifier for given version
   * @param identifier is the unique record id for given data package
   * @param version 
   * @return String
   * @throws IDNotFoundException
   */
  String findHeadBagFor(String identifier, String version) throws IDNotFoundException;
  
  
}
