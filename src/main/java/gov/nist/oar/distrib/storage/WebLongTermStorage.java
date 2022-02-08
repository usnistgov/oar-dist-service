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
package gov.nist.oar.distrib.storage;

import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.ArrayDeque;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.URL;
import java.net.URI;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.HttpURLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.FileDescription;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.StorageStateException;
import gov.nist.oar.distrib.storage.PDRBagStorageBase;
import gov.nist.oar.bags.preservation.BagUtils;

/**
 * An implementation of the LongTermStorage interface for accessing files from a remote web service 
 * via URLs.
 */
public class WebLongTermStorage implements LongTermStorage {

    /** the logger instance to use */
    protected Logger logger = null;

    private String _name = null;
    private String _alg = null;
    private String _prefix = null;
    private FileDescCache _fdc = null;

    /** the base URL of the repository web site */
    protected String urlbase = null;

    /**
     * initialize the storage 
     * @param name     a name to refer to this storage by (in log messages)
     * @param log      a Logger to use; if null, a default is created.
     */
    protected WebLongTermStorage(String name, Logger log) {
        _name = name;
        if (log == null)
            log = LoggerFactory.getLogger(getClass());
        logger = log;
    }

    /**
     * the storage interface around the base URL of the target web site
     */
    public WebLongTermStorage(String urlbase, String checksumName, Logger log)
        throws MalformedURLException
    {
        this(urlbase, checksumName, null, urlbase, 6, log);
    }

    /**
     * the storage interface around the base URL of the target web site
     */
    public WebLongTermStorage(String urlbase, String checksumName) throws MalformedURLException {
        this(urlbase, checksumName, null, urlbase, 6, null);
    }

    /**
     * the storage interface around the base URL of the target web site
     */
    public WebLongTermStorage(String urlbase) throws MalformedURLException {
        this(urlbase, null, null);
    }

    /**
     * the storage interface around the base URL of the target web site
     * @param baseurl         the base HTTP/HTTPS URL for the remote repository.
     * @param checksumName    the name for the checksum type supported by the repository.  If 
     *                        null (or empty) checksum access will not be supported.  If provided,
     *                        this class will retrieve checksum values from remote resources with 
     *                        a path, <i>resource_path.checksumName</i>.
     * @param resourcePrefix  a prefix that is expected in input resource identifiers that should 
     *                        be stripped before appending it to the base URL for remote access.
     *                        If non-null and non-empty, resources without this prefix will be 
     *                        rejected as being non-existent.  Include a trailing slash (/) if 
     *                        the prefix is meant to represent whole path name fields.
     * @param storename       a name to give to this remote store; if null, the baseurl string 
     *                        will be used as the name.
     * @param infoCacheSize   the limit in the number of resource headers that are internally 
     *                        cached to reduce unnecessary repeated remote accesses.
     * @param log             the Logger to send messages to.
     */
    public WebLongTermStorage(String baseurl, String checksumName, String resourcePrefix, 
                              String storename, int infoCacheSize, Logger log)
        throws MalformedURLException
    {
        this(storename, log);

        // ensure URL base has a legal format
        URL base = new URL(baseurl);
        try {
            String scheme = base.toURI().getScheme();
            if (! scheme.equals("http") && ! scheme.equals("https"))
                throw new MalformedURLException("Unsupported (non-HTTP) URL scheme: "+scheme+
                                                " (in "+baseurl+")");
        } catch (URISyntaxException ex) {
            throw new MalformedURLException("Not a base URL ("+ex.getMessage()+"): "+baseurl);
        }
        urlbase = baseurl;

        if (checksumName != null && checksumName.length() > 0)
            _alg = checksumName;
        _fdc = new FileDescCache(infoCacheSize);
    }

    /**
     * return a name for the storage system.  This is used primarily for enhancing error messages
     * by indicating which storage system produced the error.
     */
    @Override
    public String getName() { return _name; }

    /**
     * return the type of checksum supported by this storage or null if checksums are not supported
     */
    public String getChecksumAlgorithm() { return _alg; }

    /**
     * return the URL to use to access the resource or null if the resource is not of a 
     * legal or recognized form.
     */
    protected URL getResourceURL(String resource) {
        if (_prefix != null) {
            if (! resource.startsWith(_prefix)) return null;
            resource = resource.substring(_prefix.length());
        }
        
        if (resource.length() == 0) return null;
        
        try {
            return new URL(urlbase+resource);
        }
        catch (MalformedURLException ex) {
            return null;
        }
    }

    /**
     * open a connection to the resource.  This allows sub-classes to tune the connection parameters
     * by overriding this method.
     */
    protected HttpURLConnection openConnection(URL resource) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) resource.openConnection();
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    /**
     * retreive (and cache) header information for a remote resource.  This will submit a HEAD 
     * request to the remote resource, and the content length and type will be extracted.  
     * @return FileDescription  extracted header information or null if the resource does not exist
     */
    protected FileDescription getResourceHead(String resource) throws IOException {
        URL ep = getResourceURL(resource);
        HttpURLConnection conn = openConnection(ep);
        try {
            conn.setRequestMethod("HEAD");
            logger.debug("HEAD {}", ep.toString());
            int status = conn.getResponseCode();    // triggers request

            if (status >= 500) 
                throw new IOException("Server error "+Integer.toString(status)+" while accessing "+
                                      ep.toString()+": "+conn.getResponseMessage());
            if (status > 300)
                return null;

            FileDescription out = new FileDescription(resource, conn.getContentLengthLong(),
                                                      conn.getContentType());
            _fdc.add(resource, out);
            return out;
        }
        finally {
            conn.disconnect();
        }
    }

    /**
     * retreive (and cache) checksum for a remote resource or null if either the resource does not 
     * exist or checksums are not supported.
     */
    protected Checksum getResourceChecksum(String resource) throws IOException {
        if (_alg == null)
          throw new UnsupportedOperationException("Checksums not supported on this WebLongTermStorage");

        URL ep = getResourceURL(resource+"."+_alg);
        HttpURLConnection conn = openConnection(ep);
        Checksum out = null;
        try {
            conn.setRequestMethod("GET");
            logger.debug("GET {}", ep.toString());
            int status = conn.getResponseCode();    // triggers request
            
            if (status >= 500) 
                throw new IOException("Server error "+Integer.toString(status)+" while accessing "+
                                      ep.toString()+": "+conn.getResponseMessage());
            if (status > 300)
                return null;
            if (! conn.getContentType().startsWith("text/plain"))
                throw new IOException("Unexpected content type for checksum: "+conn.getContentType());

            String content = getStringContent(conn);
            if (content == null || content.trim().length() == 0)
                throw new IOException("Empty content in checksum resource for "+resource);
            content = content.trim();
            out = new Checksum(content.split(" ")[0], _alg);
        }
        finally {
            conn.disconnect();
        }

        FileDescription fd = _fdc.lookup(resource);
        if (fd == null) fd = getResourceHead(resource);
        fd.checksum = out;

        return out;
    }

    private String getStringContent(HttpURLConnection conn) throws IOException {
        BufferedReader sr = null;
        try {
            sr = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            return sr.readLine();
        }
        finally {
            if (sr != null) sr.close();
        }
    }

    /**
     * Given an exact file name in the storage, return an InputStream open at the start of the file.
     * The caller is responsible for closing the stream when finished with it.
     * @param resource   The name of the desired resource.  Note that this does not refer to files that 
     *                   may reside inside a serialized bag or other archive (e.g. zip) file.  
     * @return InputStream open at the start of the file
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    @Override
    public InputStream openFile(String resource) throws FileNotFoundException, StorageVolumeException {
        URL ep = getResourceURL(resource);
        HttpURLConnection conn = null;
        try {
            conn = openConnection(ep);
            conn.setRequestMethod("GET");
            logger.debug("GET {}", ep.toString());
            int status = conn.getResponseCode();    // triggers request

            if (status >= 500) 
                throw new IOException("Server error "+Integer.toString(status)+" while accessing "+
                                      ep.toString()+": "+conn.getResponseMessage());
            if (status > 300)
                throw new FileNotFoundException(resource);

            FileDescription fd = new FileDescription(resource, conn.getContentLengthLong(),
                                                     conn.getContentType());
            _fdc.add(resource, fd);

            return conn.getInputStream();
        }
        catch (FileNotFoundException ex) {
            throw ex;
        }
        catch (IOException ex) {
            throw new StorageVolumeException("Error accessing remote resource ("+ep.toString()+"): "+
                                             ex.getMessage(), ex);
        }
    }

    /**
     * return true if a file with the given name exists in the storage 
     * @param resource   The name of the remote resource.  This corresponds to the resource URL's
     *                   path relative to the base URL
     */
    @Override
    public boolean exists(String resource) throws StorageVolumeException {
        if (_fdc.lookup(resource) != null) return true;
        try {
            return (getResourceHead(resource) != null);
        } catch (IOException ex) {
            throw new StorageVolumeException(resource+": Problem accessing remote resource: "+
                                             ex.getMessage(), ex);
        }
    }

    /**
     * return the checksum for the given file
     * @param resource   The name of the remote resource.  
     * @return Checksum, a container for the checksum value
     * @throws FileNotFoundException  if the file with the given filename does not exist
     * @throws UnsupportedOperationException   if checksums are not supported on this storage system
     */
    @Override
    public Checksum getChecksum(String resource) throws FileNotFoundException, StorageVolumeException {
        FileDescription fd = _fdc.lookup(resource);
        if (fd != null && fd.checksum != null)
            return fd.checksum;

        Checksum out = null;
        try {
            out = getResourceChecksum(resource);
        }
        catch (IOException ex) {
            throw new StorageVolumeException(resource+": Problem accessing remote resource: "+
                                             ex.getMessage(), ex);
        }
        if (out == null && ! exists(resource))
            throw new FileNotFoundException(resource);
        return out;
    }

    /**
     * Return the size of the named file in bytes
     * @param resource   The name of the desired resource.  
     * @return long, the size of the file in bytes.
     * @throws FileNotFoundException  if the file with the given filename does not exist
     */
    @Override
    public long getSize(String resource) throws FileNotFoundException, StorageVolumeException {
        FileDescription fd = _fdc.lookup(resource);
        if (fd != null)
            return fd.contentLength;

        try {
            fd = getResourceHead(resource);
        }
        catch (IOException ex) {
            throw new StorageVolumeException(resource+": Problem accessing remote resource: "+
                                             ex.getMessage(), ex);
        }
        if (fd == null)
            throw new FileNotFoundException(resource);
        
        return fd.contentLength;
    }

    // TODO: add time limits on cache items
    static class FileDescCache {
        private Queue<String> q = new ArrayDeque<String>(3);
        private Map<String, FileDescription> lu = new HashMap<String, FileDescription>(3);
        private int lim = 6;

        FileDescCache() {
            this(0);
        }
        FileDescCache(int size) {
            if (size <= 0) size = 6;
            lim = size;
        }

        private synchronized void dropfi() {
            String fi = q.poll();
            if (fi != null) lu.remove(fi);
        }
        private synchronized void ensureRoom() {
            while (q.size() >= lim) 
                dropfi();
        }
        private synchronized void remove(String resource) {
            q.remove(resource);
            lu.remove(resource);
        }

        public FileDescription lookup(String resource) {
            return lu.get(resource);
        }

        public synchronized void add(String resource, FileDescription fd) {
            if (lu.containsKey(resource))
                remove(resource);
            ensureRoom();
            q.add(resource);
            lu.put(resource, fd);
        }
    }

}
