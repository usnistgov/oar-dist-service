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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gov.nist.oar.clients.AmbiguousIDException;
import gov.nist.oar.clients.OARServiceException;
import gov.nist.oar.clients.OARWebServiceException;

public class RMMResourceResolverTest {

    static class MockRMMResourceResolver extends RMMResourceResolver {
        int remoteQueries = 0;

        public MockRMMResourceResolver(String ep) {
            super(ep, 10, -1);
        }

        @Override
        protected JSONObject getJSON(URL url)
                throws OARWebServiceException, JSONException, IOException {
            remoteQueries++;

            // get the resource data from a file on disk
            String ep = url.toString();
            if (!ep.startsWith(epURL))
                throw new OARWebServiceException(400, "Bad URL: " + ep);
            ep = ep.substring(epURL.length());
            if (ep.startsWith("?@id="))
                ep = ep.substring("?@id=".length());
            if (ep.length() == 0)
                throw new OARWebServiceException(400, "Bad URL: " + url.toString());
            if (ep.startsWith("ark:/88434/"))
                ep = ep.substring("ark:/88434/".length());
            if (ep.length() == 0)
                throw new OARWebServiceException(400, "Bad URL: " + url.toString());
            if (!ep.startsWith("/")) ep = "/" + ep;

            InputStream is = null;
            try {
                is = getClass().getResourceAsStream(ep + ".json");
                if (is == null)
                    return null;
                return new JSONObject(new JSONTokener(is));
            } finally {
                if (is != null) is.close();
            }
        }
    }

    MockRMMResourceResolver reslvr = null;

    @BeforeEach
    public void setUp() {
        reslvr = new MockRMMResourceResolver("https://localhost/rmm/records");
    }

    @AfterEach
    public void tearDown() {
        reslvr.compcache.clear();
    }

    @Test
    public void testIsRecognizedEDIID() throws OARServiceException {
        assertTrue(reslvr.isRecognizedEDIID("ark:/88434/mds2-2116"));
        assertTrue(reslvr.isRecognizedEDIID("ark:/88434/mds1-2116"));
        assertTrue(reslvr.isRecognizedEDIID("ark:/88434/mds3-2116"));
        assertTrue(reslvr.isRecognizedEDIID("ark:/88434/mds3-2116/goober"));
        assertTrue(reslvr.isRecognizedEDIID("ECBCC1C1301D2ED9E04306570681B10735"));
        assertTrue(reslvr.isRecognizedEDIID("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"));

        assertFalse(reslvr.isRecognizedEDIID("ark:/88434/mds0059kn8"));
        assertFalse(reslvr.isRecognizedEDIID("ark:/88435/mds2-2116"));
        assertFalse(reslvr.isRecognizedEDIID("mds2-2116"));
        assertFalse(reslvr.isRecognizedEDIID(""));
        assertFalse(reslvr.isRecognizedEDIID("ECBCC1C1301D2ED9E04306570681B"));
    }

    @Test
    public void testSplitID() throws OARServiceException {
        String[] need = new String[2];
        need[0] = "ark:/88434/mds3-2116";
        need[1] = "cmps/goober";
        assertArrayEquals(need, reslvr.splitID("ark:/88434/mds3-2116/cmps/goober"));

        need[1] = null;
        assertArrayEquals(need, reslvr.splitID("ark:/88434/mds3-2116"));

        need[1] = "";
        assertArrayEquals(need, reslvr.splitID("ark:/88434/mds3-2116/"));

        need[1] = "#doi";
        assertArrayEquals(need, reslvr.splitID("ark:/88434/mds3-2116#doi"));

        need[0] = "ECBCC1C1301D2ED9E04306570681B10735";
        need[1] = "goober";
        assertArrayEquals(need, reslvr.splitID("ECBCC1C1301D2ED9E04306570681B10735/goober"));

        assertThrows(AmbiguousIDException.class, () -> reslvr.splitID("http://goober.net/gurn/cranston"));
        assertThrows(AmbiguousIDException.class, () -> reslvr.splitID("handle://handle.net/gurn/cranston"));
    }

    @Test
    public void testResolveEDIID() throws OARServiceException {
        assertEquals(0, reslvr.getCacheSize());
        JSONObject rec = reslvr.resolveEDIID("3A1EE2F169DD3B8CE0531A570681DB5D1491");
        assertNotNull(rec, "Failed to find record");
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", rec.getString("ediid"));
        assertEquals("ark:/88434/mds00hw91v", rec.getString("@id"));
        assertEquals(6, reslvr.getCacheSize());

        assertNull(reslvr.resolveEDIID("ark:/88434/mds00hw91v"), "resource id treated as EDIID");
    }

    @Test
    public void testResolveResourceID() throws OARServiceException {
        assertEquals(0, reslvr.getCacheSize());
        JSONObject rec = reslvr.resolveResourceID("ark:/88434/mds00hw91v");
        assertNotNull(rec, "Failed to find record");
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", rec.getString("ediid"));
        assertEquals("ark:/88434/mds00hw91v", rec.getString("@id"));
        assertEquals(6, reslvr.getCacheSize());
    }

    @Test
    public void testResolveComponentID() throws OARServiceException {
        assertEquals(0, reslvr.getCacheSize());
        assertEquals(0, reslvr.remoteQueries);
        JSONObject rec = reslvr.resolveComponentID("ark:/88434/mds00hw91v/cmps/1491_optSortSphEvaluated20160701.cdf");
        assertNotNull(rec, "Failed to find component");
        assertEquals("cmps/1491_optSortSphEvaluated20160701.cdf", rec.getString("@id"));
        assertEquals("1491_optSortSphEvaluated20160701.cdf", rec.getString("filepath"));
        assertEquals(6, reslvr.getCacheSize());
        assertEquals(1, reslvr.remoteQueries);

        rec = reslvr.resolveComponentID("ark:/88434/mds00hw91v/cmps/1491_optSortSphEvaluated20160701.cdf.sha256");
        assertNotNull(rec, "Failed to find component");
        assertEquals("cmps/1491_optSortSphEvaluated20160701.cdf.sha256", rec.getString("@id"));
        assertEquals("1491_optSortSphEvaluated20160701.cdf.sha256", rec.getString("filepath"));
        assertEquals(6, reslvr.getCacheSize());
        assertEquals(1, reslvr.remoteQueries);

        rec = reslvr.resolveComponentID("ark:/88434/mds00hw91v#10.18434/T4SW26");
        assertNotNull(rec, "Failed to find component");
        assertEquals("#10.18434/T4SW26", rec.getString("@id"));
        assertEquals("http://doi.org/10.18434/T4SW26", rec.getString("accessURL"));
        assertEquals(6, reslvr.getCacheSize());
        assertEquals(2, reslvr.remoteQueries);

        rec = reslvr.resolveComponentID("ark:/88434/mds00hw91v/cmps/goober");
        assertNull(rec, "Found bogus component");
        assertEquals(6, reslvr.getCacheSize());
        assertEquals(3, reslvr.remoteQueries);
    }

    @Test
    public void testResolve() throws OARServiceException {
        assertEquals(0, reslvr.getCacheSize());
        assertEquals(0, reslvr.remoteQueries);
        JSONObject rec = reslvr.resolve("ark:/88434/mds00hw91v/cmps/1491_optSortSphEvaluated20160701.cdf");
        assertNotNull(rec, "Failed to find component");
        assertEquals("cmps/1491_optSortSphEvaluated20160701.cdf", rec.getString("@id"));
        assertEquals("1491_optSortSphEvaluated20160701.cdf", rec.getString("filepath"));
        assertEquals(6, reslvr.getCacheSize());
        assertEquals(1, reslvr.remoteQueries);

        rec = reslvr.resolve("ark:/88434/mds00hw91v/cmps/1491_optSortSphEvaluated20160701.cdf.sha256");
        assertNotNull(rec, "Failed to find component");
        assertEquals("cmps/1491_optSortSphEvaluated20160701.cdf.sha256", rec.getString("@id"));
        assertEquals("1491_optSortSphEvaluated20160701.cdf.sha256", rec.getString("filepath"));
        assertEquals(6, reslvr.getCacheSize());
        assertEquals(1, reslvr.remoteQueries);

        rec = reslvr.resolve("ark:/88434/mds00hw91v#10.18434/T4SW26");
        assertNotNull(rec, "Failed to find component");
        assertEquals("#10.18434/T4SW26", rec.getString("@id"));
        assertEquals("http://doi.org/10.18434/T4SW26", rec.getString("accessURL"));
        assertEquals(6, reslvr.getCacheSize());
        assertEquals(2, reslvr.remoteQueries);

        rec = reslvr.resolve("3A1EE2F169DD3B8CE0531A570681DB5D1491");
        assertNotNull(rec, "Failed to find record");
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", rec.getString("ediid"));
        assertEquals("ark:/88434/mds00hw91v", rec.getString("@id"));
        assertEquals(6, reslvr.getCacheSize());
        assertEquals(3, reslvr.remoteQueries);

        rec = reslvr.resolve("ark:/88434/mds00hw91v");
        assertNotNull(rec, "Failed to find record");
        assertEquals("3A1EE2F169DD3B8CE0531A570681DB5D1491", rec.getString("ediid"));
        assertEquals("ark:/88434/mds00hw91v", rec.getString("@id"));
        assertEquals(6, reslvr.getCacheSize());
        assertEquals(4, reslvr.remoteQueries);
    }
}
