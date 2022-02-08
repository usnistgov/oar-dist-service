/*
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
package gov.nist.oar.distrib.cachemgr.simple;

import gov.nist.oar.distrib.cachemgr.restore.ZipFileRestorer;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ObjectNotFoundException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * a SimpleCacheManager assuming that the long-term storage is a directory filled 
 * with zip files.  The first field in an object identifier's path (whether in a URI or is 
 * a file path) corresponds to the the zip file in that directory: ".zip" is appended to 
 * find the file in the given zipdir argument given here.  
 */
public class ZipRepoRestorer extends ZipFileRestorer {

    /**
     * create the restorer accessing zip files in a given directory
     * @param zipdir  the path to the directory where the zip files can be found.  
     * @throws FileNotFoundException   if zipdir does not exist
     */
    public ZipRepoRestorer(String zipdir) throws FileNotFoundException {
        super(new FilesystemLongTermStorage(zipdir));
    }

    public File getRepoDir() {
        return ((FilesystemLongTermStorage) store).rootdir;
    }

    /**
     * given an object identifier, return the zip file in long-term storage that contains 
     * the object and the file within the zip file that represents that object.
     * @return String[]   a 2-element array where the first element is the locaiton of the 
     *                    zip file and the second element is the name of the file to extract
     *                    from it.  
     */
    protected String[] id2location(String id) throws ObjectNotFoundException {
        if (id == null)
            throw new IllegalArgumentException("determineCacheObjectName(): null ID string");
        if (id.length() == 0)
            throw new IllegalArgumentException("determineCacheObjectName(): empty ID string");

        Path path = null;
        try {
            path = Paths.get(URI.create(id));
            if (path == null)
                throw new ObjectNotFoundException(id);
        } catch (RuntimeException ex) { }

        if (path == null)
            path = Paths.get(id);
        if (path.getNameCount() < 2)
            throw new ObjectNotFoundException(id);

        String[] out = new String[2];
        out[0] = path.subpath(0, 1).toString() + ".zip";
        out[1] = id;
        return out;
    }
}
