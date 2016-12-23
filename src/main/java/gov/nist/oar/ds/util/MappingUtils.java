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
 * 
 * @author:Harold Affo (Prometheus Computing, LLC)
 */
package gov.nist.oar.ds.util;

import java.io.IOException;

/**
 * This is the mapping utility class providing functions to handle the ORE mapping content
 *
 */
public class MappingUtils {

  private MappingUtils() {

  }

  /**
   * Return the distribution file key from the ore mapping file
   * 
   * @param dsId: the id of the distribution
   * @param oreContent: content of the mapping file
   * @return the key of the distribution file
   * @throws IOException
   */
  public static String findDistFileKey(String distId, String oreContent) throws IOException {
    return null;
  }


}
