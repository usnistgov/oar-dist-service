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
package gov.nist.oar.distrib.cachemgr.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import gov.nist.oar.distrib.cachemgr.CacheObject;

/**
 * this test also tests the SizeLimitedSelectionStrategy implementation
 */
public class BySizeSelectionStrategyTest {

    @Test
    public void testInit() {
        BySizeSelectionStrategy ss = new BySizeSelectionStrategy(10);

        assertEquals(ss.getPurpose(), "deletion_s");
        assertEquals(ss.getTotalSize(), 0);
        assertFalse(ss.limitReached());
        assertEquals(ss.getNormalizingSize(), 5.0e8, 1.0);
    }

    @Test
    public void testScore() throws JSONException {
        BySizeSelectionStrategy ss = new BySizeSelectionStrategy(10, 5, 1.0);
        assertEquals(ss.getNormalizingSize(), 1.0, 1.0e-8);
        assertEquals(ss.getTotalSize(), 0L);
        assertEquals(ss.getSufficientSize(), 0L);

        JSONObject job = new JSONObject();
        job.put("size", 3L);
        job.put("checksum", "s2h56a256");
        job.put("checksumAlgorithm", "sha256");
        job.put("refcount", 3);
        CacheObject co = new CacheObject("hank", job, "trash");
        assertEquals(co.score, 0.0, 0.0);

        assertEquals(ss.score(co), 3.0, 0.0);
        assertEquals(co.score, 3.0, 0.0);
        assertEquals(ss.getTotalSize(), 3L);
        assertFalse(ss.limitReached());

        job.put("size", 4L);
        co = new CacheObject("bob", job, "trash");
        assertEquals(ss.score(co), 4.0, 0.0);
        
        job.put("size", 5L);
        co = new CacheObject("bob", job, "trash");
        assertEquals(ss.score(co), 5.0, 0.0);
        
        assertEquals(co.score, 5.0, 1.0e-8);
        assertEquals(ss.getTotalSize(), 12L);
        assertEquals(ss.getSufficientSize(), 7L);
        assertTrue(ss.limitReached());

        ss.reset();
        assertFalse(ss.limitReached());
    }

    @Test
    public void testCalculateScore() throws JSONException {
        BySizeSelectionStrategy ss = new BySizeSelectionStrategy(10, 1.0);

        JSONObject job = new JSONObject();
        job.put("size", 3L);
        job.put("checksum", "s2h56a256");
        job.put("checksumAlgorithm", "sha256");
        job.put("refcount", 3);
        CacheObject co = new CacheObject("hank", job, "trash");
        assertEquals(co.score, 0.0, 0.0);

        assertEquals(ss.calculateScore(co), 3.0, 0.0);
        assertEquals(co.score, 0.0, 0.0);
        assertEquals(ss.getTotalSize(), 0L);
        assertFalse(ss.limitReached());

        job.put("size", 9L);
        co = new CacheObject("bob", job, "trash");
        assertEquals(ss.calculateScore(co), 9.0, 1.0e-8);
    }

    @Test
    public void testNormalizer() throws JSONException {
        BySizeSelectionStrategy ss = new BySizeSelectionStrategy(10, 10.0);

        JSONObject job = new JSONObject();
        job.put("size", 3L);
        job.put("checksum", "s2h56a256");
        job.put("checksumAlgorithm", "sha256");
        job.put("refcount", 3);
        CacheObject co = new CacheObject("hank", job, "trash");
        assertEquals(co.score, 0.0, 0.0);

        assertEquals(ss.calculateScore(co), 0.3, 0.0);
        assertEquals(co.score, 0.0, 0.0);
        assertEquals(ss.getTotalSize(), 0L);
        assertFalse(ss.limitReached());

        job.put("size", 9L);
        co = new CacheObject("bob", job, "trash");
        assertEquals(ss.calculateScore(co), 0.9, 1.0e-8);
    }

    @Test
    public void testSort() {
        List<CacheObject> list = new ArrayList<CacheObject>();
        BySizeSelectionStrategy ss = new BySizeSelectionStrategy(10, 1.0);

        JSONObject job = new JSONObject();
        job.put("size", 3L);
        job.put("checksum", "s2h56a256");
        job.put("checksumAlgorithm", "sha256");
        job.put("refcount", 3);
        CacheObject co = new CacheObject("hank", job, "trash");

        ss.score(co);
        list.add(co);
        assertEquals(3L,  list.get(0).getSize());

        job = new JSONObject(job, JSONObject.getNames(job));
        job.put("size", 1L);
        co = new CacheObject("hank", job, "trash");
        ss.score(co);
        list.add(co);
        assertEquals(1L,  list.get(1).getSize());
        
        job = new JSONObject(job, JSONObject.getNames(job));
        job.put("size", 18L);
        co = new CacheObject("hank", job, "trash");
        ss.score(co);
        list.add(co);
        assertEquals(18L,  list.get(2).getSize());
        
        job = new JSONObject(job, JSONObject.getNames(job));
        job.put("size", 5L);
        co = new CacheObject("hank", job, "trash");
        ss.score(co);
        list.add(co);
        assertEquals(5L,  list.get(3).getSize());
        
        assertEquals(3L,  list.get(0).getSize());
        assertEquals(1L,  list.get(1).getSize());
        assertEquals(18L, list.get(2).getSize());
        assertEquals(5L,  list.get(3).getSize());

        ss.sort(list);
        
        assertEquals(18L, list.get(0).getSize());
        assertEquals(5L,  list.get(1).getSize());
        assertEquals(3L,  list.get(2).getSize());
        assertEquals(1L,  list.get(3).getSize());
    }
}
