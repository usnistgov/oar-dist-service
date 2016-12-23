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
package gov.nist.oar.ds.service.impl;

import org.springframework.stereotype.Service;

import gov.nist.oar.ds.service.CacheManager;

/**
 * 
 * This is the is the default implementation of the cache manager class responsible of handling
 * caching requests
 *
 */
@Service
public class CacheManagerImpl implements CacheManager {

  @Override
  public boolean isCached(String dsId, String distId) {
    return false;
  }

}
