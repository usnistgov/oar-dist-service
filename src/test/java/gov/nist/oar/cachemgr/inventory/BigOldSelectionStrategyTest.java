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
package gov.nist.oar.cachemgr.inventory;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import gov.nist.oar.cachemgr.CacheObject;
import gov.nist.oar.cachemgr.SelectionStrategy;
import gov.nist.oar.cachemgr.inventory.BigOldSelectionStrategy;
import gov.nist.oar.cachemgr.inventory.SQLiteStorageInventoryDB;

import java.util.List;
import java.util.ArrayList;

import org.json.JSONObject;
import org.json.JSONException;

/**
 * this test also tests the SizeLimitedSelectionStrategy implementation
 */
public class BigOldSelectionStrategyTest {

    @Test
    public void testInit() {
        BigOldSelectionStrategy ss = new BigOldSelectionStrategy(10);

        assertEquals(ss.getPurpose(), "deletion_s");
        assertEquals(ss.getTotalSize(), 0);
        assertEquals(ss.getTurnOverAge(), 2.5*3600*1000, 0.5);
        assertEquals(ss.getTurnOverSize(), 5.0e8, 0.5);
        assertFalse(ss.limitReached());
    }

    @Test
    public void testScore() throws JSONException {
        BigOldSelectionStrategy ss = new BigOldSelectionStrategy(3010, -1.0, 1.0e3);
        assertEquals(ss.getTurnOverAge(), 2.5*3600*1000, 0.5);
        assertEquals(ss.getTurnOverSize(), 1.0e3, 0.5);

        JSONObject job = new JSONObject();
        job.put("size", 3000L);
        job.put("priority", 10);
        job.put("since", System.currentTimeMillis() - 3600*1000*26);
        job.put("checksum", "s2h56a256");
        job.put("checksumAlgorithm", "sha256");
        job.put("refcount", 3);
        CacheObject co = new CacheObject("hank", job, "trash");
        assertEquals(co.score, 0.0, 0.0);

        assertEquals(10.0, ss.score(co), 0.1);
        assertEquals(10.0, co.score, 0.1);
        assertEquals(ss.getTotalSize(), 3000L);
        assertFalse(ss.limitReached());

        job.put("size", 100);
        co = new CacheObject("bob", job, "trash");

        assertEquals(1.0, ss.score(co), 0.1);
        assertEquals(1.0, co.score, 0.1);
        assertEquals(3100, ss.getTotalSize());
        assertTrue(ss.limitReached());
    }

    @Test
    public void testCalculateScoreByAge() throws JSONException {
        BigOldSelectionStrategy ss = new BigOldSelectionStrategy(10, -1.0, 1.0e3);

        JSONObject job = new JSONObject();
        job.put("size", 3000L);  // size doesn't matter
        job.put("priority", 1);

        // less that the turn-over time (1 hr < 2.5 hrs.)
        job.put("since", System.currentTimeMillis() - 3600*1000);
        CacheObject co = new CacheObject("hank", job, "trash");

        ss.reset();
        assertEquals(0.0, ss.calculateScore(co), 0.001);

        // after one day
        job.put("since", System.currentTimeMillis() - 3600*1000*25);
        co = new CacheObject("bob", job, "trash");
        assertEquals(1.0, ss.calculateScore(co), 0.1);

        // after two days
        job.put("since", System.currentTimeMillis() - 3600*1000*50);
        co = new CacheObject("bob", job, "trash");
        assertEquals(2.0, ss.calculateScore(co), 0.1);
    }

    @Test
    public void testCalculateScoreBySize() throws JSONException {
        BigOldSelectionStrategy ss = new BigOldSelectionStrategy(10, -1.0, 1.0e3);

        JSONObject job = new JSONObject();
        job.put("since", System.currentTimeMillis() - 3600*1000*26);
        job.put("priority", 1);

        // size >> turn-over
        job.put("size", 3000L);  // size doesn't matter
        CacheObject co = new CacheObject("bob", job, "trash");
        assertEquals(1.0, ss.calculateScore(co), 0.01);

        // size << turn-over: linear
        job.put("size", 100L);  
        co = new CacheObject("hank", job, "trash");
        assertEquals(0.1, ss.calculateScore(co), 0.01);

        // size << turn-over: linear
        job.put("size", 50L);  
        co = new CacheObject("hank", job, "trash");
        assertEquals(0.05, ss.calculateScore(co), 0.01);
    }

}
