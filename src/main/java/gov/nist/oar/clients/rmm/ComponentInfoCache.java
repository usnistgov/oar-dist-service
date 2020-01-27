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
package gov.nist.oar.distrib.clients.rmm;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * This class provides a rolling in-memory cache of NERDm component metadata.  Limits can be specified 
 * for both the total number of records in the cache and the length of time a record remains in the 
 * cache.  When additing a record causes the number limit to be exceeded, the least recently accessed 
 * record is removed.
 */
public class ComponentInfoCache {

    HashSet<String> retainTypes = null;
    HashSet<String> excludeTypes = null;

    CompMap info = null;

    /**
     * create an empty cache
     * @param limit       the maximum number of records allowed to be held in the cache; if 
     *                      negative, the default limit (100) will be used.
     * @param expiresSecs the time limit that a record can remain in the cache, in seconds; if
     *                      negative, the default limit (5 minutes) will be used.
     * @param initCap     the initial capacity of the empty cache
     */
    public ComponentInfoCache(int limit, long expiresSecs, int initCap) {
        info = new CompMap(limit, expiresSecs, initCap);
    }
    
    /**
     * create an empty cache
     * @param limit       the maximum number of records allowed to be held in the cache; if 
     *                      negative, the default limit (100) will be used.
     * @param expiresSecs the time limit that a record can remain in the cache, in seconds; if
     *                      negative, the default limit (5 minutes) will be used.
     * @param retain      a set of component types used to select out components to be cached 
     *                      from a NERDm record provided via cacheResource(): a component in the 
     *                      record that has a type matching one of these types will be cached 
     *                      (unless the component is excluded via the <code>exclude</code> set).  
     *                      If this parameter is null, all types will be selected (apart from 
     *                      those listed in exclude).
     * @param exclude     a set of component types used to reject components for caching
     *                      from a NERDm record provided via cacheResource(): a component in the 
     *                      record that has a type matching one of these types will <i>not</i> be cached 
     *                      If this parameter is null, no types will be explicitly excluded.  This
     *                      list overrides the include list.  
     * @param initCap     the initial capacity of the empty cache
     */
    public ComponentInfoCache(int limit, long expiresSecs,
                              Collection<String> retain, Collection<String> exclude, int initCap)
    {
        this(limit, expiresSecs, initCap);
        if (retain != null)
            retainTypes = new HashSet<String>(retain);
        if (exclude != null)
            excludeTypes = new HashSet<String>(exclude);
    }

    /**
     * return the number of records in the cache.
     */
    public int size() { return info.size(); }

    /**
     * return the capacity of the cache.  The cache will allow more records than this (unless 
     * the cache is full and the capacity is set to a lower number via 
     * {@link #setCapacity(int) setCapacity()}; calling {@link #weed()} will reduce the number of 
     * records to the new capacity).  
     */
    public int getCapacity() { return info.lim; }

    /**
     * set a new capacity of the cache.  This method will not change the contents of the cache.
     * If the capacity is changed, the cache size can grow and shrink with calls to 
     * {@link #put(String,JSONObject) put()} and {@link #give(String,JSONObject) give()} according to 
     * the new constraint.  If the capacity is reduced, calling {@link #weed()} will reduce the number of 
     * records to within the new capacity.  
     */
    public void setCapacity(int size) { if (info.lim >= 0) info.lim = size; }

    /**
     * empty the cache
     */
    public synchronized void clear() {
        for(Iterator it=info.keySet().iterator(); it.hasNext();) {
            it.next();
            it.remove();
        }
    }

    /**
     * save the given component to the cache.  This assumes that the caller is giving over control of 
     * the object to the cache, allowing it to alter its contents.  (A timestamp is added to track the 
     * age of the record.)  No check of the component type is done; the record will be saved regardless 
     * of its type.
     * @param id   the identifier string to save the component as; no check is done to ensure this id 
     *               corresponds to the value in the component record.  
     * @param comp the record to save to the cache; no check is done ensure that the record is a legal 
     *               NERDm component
     */
    public synchronized void give(String id, JSONObject comp) {
        info.put(id, comp);
    }

    /**
     * Place a copy of the component record into the cache with the given identifier.  No check of the 
     * component type is done; the record will be saved regardless of its type.
     * @param id   the identifier string to save the component as; no check is done to ensure this id 
     *               corresponds to the value in the component record.  
     * @param comp the record to save to the cache; no check is done ensure that the record is a legal 
     *               NERDm component
     */
    public void put(String id, JSONObject comp) {
        give(id, new JSONObject(comp, JSONObject.getNames(comp)));
    }

    /**
     * Return the component with the given identifier or null if the component is not in the cache.  
     * If the component was added via add {@link #cacheResource(JSONObject,boolean,String) cacheResource()}
     * then the identifier will be the resource identifer concatonated with the component's identifer 
     * (each take from the respective <code>@id</code> properties).  
     * 
     * @param id      the identifier that the component was saved under.
     * @param clean   if true, the record returned will have the exact values of the object that was 
     *                  originally saved; otherwise, the <code>@id</code> property will be set to the 
     *                  id it was saved under (see 
     *                  {@link #cacheResource(JSONObject,boolean,String) cacheResource()})
     *                  and the object may have administrative properties added (e.g. <code>_since</code>).
     */
    public JSONObject get(String id, boolean clean) {
        JSONObject out = info.get(id);
        if (out == null)
            return null;

        out = new JSONObject(out, JSONObject.getNames(out));
        if (clean)
            out.remove("_since");
        else
            out.put("@id", id);
        return out;
    }

    /**
     * Return the component with the given identifier or null if the component is not in the cache.  
     * This is the equivalent to {@link #get(String,boolean) get(id, false)}. 
     * @param id      the identifier that the component was saved under.
     */
    public JSONObject get(String id) {
        return get(id, false);
    }

    /**
     * cache all the components found in the given NERDm resoure record.  This looks for its components
     * in the given record's <code>components</code> property; if this property is not found, no components
     * are added.  The identifier each component is saved under will be the resource record's identifier 
     * (given by its <code>@id</code> value) contatonated with the component's identifier (given by its
     * <code>@id</code> value).  If the retain or exclude sets were provided at construction time, they
     * will be used to select the components to cache.  
     * @param res       the NERDm resource record to select components from.
     * @param copy      if true, a copy of each selected component will be cached; otherwise, this method
     *                    is free to alter component objects.
     * @param returnId  the sub-identifier for a desired component; if a component with this identifier 
     *                    is found, it will be returned; otherwise (or if returnId is null), null is returned.
     * @returns int -- the number of components selected from the resource 
     */
    public JSONObject cacheResource(JSONObject res, boolean copy, String returnId) {
        JSONArray comps = null;
        try {
            comps = res.getJSONArray("components");
            if (comps == null) return null;
        }
        catch (JSONException ex) {
            return null;
        }

        String resid = null;
        try {
            resid = res.getString("@id");
        } catch (JSONException ex) {
            resid = "";
        }

        int selCount = 0;
        JSONObject comp = null;
        JSONObject out = null;
        StringBuilder fullid = null;
        String subid = null;
        for(int i=0; i < comps.length(); i++) {
            try {
                comp = comps.getJSONObject(i);
                subid = comp.optString("@id", null);
                if (subid == null)
                    continue;
                if (returnId != null && returnId.equals(comp.optString("@id")))
                    out = comp;
                if (! shouldCache(comp))
                    continue;
                if (copy)
                    comp = new JSONObject(comp, JSONObject.getNames(comp));
                fullid = new StringBuilder(resid);
                if (! subid.startsWith("#")) fullid.append("/");
                fullid.append(subid);
                give(fullid.toString(), comp);
                selCount++;
            }
            catch (JSONException ex) { /* don't save */ }
        }

        return out;
    }

    /**
     * return true if the cache contains the component with the given identifier.  For 
     * records added via {@link #cacheResource(JSONObject,boolean,String) cacheResource()}, the identifier
     * will be the concatonation of the resource's and the component's identifiers.
     */
    public boolean containsId(String id) {
        return info.containsKey(id);
    }

    /**
     * return true if the given component matches the selection criteria established 
     * at construction time.  This is called within 
     * {@link #cacheResource(JSONObject,boolean,String) cacheResource()}
     * to determine which of its components should be saved to the cache.  
     */
    public boolean shouldCache(JSONObject comp) {
        if (retainTypes == null && excludeTypes == null)
            return true;
        
        JSONArray ctypes = null;
        int l = 1;
        try {
            ctypes = comp.getJSONArray("@type");
            l = ctypes.length();
        } catch (JSONException ex) { }
            
        if (ctypes == null) {
            try {
                Object[] tps = new Object[1];
                tps[0] = comp.getString("@type");
                ctypes = new JSONArray(tps);
            }
            catch (JSONException ex) {
                ctypes = new JSONArray(new Object[0]);
            }
        }

        ArrayList<String> types = new ArrayList<String>(l);
        for(int i=0; i < l; i++) {
            try {
                types.add(ctypes.getString(i));
            } catch (JSONException ex) { }
        }
        
        if (retainTypes != null) {
            boolean keep = false;
            for(String tp : types) {
                if (retainTypes.contains(tp)) {
                    keep = true;
                    break;
                }
                if (! keep) return false;
            }
        }

        if (excludeTypes != null) {
            for(String tp : types) {
                if (excludeTypes.contains(tp)) 
                    return false;
            }
        }
        return true;
    }

    /**
     * Weed out the contents of the cache, removing records that have exceeded their time or 
     * capacity limits.
     */
    public synchronized void weed() { info.weed(); }

    static class CompMap extends LinkedHashMap<String, JSONObject> {
        public int lim = 100;
        public long expire = 300000;  // 5 minutes

        public CompMap(int limit, long expireSecs, int initCap) {
            super(initCap, 0.75F, true);
            if (limit >= 0) lim = limit;
            if (expireSecs >= 0) expire = expireSecs * 1000;
        }

        public CompMap(int limit, long expireSecs) {
            this(limit, expireSecs, limit);
        }

        protected boolean isTooOld(JSONObject comp) {
            return (System.currentTimeMillis()-comp.optLong("_since",0L)) > expire;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, JSONObject> eldest) {
            return (size() > lim || isTooOld(eldest.getValue()));
        }

        @Override
        public JSONObject put(String id, JSONObject comp) {
            comp.put("_since", System.currentTimeMillis());
            return super.put(id, comp);
        }

        public void weed() {
            JSONObject comp = null;
            int pos = size();
            Iterator<Map.Entry<String, JSONObject>> it = entrySet().iterator();
            while (it.hasNext()) {
                comp = it.next().getValue();
                if (--pos >= lim || isTooOld(comp)) it.remove();
            }
        }
    }
}
