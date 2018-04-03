package gov.nist.oar.cachemgr;

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

    
}
