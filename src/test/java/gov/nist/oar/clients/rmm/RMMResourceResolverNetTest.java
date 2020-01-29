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

import gov.nist.oar.clients.OARServiceException;
import gov.nist.oar.clients.OARWebServiceException;
import gov.nist.oar.clients.AmbiguousIDException;

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.ClassRule;
import org.junit.rules.TestRule;
import static org.junit.Assert.*;

import gov.nist.oar.RequireWebSite;
import gov.nist.oar.EnvVarIncludesWords;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONTokener;
import org.json.JSONException;

public class RMMResourceResolverNetTest {

    @ClassRule
    public static TestRule siterule = new RequireWebSite("https://data.nist.gov/rmm/resourceApi");
    
    @ClassRule
    public static TestRule envrule = new EnvVarIncludesWords("OAR_TEST_INCLUDE", "net");

    RMMResourceResolver reslvr = null;

    @Before
    public void setUp() {
        reslvr = new RMMResourceResolver("https://data.nist.gov/rmm/records", 10, -1);
    }

    @After
    public void tearDown() {
        reslvr.compcache.clear();
    }

    @Test
    public void testResolveEDIID() throws OARServiceException {
        assertEquals(0, reslvr.getCacheSize());
        JSONObject rec = reslvr.resolveEDIID("3A1EE2F169DD3B8CE0531A570681DB5D1491");
        assertNotNull("Failed to find record", rec);
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", rec.getString("ediid"));
        assertEquals("ark:/88434/mds00hw91v", rec.getString("@id"));
        assertEquals(6, reslvr.getCacheSize());

        assertNull("resource id treated as EDIID", reslvr.resolveEDIID("ark:/88434/mds00hw91v"));
    }

    @Test
    public void testResolveResourceID() throws OARServiceException {
        assertEquals(0, reslvr.getCacheSize());
        JSONObject rec = reslvr.resolveResourceID("ark:/88434/mds00hw91v");
        assertNotNull("Failed to find record", rec);
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", rec.getString("ediid"));
        assertEquals("ark:/88434/mds00hw91v", rec.getString("@id"));
        assertEquals(6, reslvr.getCacheSize());

        // assertNull("resource id treated as resource ID",
        //           reslvr.resolveEDIID("3A1EE2F169DD3B8CE0531A570681DB5D1491"));
    }

    @Test
    public void testResolveComponentID() throws OARServiceException {
        assertEquals(0, reslvr.getCacheSize());
        JSONObject rec =
            reslvr.resolveComponentID("ark:/88434/mds00hw91v/cmps/1491_optSortSphEvaluated20160701.cdf");
        assertNotNull("Failed to find component", rec);
        assertEquals("cmps/1491_optSortSphEvaluated20160701.cdf", rec.getString("@id"));
        assertEquals("1491_optSortSphEvaluated20160701.cdf", rec.getString("filepath"));
        assertEquals(6, reslvr.getCacheSize());

        rec =
          reslvr.resolveComponentID("ark:/88434/mds00hw91v/cmps/1491_optSortSphEvaluated20160701.cdf.sha256");
        assertNotNull("Failed to find component", rec);
        assertEquals("cmps/1491_optSortSphEvaluated20160701.cdf.sha256", rec.getString("@id"));
        assertEquals("1491_optSortSphEvaluated20160701.cdf.sha256", rec.getString("filepath"));
        assertEquals(6, reslvr.getCacheSize());

        rec = reslvr.resolveComponentID("ark:/88434/mds00hw91v#doi:10.18434/T4SW26");
        assertNotNull("Failed to find component", rec);
        assertEquals("#doi:10.18434/T4SW26", rec.getString("@id"));
        assertEquals("https://doi.org/10.18434/T4SW26", rec.getString("accessURL"));
        assertEquals(6, reslvr.getCacheSize());

        rec = reslvr.resolveComponentID("ark:/88434/mds00hw91v/cmps/goober");
        assertNull("Found bogus component", rec);
        assertEquals(6, reslvr.getCacheSize());
    }

}
