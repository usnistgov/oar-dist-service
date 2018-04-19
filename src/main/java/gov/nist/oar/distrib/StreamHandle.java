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

// Possible TODO:  add JsonObject for arbitrary metadata.

/**
 * a simple container class used to deliver an InputStream along with 
 * metadata about the stream.  This is metadata that is useful a web 
 * service interface, including number of bytes that can/should be read 
 * and an appropriate content-type string.  
 */
public class StreamHandle {

    /**
     * an OutputStream that is ready to deliver requested content.  This can be 
     * null if only the metadata is desired.
     */
    public InputStream dataStream = null;

    /**
     * the total number of bytes that are available from the output data stream.  
     * A value less than 0 indicates that the total length is unknown.
     */
    public long size = -1L;

    /**
     * a name for the stream of bytes
     */
    public String name = null;

    /**
     * the checksum hash for the bytes on the output data stream.  If null, the 
     * hash is not available for this deliverable.
     */
    public String hash = null;

    /**
     * the checksum algorithm used to create checksum hash value.  If null, the 
     * algorithm is intended to known to the system through some other mechanism.
     * This value should be the string used as a file extension for a file that
     * holds such a hash.
     */
    public String algorithm = null;

    /**
     * the content type appropriate for the contents of the data stream.  If null,
     * a default contentType shoud be assumed.  
     */
    public String contentType = null;

    /**
     * the name for the SHA-256 hash algorithm.  
     */
    public static String SHA256 = "sha256";

    /**
     * initialize this handle with all available data.  The stream and any String may
     * be null, and size should be negative if not known.
     */
    public StreamHandle(InputStream strm, long size, String name, String contentType,
                        String hash, String algorithm)
    {
        dataStream = strm;
        this.size = size;
        this.name = name;
        this.contentType = contentType;
        this.hash = hash;
        this.algorithm = algorithm;
    }

    /**
     * initialize this handle with all available data.  The stream and any String may
     * be null, and size should be negative if not known.  The algorithm will be set 
     * to null.
     */
    public StreamHandle(InputStream strm, long size, String name, String contentType,
                        String hash)
    {
        this(strm, size, name, contentType, hash, null);
    }

    /**
     * initialize this handle with all available data.  The stream and may
     * be null, and size should be negative if not known.  The name, contentType, 
     * hash, and algorithm will be set to null.
     */
    public StreamHandle(InputStream strm, long size) {
        this(strm, size, null, null, null, null);
    }

    /**
     * initialize this handle with all available data.  The stream may be null.  The
     * name, contentType, hash, and algorithm will all be set to null, and the 
     * size will be set to -1.
     */
    public StreamHandle(InputStream strm) {
        this(strm, -1L, null, null, null, null);
    }

    /**
     * initialize this handle with all available data.  The stream and any String may
     * be null, and size should be negative if not known.  The algorithm will be set 
     * to null.
     */
    public StreamHandle() { }
}
