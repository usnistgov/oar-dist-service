/*
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
package gov.nist.oar.bags.preservation;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.zip.ZipEntry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ZipBagUtilsNerdmExtractorTest {

    InputStream bagstrm = null;
    final String bagname = "mds1491.1_1_0.mbag0_4-1";
    final String zipfile = "/mds1491.1_1_0.mbag0_4-1.zip";
    final String bagver = "0.4";
    // Logger logger = LoggerFactory.getLogger(ZipBagUtilsTest.class);
    public ZipBagUtilsNerdmExtractorTest() {}

    @BeforeEach
    public void setUp() throws IOException {
        bagstrm = getClass().getResourceAsStream(zipfile);
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (bagstrm != null) bagstrm.close();
    }

    @Test
    public void testCtor() throws IOException {
        ZipBagUtils.NerdmExtractor extr = new ZipBagUtils.NerdmExtractor(bagver, bagstrm, bagname);
        assertEquals("mds1491.1_1_0.mbag0_4-1/multibag/file-lookup.tsv", extr.fluname);
        assertEquals("mds1491.1_1_0.mbag0_4-1/metadata/", extr.mdatadir);
        assertEquals("mds1491.1_1_0.mbag0_4-1/metadata/nerdm.json", extr.resmdatafile);
        assertNotNull(extr.zipstrm);
    }

    @Test
    public void testIsNerdmFile() {
        ZipBagUtils.NerdmExtractor extr = new ZipBagUtils.NerdmExtractor(bagver, bagstrm, bagname);
        assertTrue(extr.isNerdmFile("mds1491.1_1_0.mbag0_4-1/metadata/nerdm.json"));
        assertTrue(extr.isNerdmFile("mds1491.1_1_0.mbag0_4-1/metadata/trial1.json/nerdm.json"));
        assertTrue(extr.isNerdmFile("mds1491.1_1_0.mbag0_4-1/metadata/subdir/nerdm.json"));

        assertFalse(extr.isNerdmFile("mds1491.1_1_0.mbag0_4-1/metadata/pod.json"));
        assertFalse(extr.isNerdmFile("metadata/nerdm.json"));
        assertFalse(extr.isNerdmFile("mds1491.1_1_0.mbag0_4-1/metadata/trial1.json"));
        assertFalse(extr.isNerdmFile("mds1491.1_1_0.mbag0_4-1/multibag/file-lookup.tsv"));
        assertFalse(extr.isNerdmFile("mds1491.1_1_0.mbag0_4-1/multibag/group-directory.txt"));
    }

    @Test
    public void testIsLookupFile() throws IOException {
        ZipBagUtils.NerdmExtractor extr = new ZipBagUtils.NerdmExtractor(bagver, bagstrm, bagname);
        assertTrue(extr.isLookupFile("mds1491.1_1_0.mbag0_4-1/multibag/file-lookup.tsv"));

        assertFalse(extr.isLookupFile("mds1491.1_1_0.mbag0_4-1/multibag/group-directory.txt"));
        assertFalse(extr.isLookupFile("mds1491.1_1_0.mbag0_4-1/metadata/trial1.json/nerdm.json"));

        bagstrm.close();
        bagstrm = getClass().getResourceAsStream("/mds1491.mbag0_2-0.zip");
        assertNotNull(bagstrm);
        extr = new ZipBagUtils.NerdmExtractor("0.2", bagstrm, "mds1491.mbag0_2-0");
        assertTrue(extr.isLookupFile("mds1491.mbag0_2-0/multibag/group-directory.txt"));
        assertFalse(extr.isLookupFile("mds1491.mbag0_2-0/multibag/file-lookup.tsv"));
        assertFalse(extr.isLookupFile("mds1491.mbag0_2-0/metadata/trial1.json/nerdm.json"));
    }

    @Test
    public void testAnnotateLocation() throws JSONException {
        ZipBagUtils.NerdmExtractor extr = new ZipBagUtils.NerdmExtractor(bagver, bagstrm, bagname);

        // setup a fake component metadata object and a fake lookup map
        JSONObject comp = new JSONObject();
        comp.put("filepath", "a/b/c.txt");
        HashMap<String,String> lu = new HashMap<String,String>(2);
        lu.put("data/c.dat", "here");
        lu.put("data/a/b/c.txt", "there");
        lu.put("bagit.txt", "there");

        extr.annotateLocation(comp, lu);
        assertEquals("there", comp.get("_location"));

        comp = new JSONObject();
        comp.put("filepath", "favicon.ico");
        extr.annotateLocation(comp, lu);
        assertEquals(JSONObject.NULL, comp.get("_location"));
    }

    @Test
    public void testLoadFileLocations() throws IOException {
        HashMap<String, String> locs = new HashMap<String, String>(4);
        ZipBagUtils.NerdmExtractor extr = new ZipBagUtils.NerdmExtractor(bagver, bagstrm, bagname);

        // manually find the file-lookup
        ZipEntry ze = null;
        while ((ze = extr.zipstrm.getNextEntry()) != null) {
            if (ze.getName().equals(bagname+"/multibag/file-lookup.tsv"))
                break;
        }
        extr.loadFileLocations(locs);

        assertTrue(locs.containsKey("data/trial1.json"));
        assertTrue(locs.containsKey("data/trial2.json"));
        assertTrue(locs.containsKey("data/trial3/trial3a.json"));
        assertEquals("mds1491.1_1_0.mbag0_4-1", locs.get("data/trial1.json"));

        assertFalse(locs.containsKey("metadata/trial3/trial3a.json"));
        assertFalse(locs.containsKey("metadata/trial3/trial3a.json/nerdm.json"));
        assertFalse(locs.containsKey("bagit.txt"));
    }

    @Test
    public void testExtract() throws IOException, JSONException {
        ZipBagUtils.NerdmExtractor extr = new ZipBagUtils.NerdmExtractor(bagver, bagstrm, bagname);

        JSONObject res = extr.extract();
        assertNotNull(res);
        assertEquals("ark:/88434/edi00hw91c", res.optString("@id"));
        JSONArray comps = res.getJSONArray("components");
        assertEquals(8, comps.length());

        JSONObject cmp = null;
        for(int i=0; i < comps.length(); i++) {
            cmp = comps.getJSONObject(i);
            if (! cmp.has("filepath"))
                assertFalse(cmp.has("_location"), "Unexpectedly annotated: "+cmp.opt("@id"));
            else if (cmp.optString("filepath").equals("sim++.json"))
                assertEquals(JSONObject.NULL, cmp.opt("_location"));
            else if (cmp.optString("filepath").equals("trial2.json"))
                assertEquals("mds1491.mbag0_2-0", cmp.opt("_location"));
            else if (cmp.optString("filepath").equals("trial3"))
                assertEquals(JSONObject.NULL, cmp.opt("_location"));
            else 
                assertEquals("mds1491.1_1_0.mbag0_4-1", cmp.opt("_location"), 
                    "Unexpected bagname: "+cmp.optString("filepath")+" ("+Integer.toString(i)+") ");
        }
    }
}
