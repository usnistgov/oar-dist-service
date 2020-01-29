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
package gov.nist.oar.clients.rmm;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Iterator;
import java.util.Arrays;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class ComponentInfoCacheTest {

    ComponentInfoCache cache = null;

    public JSONObject makeComp(String id, String ...types) throws JSONException {
        JSONObject out = new JSONObject();
        out.put("@id", id); 
        out.put("size", 500L);
        out.put("@type", makeTypes(types));
        return out;
    }
    public JSONObject makeComp() throws JSONException {
        return makeComp("dcat:Distribution");
    }
    
    public JSONArray makeTypes(String ...types) throws JSONException {
        return new JSONArray(types);
    }

    public class Resource extends JSONObject {
        public Resource(String id) {
            super();
            put("@id", id);
            put("components", new JSONArray());
        }
        public String getId() throws JSONException { return getString("@id"); }
        public JSONArray getComps() throws JSONException { return getJSONArray("components"); }
        public void addComp(JSONObject comp) {
            getComps().put(comp);
        }
    }

    private long now() { return System.currentTimeMillis(); }

    @Test
    public void testCtor() {
        ComponentInfoCache cache = new ComponentInfoCache(5, -1L, 3);
        assertEquals(5, cache.getCapacity());
        assertEquals(0, cache.size());
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    public void testWeed() {
        ComponentInfoCache cache = new ComponentInfoCache(5, -1L, 3);
        
        JSONObject got = null;
        assertEquals(0, cache.size());

        JSONObject jo = makeComp();
        jo.put("name", "Henry");
        cache.give("hank", jo);
        assertEquals(1, cache.size());
        
        jo = makeComp();
        jo.put("name", "Gurn");
        cache.give("gurn", jo);
        assertEquals(2, cache.size());
        jo.put("_since", now()-600000);

        jo = makeComp();
        jo.put("name", "Theresa");
        cache.give("terry", jo);
        assertEquals(3, cache.size());

        cache.setCapacity(2);
        assertEquals(2, cache.getCapacity());
        cache.weed();
        assertEquals(1, cache.size());
        assertTrue(cache.containsId("terry"));
        assertFalse(cache.containsId("gurn"));
        assertFalse(cache.containsId("hank"));

        cache.give("therese", jo);
        assertEquals(2, cache.size());
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    public void testGivePutGet() {
        ComponentInfoCache cache = new ComponentInfoCache(5, -1L, 3);
        
        JSONObject got = null;
        assertEquals(0, cache.size());

        JSONObject jo = makeComp();
        jo.put("name", "Henry");
        jo.put("@id", "Henry");
        cache.give("hank", jo);
        assertEquals(1, cache.size());
        
        got = cache.get("hank");
        assertTrue(got.has("_since"));
        assertEquals("hank", got.getString("@id"));
        assertFalse(got.has("goob"));
        
        jo.put("goobs", 3);
        got = cache.get("hank");
        assertTrue(got.has("goobs"));

        cache.put("hank", jo);
        assertEquals(1, cache.size());
        got = cache.get("hank", true);
        assertTrue(! got.has("_since"));
        assertEquals("Henry", got.getString("@id"));
        
        jo.put("corns", 2);
        got = cache.get("hank");
        assertTrue(! got.has("corns"));
    }

    @Test
    public void testCacheResourceAll() {
        ComponentInfoCache cache = new ComponentInfoCache(5, -1L, 3);

        Resource res = new Resource("urn:big");
        res.addComp(makeComp("file", "nrdp:DataFile", "dcat:Distribution"));
        res.addComp(makeComp("file.sha256", "nrdp:ChecksumFile", "dcat:Distribution"));
        res.addComp(makeComp("#doi", "nrd:Hidden", "dcat:Distribution"));
        res.addComp(makeComp("db", "nrd:AccessPage", "dcat:Distribution"));
        assertEquals(0, cache.size());
        
        assertNull(cache.cacheResource(res, false, null));
        assertEquals(4, cache.size());
        assertTrue(cache.containsId("urn:big/file"));
        assertTrue(cache.containsId("urn:big/file.sha256"));
        assertTrue(cache.containsId("urn:big#doi"));
        assertTrue(cache.containsId("urn:big/db"));
    }

    @Test
    public void testCacheResourceInclude() {
        ComponentInfoCache cache = new ComponentInfoCache(5, -1L,
                                                          Arrays.asList("nrd:AccessPage", "nrdp:DataFile"),
                                                          null, false, 3);

        Resource res = new Resource("urn:big");
        res.addComp(makeComp("file", "nrdp:DataFile", "dcat:Distribution"));
        res.addComp(makeComp("file.sha256", "nrdp:ChecksumFile", "dcat:Distribution"));
        res.addComp(makeComp("#doi", "nrd:Hidden", "dcat:Distribution"));
        res.addComp(makeComp("db", "nrd:AccessPage", "dcat:Distribution"));
        assertEquals(0, cache.size());
        
        assertNull(cache.cacheResource(res, false, null));
        assertEquals(2, cache.size());
        assertTrue(cache.containsId("urn:big/file"));
        assertTrue(! cache.containsId("urn:big/file.sha256"));
        assertTrue(! cache.containsId("urn:big#doi"));
        assertTrue(cache.containsId("urn:big/db"));
    }

    @Test
    public void testCacheResourceExclude() {
        ComponentInfoCache cache = new ComponentInfoCache(5, -1L, null,
                                                          Arrays.asList("nrd:Hidden", "nrd:AccessPage"),
                                                          false, 3);

        Resource res = new Resource("urn:big");
        res.addComp(makeComp("file", "nrdp:DataFile", "dcat:Distribution"));
        res.addComp(makeComp("file.sha256", "nrdp:ChecksumFile", "dcat:Distribution"));
        res.addComp(makeComp("#doi", "nrd:Hidden", "dcat:Distribution"));
        res.addComp(makeComp("db", "nrd:AccessPage", "dcat:Distribution"));
        assertEquals(0, cache.size());
        
        assertNull(cache.cacheResource(res, false, null));
        assertEquals(2, cache.size());
        assertTrue(cache.containsId("urn:big/file"));
        assertTrue(cache.containsId("urn:big/file.sha256"));
        assertTrue(! cache.containsId("urn:big#doi"));
        assertTrue(! cache.containsId("urn:big/db"));
    }
    
    @Test
    public void testCacheResourceSelect() {
        ComponentInfoCache cache = new ComponentInfoCache(5, -1L,
                                                          Arrays.asList("nrd:AccessPage", "nrdp:DataFile"),
                                                          Arrays.asList("nrd:Hidden"), false, 3);

        Resource res = new Resource("urn:big");
        JSONObject cmp = makeComp("cmps/file", "nrdp:DataFile", "dcat:Distribution");
        cmp.put("filepath", "file");
        res.addComp(cmp);
        cmp = makeComp("cmps/file.sha256", "nrdp:ChecksumFile", "dcat:Distribution");
        cmp.put("filepath", "file.sha256");
        res.addComp(cmp);
        res.addComp(makeComp("#doi", "nrd:Hidden", "nrd:AccessPage", "dcat:Distribution"));
        res.addComp(makeComp("db", "nrd:AccessPage", "dcat:Distribution"));
        assertEquals(0, cache.size());
        
        assertNull(cache.cacheResource(res, false, null));
        assertEquals(2, cache.size());
        assertTrue(cache.containsId("urn:big/cmps/file"));
        assertTrue(! cache.containsId("urn:big/file"));
        assertTrue(! cache.containsId("urn:big/cmps/file.sha256"));
        assertTrue(! cache.containsId("urn:big/file.sha256"));
        assertTrue(! cache.containsId("urn:big#doi"));
        assertTrue(cache.containsId("urn:big/db"));
    }

    
    @Test
    public void testCacheResourceSelectByFile() {
        ComponentInfoCache cache = new ComponentInfoCache(5, -1L,
                                                          Arrays.asList("nrd:AccessPage", "nrdp:DataFile"),
                                                          Arrays.asList("nrd:Hidden"), true, 3);

        Resource res = new Resource("urn:big");
        JSONObject cmp = makeComp("cmps/file", "nrdp:DataFile", "dcat:Distribution");
        cmp.put("filepath", "file");
        res.addComp(cmp);
        cmp = makeComp("cmps/file.sha256", "nrdp:ChecksumFile", "dcat:Distribution");
        cmp.put("filepath", "file.sha256");
        res.addComp(cmp);
        res.addComp(makeComp("#doi", "nrd:Hidden", "nrd:AccessPage", "dcat:Distribution"));
        res.addComp(makeComp("db", "nrd:AccessPage", "dcat:Distribution"));
        assertEquals(0, cache.size());
        
        assertNull(cache.cacheResource(res, false, null));
        assertEquals(2, cache.size());
        assertTrue(cache.containsId("urn:big/file"));
        assertTrue(! cache.containsId("urn:big/file.sha256"));
        assertTrue(! cache.containsId("urn:big/cmps/file.sha256"));
        assertTrue(! cache.containsId("urn:big#doi"));
        assertTrue(cache.containsId("urn:big/db"));
    }

}
