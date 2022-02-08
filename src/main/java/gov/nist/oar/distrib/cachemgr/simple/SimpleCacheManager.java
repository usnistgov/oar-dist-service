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

import gov.nist.oar.distrib.cachemgr.BasicCacheManager;
import gov.nist.oar.distrib.cachemgr.Cache;
import gov.nist.oar.distrib.cachemgr.Restorer;
import gov.nist.oar.distrib.cachemgr.restore.FileCopyRestorer;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.storage.WebLongTermStorage;

import java.net.URI;
import java.net.MalformedURLException;
import java.io.FileNotFoundException;

import org.apache.commons.lang3.RandomStringUtils;

/**
 * A simple extension of the BasicCacheManager implementation.  
 * <p>
 * This implementation imposes an assumption about global identifiers: they are either URIs, file 
 * paths, or generic labels (without path delimiters).  Via 
 * {@link #determineCacheObjectName(String,String)}, it will extract a file path from the identifier
 * and use that as the cache file name.  
 */
public class SimpleCacheManager extends BasicCacheManager {

    /**
     * create the CacheManager by wrapping a Cache and Restorer.
     */
    public SimpleCacheManager(Cache cache, Restorer rest) {
        super(cache, rest);
    }

    /**
     * create a name for a data object within a particular {@link gov.nist.oar.distrib.cachemgr.CacheVolume}.  
     */
    protected String determineCacheObjectName(String volname, String id) {
        if (id == null)
            throw new IllegalArgumentException("determineCacheObjectName(): null ID string");
        if (id.length() == 0)
            throw new IllegalArgumentException("determineCacheObjectName(): empty ID string");

        String out = null;
        try {
            out = URI.create(id).getPath();
            if (out == null) 
                // id is a URI but does not contain a path (e.g. http://localhost/);
                // create a random name
                out = RandomStringUtils.randomAlphanumeric(Math.max(id.length(), 32));
        }
        catch (IllegalArgumentException ex) { }
        if (out == null)
            // treat like a filepath
            out = id;

        return out;
    }

    /**
     * create a SimpleCacheManager assuming that the long-term storage is a directory filled 
     * with zip files.  The first field in an object identifier's path (whether in a URI or is 
     * a file path) corresponds to the the zip file in that directory: ".zip" is appended to 
     * find the file in the given zipdir argument given here.  
     * 
     * @param cache   the Cache to store files extracted from their zipfile sources
     * @param zipdir  the path to the directory where the zip files can be found.  /
     * @throws FileNotFoundException   if zipdir does not exist
     */
    public static SimpleCacheManager forZipRepository(Cache cache, String zipdir)
        throws FileNotFoundException
    {
        return new SimpleCacheManager(cache, new ZipRepoRestorer(zipdir));
    }

    /**
     * create a SimpleCacheManager assuming that the long-term storage is a remote web site.
     * This manager will mirror the remote site, replicating file resources into the cache 
     * as needed.  
     * 
     * @param cache    the Cache to store files extracted from the remote web site
     * @param baseurl  the base URL of the remote site to use to retrieve the files from.  
     *                 The path extracted from the requested object IDs will be appended 
     *                 to this URL to retrieve the remote copy of the object.  
     */
    public static SimpleCacheManager forWebRepository(Cache cache, String baseurl)
        throws MalformedURLException
    {
        LongTermStorage lts = new WebLongTermStorage(baseurl);
        return new SimpleCacheManager(cache, new FileCopyRestorer(lts));
    }
}
