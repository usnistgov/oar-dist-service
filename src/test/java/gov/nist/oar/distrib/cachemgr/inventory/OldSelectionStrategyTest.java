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

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.SelectionStrategy;
import gov.nist.oar.distrib.cachemgr.inventory.OldSelectionStrategy;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;

import java.util.List;
import java.util.ArrayList;

import org.json.JSONObject;
import org.json.JSONException;

public class OldSelectionStrategyTest {

    @Test
    public void testInit() {
        OldSelectionStrategy ss = new OldSelectionStrategy(10);

        assertEquals(ss.getPurpose(), "deletion_p");
        assertEquals(ss.getTotalSize(), 0);
        assertEquals(ss.getNormalPriority(), 10);
        assertEquals(ss.getMinimumAge(), 3600*1000);  // 1 hour
        assertFalse(ss.limitReached());
    }

    @Test
    public void testScore() throws JSONException {
        OldSelectionStrategy ss = new OldSelectionStrategy(3010, -1, 60000);
        assertEquals(ss.getNormalPriority(), 10);
        assertEquals(ss.getMinimumAge(), 60*1000);  // 1 minute

        JSONObject job = new JSONObject();
        job.put("size", 3000L);
        job.put("priority", 10);
        job.put("since", System.currentTimeMillis() - 20000);
        job.put("checksum", "s2h56a256");
        job.put("checksumAlgorithm", "sha256");
        job.put("refcount", 3);
        CacheObject co = new CacheObject("hank", job, "trash");
        assertEquals(co.score, 0.0, 0.0);

        ss.reset();
        assertEquals(0.0, ss.calculateScore(co), 0.001);

        // after 1 minute
        job.put("since", System.currentTimeMillis() - 60*1000);
        co = new CacheObject("bob", job, "trash");
        assertEquals(1/24.0/60.0, ss.calculateScore(co), 0.0001);

        // after one day
        job.put("since", System.currentTimeMillis() - 3600*1000*24);
        co = new CacheObject("bob", job, "trash");
        assertEquals(1.0, ss.calculateScore(co), 0.0001);

        job.put("priority", 20);
        co = new CacheObject("bob", job, "trash");
        assertEquals(2.0, ss.calculateScore(co), 0.0001);
    }
}
