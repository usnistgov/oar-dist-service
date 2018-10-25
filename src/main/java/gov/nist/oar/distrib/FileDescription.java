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
 */
package gov.nist.oar.distrib;

import java.util.Map;
import java.util.Hashtable;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * a simple container for metadata about a downloadable file that are crucial to its storage and 
 * delivery through a web service.  This includes its canonical name (which can be a hierarchical 
 * path name), its size, and checksum hash.  Additional metadata can be attached as well.
 * <p>
 * This container leverages the Jackson JSON framework (which is used by the Spring Framework) for 
 * serializing this information into JSON.  
 */
@JsonInclude(Include.NON_NULL)
public class FileDescription {

    /**
     * the name of the file (which can be hierarchical)
     */
    public String name = null;

    /**
     * the total number of bytes that are available from the output data stream.  
     * A value less than 0 indicates that the total length is unknown.
     */
    public long contentLength = -1L;

    /**
     * the content type appropriate for the contents of the data stream.  If null,
     * a default contentType shoud be assumed.  
     */
    public String contentType = null;

    /**
     * the checksum calculated for the bytes on the output data stream.  If null, the 
     * hash is not available for this deliverable.
     */
    public Checksum checksum = null;

    /**
     * the identifier of the AIP (<em>archive information package</em>) that this file is 
     * associated with
     */
    public String aipid = null;

    /**
     * additional named properties of the file
     */
    protected Map<String, Object> props = null;

    /**
     * create an empty description
     */
    public FileDescription() { }

    /**
     * initialize this description with basic file information
     * @param name         a (file) name for the bytes on the stream
     * @param size         the expected number of bytes available on the stream
     * @param contentType  the MIME type to associate with the bytes
     * @param cs           a Checksum for the bytes on the stream
     */
    public FileDescription(String name, long size, String contentType, Checksum cs) {
        this.name = name;
        this.contentLength = size;
        this.contentType = contentType;
        this.checksum = cs;
    }

    /**
     * initialize this description with basic file information.  Any String may 
     * be null, and size should be negative if not known.
     * @param name         a (file) name for the bytes on the stream
     * @param size         the expected number of bytes available on the stream
     * @param contentType  the MIME type to associate with the bytes
     * @param sha256       the SHA-256 hash of the file contents
     */
    public FileDescription(String name, long size, String contentType, String sha256) {
        this(name, size, contentType, (sha256 == null) ? (Checksum) null : Checksum.sha256(sha256));
    }

    /**
     * initialize this description with basic file information.  Any String may 
     * be null, and size should be negative if not known.  The checksum will be set to
     * null.
     * @param name         a (file) name for the bytes on the stream
     * @param size         the expected number of bytes available on the stream
     * @param contentType  the MIME type to associate with the bytes
     */
    public FileDescription(String name, long size, String contentType) {
        this(name, size, contentType, (Checksum) null);
    }

    /**
     * initialize this description with a name and size.  
     * @param name         a (file) name for the bytes on the stream
     * @param size         the expected number of bytes available on the stream
     */
    public FileDescription(String name, long size) {
        this(name, size, null, (Checksum) null);
    }

    private void ensureProps() {
        if (props == null) props = new Hashtable<String, Object>();
    }

    /**
     * set a named String-valued property
     */
    public void setProp(String name, String val) {
        ensureProps();
        props.put(name, val);
    }

    /**
     * set the boolean-value of the named property
     * @throws ClassCastException  if the property is not integer-valued
     */
    public String getStringProp(String name) {
        ensureProps();
        return (String) props.get(name);
    }

    /**
     * set a named integer-valued property
     */
    public void setProp(String name, int val) {
        ensureProps();
        props.put(name, new Integer(val));
    }

    /**
     * get the integer-value of the named property.  
     * @throws ClassCastException  if the property is not integer-valued
     * @throws NullPointerException  if a property was not set with the given name
     */
    public int getIntProp(String name) {
        ensureProps();
        return ((Number) props.get(name)).intValue();
    }

    /**
     * set a named long-integer-valued property
     */
    public void setProp(String name, long val) {
        ensureProps();
        props.put(name, new Long(val));
    }

    /**
     * get the long integer-value of the named property.  
     * @throws ClassCastException    if the property is not integer-valued
     * @throws NullPointerException  if a property was not set with the given name
     */
    public long getLongProp(String name) {
        ensureProps();
        return ((Number) props.get(name)).longValue();
    }

    /**
     * set a named boolean-valued property
     */
    public void setProp(String name, boolean val) {
        ensureProps();
        props.put(name, new Boolean(val));
    }

    /**
     * set the boolean-value of the named property
     * @throws ClassCastException  if the property is not integer-valued
     */
    public boolean getBooleanProp(String name) {
        ensureProps();
        return ((Boolean) props.get(name)).booleanValue();
    }

    @JsonAnyGetter
    public Map<String, Object> getProperties() { 
        ensureProps();
        return props;
    }
}

