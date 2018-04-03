package gov.nist.oar.cachemgr;

import javax.json.JsonObject;

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
     * initialize the CacheObject with a name and a null volume ID
     * @param vol   the identifer of the volume where the object is located
     *                (may be null)
     */
    public CacheObject(String vol) {
        this(vol, null);
    }

    /**
     * initialize the CacheObject with null values
     * @param vol   the identifer of the volume where the object is located
     *                (may be null)
     * @param name  the name of the object within the volume.  This may be 
     *                different from its location-idenpendent identifier.
     *                (may be null)
     */
    public CacheObject(String vol, String id) {
        this.volume = vol;
        this.id = id;
    }

    /**
     * return the names of available metadata
     */
    public KeySet<String> metadatumNames() {
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
            return _md.getInt("size", -1L);
        } catch (ClassCastException ex) {
            return -1L;
        }
    }

    /**
     * return the value of a metadatum as an integer.  
     * @param name   the name of the metadatum
     * @returns int  the value of the metadatum
     * @throws ClassCastException  if the metadatum with the given name is 
     *     stored as an int.
     */
    public int getMetadatumInt(String name, String defval) {
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
        out = _md.getJsonNumber(name);
        if (out == null) out = defval;
        return out;
    }

    /**
     * return the value of a metadatum as a String
     * @param name   the name of the metadatum
     * @returns int  the value of the metadatum
     * @throws ClassCastException  if the metadatum with the given name is 
     *     stored as an int.
     */
    public String getMetadatumString(String name, String defval) {
        return _md.getString(name, defval);
    }
}
