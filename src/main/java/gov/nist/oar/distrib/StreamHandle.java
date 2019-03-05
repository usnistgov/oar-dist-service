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
package gov.nist.oar.distrib;

import java.io.InputStream;
import java.io.IOException;
import java.io.Closeable;

/**
 * a simple container class used to deliver an InputStream along with 
 * metadata about the stream.  This is metadata that is useful to a web 
 * service interface, including number of bytes that can/should be read 
 * and an appropriate content-type string.  
 * 
 * @author Raymond Plante
 */
public class StreamHandle implements Closeable {

    /**
     * an OutputStream that is ready to deliver requested content.  This can be 
     * null if only the metadata is desired.
     */
    public InputStream dataStream = null;

    /**
     * information about the content provided on the data stream
     */
    protected FileDescription info;

    /**
     * initialize this handle with all available data.  The stream and any String may
     * be null, and size should be negative if not known.
     * @param strm         the InputStream to transport
     * @param size         the expected number of bytes available on the stream
     * @param name         a (file) name for the bytes on the stream
     * @param contentType  the MIME type to associate with the bytes
     * @param cs           a Checksum for the bytes on the stream
     */
    public StreamHandle(InputStream strm, long size, String name, String contentType,
                        Checksum cs)
    {
        dataStream = strm;
        info = new FileDescription(name, size, contentType, cs);
    }

    /**
     * initialize this handle with all available data.  The stream and any String may
     * be null, and size should be negative if not known.  The checksum will be set to
     * null.
     * @param strm         the InputStream to transport
     * @param size         the expected number of bytes available on the stream
     * @param name         a (file) name for the bytes on the stream
     * @param contentType  the MIME type to associate with the bytes
     */
    public StreamHandle(InputStream strm, long size, String name, String contentType) {
        dataStream = strm;
        info = new FileDescription(name, size, contentType, (Checksum) null);
    }

    /**
     * initialize this handle with all available data.  The stream and any String may
     * be null, and size should be negative if not known.  The algorithm will be set 
     * to a default for the system.
     * @param strm         the InputStream to transport
     * @param size         the expected number of bytes available on the stream
     * @param name         a (file) name for the bytes on the stream
     * @param contentType  the MIME type to associate with the bytes
     * @param hash         a Checksum hash value for the bytes on the stream
     */
    public StreamHandle(InputStream strm, long size, String name, String contentType,
                        String hash)
    {
        this(strm, size, name, contentType,
             (hash == null) ? null : Checksum.sha256(hash));
    }

    /**
     * initialize this handle with all available data.  The stream and may
     * be null, and size should be negative if not known.  The name, contentType, 
     * and checksum will be set to null.
     * @param strm         the InputStream to transport
     * @param size         the expected number of bytes available on the stream
     */
    public StreamHandle(InputStream strm, long size) {
        this(strm, size, null, null, (Checksum) null);
    }

    /**
     * initialize this handle with all available data.  The stream may be null.  The
     * name, contentType, and checksum will all be set to null, and the 
     * size will be set to -1.
     * @param strm         the InputStream to transport
     */
    public StreamHandle(InputStream strm) {
        this(strm, -1L, null, null, (Checksum) null);
    }

    /**
     * initialize an empty handle.  The size will be set to -1, and all other data will 
     * be set to null.
     */
    public StreamHandle() { info = new FileDescription(); }

    /**
     * close that bag's open dataStream
     */
    public void close() throws IOException {
        if (dataStream != null) dataStream.close();
    }

    /**
     * return a description of the content available on the data stream
     */
    public FileDescription getInfo() {
        return info;
    }
}
