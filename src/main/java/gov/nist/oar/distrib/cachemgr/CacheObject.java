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
package gov.nist.oar.distrib.cachemgr;

import java.util.Set;
import org.json.JSONObject;
import org.json.JSONException;

/**
 * a simple container class representing an object that can be stored in a
 * cache.  
 * 
 * A CacheObject encapsulates to public properties: a CacheVolume identifer 
 * (i.e. name) and the object's name within that volume 
 */
public class CacheObject {

    /**
     * the CacheVolume that the object is located in.  
     *
     * This may be null, e.g. when returned from 
     * {@link StorageInventoryDB#findObject(String) StorageInventoryDB.findObject()} as 
     * that class is not capable of instantiating CacheVolume objects.
     */
    public CacheVolume volume = null;

    /**
     * the name of the CacheVolume that the object is located in.  If this name is null,
     * this instance represents a placeholder for an object whose name is not known or 
     * determined, yet.  If volume is not null, this should be set to the volume's name.
     */
    public String volname = null;

    /**
     * the name that the object has in the volume.  If the name is null, this
     * instance represents a placeholder for an object whose name is not known
     * or determined yet.
     */
    public String name = null;

    /**
     * the cache-volume-independent identifier for the object.  This may be null if not known
     */
    public String id = null;

    /**
     * initialize the CacheObject with null values
     */
    public CacheObject() { }

    /**
     * a deletability score.  This value will only be meaningful in the context of deletion plan.
     * It allows a list of CacheObjects to be ordered according to their readiness to be deleted.  
     * The higher the score, the more deletable the object is.
     */
    public double score = 0.0;

    /**
     * the object metadata
     */
    protected JSONObject _md = null;

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
     * initialize the CacheObject with an object name and volume name
     * @param name  the name of the object within the volume.  This may be 
     *                different from its location-idenpendent identifier.
     *                (may be null)
     * @param vol   the identifer of the volume where the object is located
     *                (may be null)
     */
    public CacheObject(String name, String vol) {
        this.volname = vol;
        this.name = name;
        this._md = new JSONObject();
    }

    /**
     * initialize the CacheObject with an object name and CacheVolume instance
     * @param name  the name of the object within the volume.  This may be 
     *                different from its location-idenpendent identifier.
     *                (may be null)
     * @param vol   a CacheVolume instance where the object is purported to be 
     *                located (may be null)
     */
    public CacheObject(String name, CacheVolume vol) {
        this(name, vol.getName());
        this.volume = vol;
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
    public CacheObject(String name, JSONObject md, CacheVolume vol) {
        this(name, md, vol.getName());
        this.volume = vol;
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
    public CacheObject(String name, JSONObject md, String vol) {
        this.volname = vol;
        this.name = name;
        _md = md;
        if (_md == null)
            _md = new JSONObject();
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
        return _md.has(name);
    }

    /**
     * return the size of the object in bytes or -1 if unknown.  
     */
    public long getSize() {
        try {
            return getMetadatumLong("size", -1L);
        } catch (JSONException ex) {
            return -1L;
        }
    }

    /**
     * return the value of a metadatum as an integer.  
     * @param name   the name of the metadatum
     * @param defval the value to return if a value is not set for name
     * @return int  the value of the metadatum
     * @throws JSONException  if the metadatum with the given name is 
     *     stored as an int.
     */
    public int getMetadatumInt(String name, int defval) {
        if (! _md.has(name))
            return defval;
        return _md.getInt(name);
    }

    /**
     * return the value of a metadatum as an integer.  
     * @param name    the name of the metadatum
     * @param defval  the value to return if the name does not have a value.
     * @return int   the value of the metadatum
     * @throws JSONException  if the metadatum with the given name is 
     *     stored as an int.
     */
    public long getMetadatumLong(String name, long defval) {
        if (! _md.has(name))
            return defval;
        return _md.getLong(name);
    }

    /**
     * return the value of a metadatum as a String
     * @param name   the name of the metadatum
     * @param defval the value to return if a value is not set for name
     * @return int  the value of the metadatum
     * @throws JSONException  if the metadatum with the given name is 
     *     stored as an int.
     */
    public String getMetadatumString(String name, String defval) {
        if (! _md.has(name))
            return defval;
        return _md.getString(name);
    }

    /**
     * return a copy of the metadata as a JSONObject instance
     */
    public JSONObject exportMetadata() {
        return new JSONObject(_md, JSONObject.getNames(_md));
    }
}
