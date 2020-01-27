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

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Iterator;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class CompMapTest {

    ComponentInfoCache.CompMap map = null;

    public JSONObject makeComp(String ...types) throws JSONException {
        JSONObject out = new JSONObject();
        out.put("@id", "#sample"); 
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

    private long now() { return System.currentTimeMillis(); }

    @Before
    public void setUp() {
        map = new ComponentInfoCache.CompMap(5, -1);
    }

    @Test
    public void testCtor() {
        assertEquals(5, map.lim);
        assertEquals(300000, map.expire);
        assertEquals(0, map.size());
    }

    @Test
    public void testIsTooOld() {
        JSONObject jo = makeComp();
        jo.put("_since", now()-1000);    // 1s in the past
        assertFalse(map.isTooOld(jo));
        jo.put("_since", now()-600000);  // 10m in the past
        assertTrue(map.isTooOld(jo));
        jo.put("_since", now()+100000);  // 100s in the future!
        assertFalse(map.isTooOld(jo));
    }

    @Test
    public void testRemoveEldestEntry() {
        JSONObject jo = makeComp();

        map.put("hank", jo);
        assertEquals(1, map.size());
        Map.Entry<String, JSONObject> e = map.entrySet().iterator().next();

        jo.put("_since", now()-600000);  // 10m in the past
        assertTrue(map.removeEldestEntry(e));
        jo.put("_since", now()-1000);    // 1s in the past
        assertFalse(map.removeEldestEntry(e));

        assertFalse(map.removeEldestEntry(e));
        map.put("gurn", jo);
        map.lim = 1;
        assertTrue(map.removeEldestEntry(e));
    }

    @Test
    public void testPut() throws InterruptedException {
        JSONObject jo = makeComp();
        jo.put("name", "Henry");
        JSONObject got = null;

        map.put("hank", jo);
        assertEquals(1, map.size());
        assertTrue(map.containsKey("hank"));
        got = map.get("hank");
        assertEquals(500L, got.getLong("size"));
        assertEquals("Henry", got.getString("name"));
        assertTrue(jo.has("_since"));
        Thread.sleep(1);

        jo = makeComp();
        jo.put("name", "Gurn");
        map.put("gurn", jo);
        assertEquals(2, map.size());
        got = map.get("hank");
        assertEquals(500L, got.getLong("size"));
        assertEquals("Henry", got.getString("name"));
        assertTrue(got.has("_since"));

        got = map.get("gurn");
        assertEquals(500L, got.getLong("size"));
        assertEquals("Gurn", got.getString("name"));
        assertTrue(got.has("_since"));
        assertNotEquals(got.getLong("_since"), map.get("hank").getLong("_since"));

        jo = makeComp();
        jo.put("name", "Theresa");
        map.put("terry", jo);
        got = map.get("terry");
        assertEquals(500L, got.getLong("size"));
        assertEquals("Theresa", got.getString("name"));
        assertTrue(got.has("_since"));
        assertEquals(3, map.size());
        got.remove("_since");
        assertTrue(jo.similar(got));
    }

    @Test
    public void testCaching() {
        JSONObject got = null;
        assertEquals(0, map.size());
        map.lim = 2;

        JSONObject jo = makeComp();
        jo.put("name", "Henry");
        map.put("hank", jo);
        assertEquals(1, map.size());
        
        jo = makeComp();
        jo.put("name", "Gurn");
        map.put("gurn", jo);
        assertEquals(2, map.size());
        
        jo = makeComp();
        jo.put("name", "Theresa");
        map.put("terry", jo);
        assertEquals(2, map.size());
        assertTrue(map.containsKey("terry"));
        assertTrue(map.containsKey("gurn"));
        assertFalse(map.containsKey("hank"));
    }

    @Test
    public void testWeed() {
        JSONObject got = null;
        assertEquals(0, map.size());

        JSONObject jo = makeComp();
        jo.put("name", "Henry");
        map.put("hank", jo);
        assertEquals(1, map.size());
        
        jo = makeComp();
        jo.put("name", "Gurn");
        map.put("gurn", jo);
        assertEquals(2, map.size());
        jo.put("_since", now()-600000);

        jo = makeComp();
        jo.put("name", "Theresa");
        map.put("terry", jo);
        assertEquals(3, map.size());

        map.lim = 2;
        map.weed();
        assertEquals(1, map.size());
        assertTrue(map.containsKey("terry"));
        assertFalse(map.containsKey("gurn"));
        assertFalse(map.containsKey("hank"));
    }

    @Test
    public void testUpdateAccess() {
        JSONObject got = null;
        assertEquals(0, map.size());

        JSONObject jo = makeComp();
        jo.put("name", "Henry");
        map.put("hank", jo);
        assertEquals(1, map.size());
        
        jo = makeComp();
        jo.put("name", "Gurn");
        map.put("gurn", jo);
        assertEquals(2, map.size());

        jo = makeComp();
        jo.put("name", "Theresa");
        map.put("terry", jo);
        assertEquals(3, map.size());

        // confirm insert order
        Iterator<JSONObject> it = map.values().iterator();
        assertEquals("Henry",   it.next().getString("name"));
        assertEquals("Gurn",    it.next().getString("name"));
        assertEquals("Theresa", it.next().getString("name"));

        // access changes the order
        map.get("gurn");
        it = map.values().iterator();
        assertEquals("Henry",   it.next().getString("name"));
        assertEquals("Theresa", it.next().getString("name"));
        assertEquals("Gurn",    it.next().getString("name"));
        map.get("terry");
        it = map.values().iterator();
        assertEquals("Henry",   it.next().getString("name"));
        assertEquals("Gurn",    it.next().getString("name"));
        assertEquals("Theresa", it.next().getString("name"));
        map.get("hank");
        it = map.values().iterator();
        assertEquals("Gurn",    it.next().getString("name"));
        assertEquals("Theresa", it.next().getString("name"));
        assertEquals("Henry",   it.next().getString("name"));
    }
}
