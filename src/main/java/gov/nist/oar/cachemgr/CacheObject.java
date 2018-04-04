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
package gov.nist.oar.cachemgr;

import java.util.Set;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonNumber;

/**
 * a simple container class representing an object that can be stored in a
 * cache.  
 * 
 * A CacheObject encapsulates to public properties: a CacheVolume identifer 
 * (i.e. name) and the object's name within that volume 
 */
public class CacheObject {

    /**
     * the identifier for the CacheVolume that the object is located in.  
     */
    public String volume = null;

    /**
     * the name that the object has in the volume.  If the name is null, this
     * instance represents a placeholder for an object whose name is not known
     * or determined yet.
     */
    public String name = null;

    /**
     * initialize the CacheObject with null values
     */
    public CacheObject() { }

    /**
     * the object metadata
     */
    protected JsonObject _md = null;

    /**
     * initialize the CacheObject with a name and a null volume ID
     * @param name  the name of the object within the volume.  This may be 
     *                different from its location-idenpendent identifier.
     *                (may be null)
     */
    public CacheObject(String name) {
        this(name, (String) null);
    }

    /**
     * initialize the CacheObject with null values
     * @param name  the name of the object within the volume.  This may be 
     *                different from its location-idenpendent identifier.
     *                (may be null)
     * @param vol   the identifer of the volume where the object is located
     *                (may be null)
     */
    public CacheObject(String name, String vol) {
        this.volume = vol;
        this.name = name;
        this._md = Json.createObjectBuilder().build();
    }

    /**
     * initialize the CacheObject with null values
     * @param name  the name of the object within the volume.  This may be 
     *                different from its location-idenpendent identifier.
     *                (may be null)
     * @param md    the object metadata provided as a JSON object
     * @param vol   the identifer of the volume where the object is located
     *                (may be null)
     */
    public CacheObject(String name, JsonObject md, String vol) {
        this.volume = vol;
        this.name = name;
        this._md = md;
    }

    /**
     * return the names of available metadata
     */
    public Set<String> metadatumNames() {
        return _md.keySet();
    }

    /**
     * return true if the metadata has a value for the datum with the given
     * name.
     * @param name   the name of the metadatum
     */
    public boolean hasMetadatum(String name) {
        return _md.containsKey(name);
    }

    /**
     * return the size of the object in bytes or -1 if unknown.  
     */
    public long getSize() {
        try {
            return getMetadatumLong("size", -1L);
        } catch (ClassCastException ex) {
            return -1L;
        }
    }

    /**
     * return the value of a metadatum as an integer.  
     * @param name   the name of the metadatum
     * @param defval the value to return if a value is not set for name
     * @returns int  the value of the metadatum
     * @throws ClassCastException  if the metadatum with the given name is 
     *     stored as an int.
     */
    public int getMetadatumInt(String name, int defval) {
        return _md.getInt(name, defval);
    }

    /**
     * return the value of a metadatum as an integer.  
     * @param name    the name of the metadatum
     * @param defval  the value to return if the name does not have a value.
     * @returns int   the value of the metadatum
     * @throws ClassCastException  if the metadatum with the given name is 
     *     stored as an int.
     */
    public long getMetadatumLong(String name, long defval) {
        JsonNumber out = _md.getJsonNumber(name);
        return (out == null) ? defval : out.longValueExact();
    }

    /**
     * return the value of a metadatum as a String
     * @param name   the name of the metadatum
     * @param defval the value to return if a value is not set for name
     * @returns int  the value of the metadatum
     * @throws ClassCastException  if the metadatum with the given name is 
     *     stored as an int.
     */
    public String getMetadatumString(String name, String defval) {
        return _md.getString(name, defval);
    }
}
