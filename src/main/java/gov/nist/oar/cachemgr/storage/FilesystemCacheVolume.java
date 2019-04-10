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
 * @author: Raymond Plante
 */
package gov.nist.oar.cachemgr.storage;

import gov.nist.oar.cachemgr.CacheVolume;

import java.io.InputStream;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

/**
 * an implementation of the CacheVolume interface assuming that the data is 
 * stored on a local disk.  
 *
 * The storage model has all data stored under a single directory.  Within that 
 * directory, objects are stored as files with the name as given to the addObject().
 * When that name includes a slash, the object file is stored in a subdirectory 
 * consistent with directory path implied by the name.  
 */
public class FilesystemCacheVolume /* implements CacheVolume */ {

    protected String root = null;

    /**
     * create a FilesystemCacheVolume 
     */
    public FilesystemCacheVolume(String rootdir) {
        root = rootdir;
    }

    
}
