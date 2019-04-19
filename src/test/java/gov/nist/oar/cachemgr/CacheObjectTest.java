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
package gov.nist.oar.cachemgr.unit;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import gov.nist.oar.cachemgr.CacheObject;

import java.util.Set;
import org.json.JSONObject;
import org.json.JSONException;

public class CacheObjectTest {

    public CacheObjectTest() {}

    @Test
    public void testWithName() {
        CacheObject co = new CacheObject("hank");
        assertEquals(co.name, "hank");
        assertNull(co.volume);
        assertNull(co.volname);
        assertEquals(co.metadatumNames().size(), 0);
        assertEquals(co.score, 0.0, 0.00000001);
    }

    @Test
    public void testWithNameVolName() {
        CacheObject co = new CacheObject("hank", "trash");
        assertEquals(co.name, "hank");
        assertEquals(co.volname, "trash");
        assertEquals(co.metadatumNames().size(), 0);
    }

    @Test
    public void testNoMetadata() {
        CacheObject co = new CacheObject("hank");
        assertEquals(co.metadatumNames().size(), 0);
        assertEquals(co.getSize(), -1L);
        assertEquals(co.getMetadatumString("foo", "bar"), "bar");
        assertEquals(co.getMetadatumInt("foo", 3), 3);
        assertEquals(co.getMetadatumLong("foo", 4), 4);
    }

    @Test
    public void testMetadata() {
        JSONObject job = new JSONObject();
        job.put("size", 31L);
        job.put("checksum", "s2h56a256");
        job.put("checksumAlgorithm", "sha256");
        job.put("refcount", 3);

        CacheObject co = new CacheObject("hank", job, (String)null);

        assertEquals(co.name, "hank");
        assertNull(co.volume);
        assertEquals(co.getSize(), 31L);
        assertEquals(co.getMetadatumLong("size", -1L), 31L);
        assertEquals(co.getMetadatumString("checksum", null), "s2h56a256");
        assertEquals(co.getMetadatumInt("refcount", -1), 3);
    }
}
