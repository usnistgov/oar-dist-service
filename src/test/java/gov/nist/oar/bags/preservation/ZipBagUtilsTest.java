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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

public class ZipBagUtilsTest {
    
    // Logger logger = LoggerFactory.getLogger(ZipBagUtilsTest.class);
    public ZipBagUtilsTest() {}

    @Test
    public void testOpenEntryCtoR() throws IOException {
        ZipBagUtils.OpenEntry oe = new ZipBagUtils.OpenEntry();
        assertNull(oe.name);
        assertNull(oe.info);
        assertNull(oe.stream);

        ZipInputStream zis =
            new ZipInputStream(getClass().getResourceAsStream("/mds1491.mbag0_2-0.zip"));
        ZipEntry ze = zis.getNextEntry();
        oe = new ZipBagUtils.OpenEntry(ze.getName(), ze, zis);

        assertNotNull(oe.stream);
        assertSame(ze, oe.info);
        assertEquals(ze.getName(), oe.name);
    }

    @Test
    public void testOpenFile() throws IOException, FileNotFoundException {
        InputStream zis = getClass().getResourceAsStream("/mds1491.mbag0_2-0.zip");
        ZipBagUtils.OpenEntry oe = ZipBagUtils.openFile(zis, "mds1491.mbag0_2-0/bagit.txt");

        assertEquals("mds1491.mbag0_2-0/bagit.txt", oe.name);
        assertEquals("mds1491.mbag0_2-0/bagit.txt", oe.info.getName());
        assertNotNull(oe.stream);

        BufferedReader rdr = new BufferedReader(new InputStreamReader(oe.stream));
        String line = rdr.readLine();
        assertTrue(line.startsWith("BagIt-Version: 0.97"));
        rdr.close();
    }

    @Test
    public void testOpenDataFile() throws IOException, FileNotFoundException {
        InputStream zis = getClass().getResourceAsStream("/mds1491.mbag0_2-0.zip");
        ZipBagUtils.OpenEntry oe = ZipBagUtils.openDataFile(zis, "mds1491.mbag0_2-0",
                                                            "trial3/trial3a.json");

        assertEquals("mds1491.mbag0_2-0/data/trial3/trial3a.json", oe.name);
        assertEquals("mds1491.mbag0_2-0/data/trial3/trial3a.json", oe.info.getName());
        assertNotNull(oe.stream);

        BufferedReader rdr = new BufferedReader(new InputStreamReader(oe.stream));
        String line = rdr.readLine();
        assertTrue(line.startsWith("{"));
        rdr.close();
    }

    @Test
    public void testOpenMemberBags() throws IOException, FileNotFoundException {
        InputStream zis = getClass().getResourceAsStream("/mds1491.mbag0_2-0.zip");
        ZipBagUtils.OpenEntry oe = ZipBagUtils.openMemberBags("0.2", zis, "mds1491.mbag0_2-0");

        assertEquals("mds1491.mbag0_2-0/multibag/group-members.txt", oe.name);
        assertEquals("mds1491.mbag0_2-0/multibag/group-members.txt", oe.info.getName());
        assertNotNull(oe.stream);

        BufferedReader rdr = new BufferedReader(new InputStreamReader(oe.stream));
        String line = rdr.readLine();
        assertTrue(line.startsWith("mds1491.mbag0_2-0"));
        rdr.close();

        zis = getClass().getResourceAsStream("/mds1491.1_1_0.mbag0_4-1.zip");
        oe = ZipBagUtils.openMemberBags("0.4", zis, "mds1491.1_1_0.mbag0_4-1");

        assertEquals("mds1491.1_1_0.mbag0_4-1/multibag/member-bags.tsv", oe.name);
        assertEquals("mds1491.1_1_0.mbag0_4-1/multibag/member-bags.tsv", oe.info.getName());
        assertNotNull(oe.stream);

        rdr = new BufferedReader(new InputStreamReader(oe.stream));
        line = rdr.readLine();
        assertTrue(line.startsWith("mds1491.mbag0_2-0"));
        line = rdr.readLine();
        assertTrue(line.startsWith("mds1491.1_1_0.mbag0_4-1"));
        rdr.close();

        // assume latest version of profile
        zis = getClass().getResourceAsStream("/mds1491.1_1_0.mbag0_4-1.zip");
        oe = ZipBagUtils.openMemberBags(zis, "mds1491.1_1_0.mbag0_4-1");

        assertEquals("mds1491.1_1_0.mbag0_4-1/multibag/member-bags.tsv", oe.name);
        assertEquals("mds1491.1_1_0.mbag0_4-1/multibag/member-bags.tsv", oe.info.getName());
        assertNotNull(oe.stream);

        rdr = new BufferedReader(new InputStreamReader(oe.stream));
        line = rdr.readLine();
        assertTrue(line.startsWith("mds1491.mbag0_2-0"));
        line = rdr.readLine();
        assertTrue(line.startsWith("mds1491.1_1_0.mbag0_4-1"));
        rdr.close();
    }

    @Test
    public void testOpenFileLookup() throws IOException, FileNotFoundException {
        InputStream zis = getClass().getResourceAsStream("/mds1491.mbag0_2-0.zip");
        ZipBagUtils.OpenEntry oe = ZipBagUtils.openFileLookup("0.2", zis, "mds1491.mbag0_2-0");

        assertEquals("mds1491.mbag0_2-0/multibag/group-directory.txt", oe.name);
        assertEquals("mds1491.mbag0_2-0/multibag/group-directory.txt", oe.info.getName());
        assertNotNull(oe.stream);

        BufferedReader rdr = new BufferedReader(new InputStreamReader(oe.stream));
        String line = rdr.readLine();
        assertTrue(line.contains(" mds1491.mbag0_2-0"));
        rdr.close();

        zis = getClass().getResourceAsStream("/mds1491.1_1_0.mbag0_4-1.zip");
        oe = ZipBagUtils.openFileLookup("0.4", zis, "mds1491.1_1_0.mbag0_4-1");

        assertEquals("mds1491.1_1_0.mbag0_4-1/multibag/file-lookup.tsv", oe.name);
        assertEquals("mds1491.1_1_0.mbag0_4-1/multibag/file-lookup.tsv", oe.info.getName());
        assertNotNull(oe.stream);

        rdr = new BufferedReader(new InputStreamReader(oe.stream));
        line = rdr.readLine();
        assertTrue(line.contains("\tmds1491.1_1_0.mbag0_4-1"));
        rdr.close();

        // assume latest version of profile
        zis = getClass().getResourceAsStream("/mds1491.1_1_0.mbag0_4-1.zip");
        oe = ZipBagUtils.openFileLookup(zis, "mds1491.1_1_0.mbag0_4-1");

        assertEquals("mds1491.1_1_0.mbag0_4-1/multibag/file-lookup.tsv", oe.name);
        assertEquals("mds1491.1_1_0.mbag0_4-1/multibag/file-lookup.tsv", oe.info.getName());
        assertNotNull(oe.stream);

        rdr = new BufferedReader(new InputStreamReader(oe.stream));
        line = rdr.readLine();
        assertTrue(line.contains("\tmds1491.1_1_0.mbag0_4-1"));
        rdr.close();
    }

    @Test
    public void testGetFileMetadata() throws IOException, JSONException {
        try (InputStream zis = getClass().getResourceAsStream("/mds1491.mbag0_2-0.zip")) {
            JSONObject cmp = ZipBagUtils.getFileMetadata("trial3/trial3a.json", zis, "mds1491.mbag0_2-0");
            assertNotNull(cmp);
            assertEquals(70, cmp.getLong("size"));
            assertEquals("cmps/trial3/trial3a.json", cmp.getString("@id"));
        }

        try (InputStream zis = getClass().getResourceAsStream("/mds1491.1_1_0.mbag0_4-1.zip")) {
            JSONObject cmp = ZipBagUtils.getFileMetadata("trial2.json", zis, "mds1491.1_1_0.mbag0_4-1");
            assertNotNull(cmp);
            assertTrue(! cmp.has("size"));
            assertEquals("cmps/trial2.json", cmp.getString("@id"));
        }

        try (InputStream zis = getClass().getResourceAsStream("/mds1491.1_1_0.mbag0_4-1.zip")) {
            JSONObject cmp = ZipBagUtils.getFileMetadata("sim++.json", zis, "mds1491.1_1_0.mbag0_4-1");
            assertNotNull(cmp);
            assertEquals(2900000, cmp.getLong("size"));
            assertEquals("cmps/sim++.json", cmp.getString("@id"));
        }
    }

    @Test
public void testGetFileMetadataNotFound() throws IOException, JSONException {
    assertThrows(FileNotFoundException.class, () -> {
        try (InputStream zis = getClass().getResourceAsStream("/mds1491.mbag0_2-0.zip")) {
            ZipBagUtils.getFileMetadata("goober.json", zis, "mds1491.mbag0_2-0");
        }
    });
}

    @Test
    public void testgetResourceMetadata() throws IOException, JSONException {
        try (InputStream zis = getClass().getResourceAsStream("/mds1491.mbag0_2-0.zip")) {
            JSONObject res = ZipBagUtils.getResourceMetadata("0.2", zis, "mds1491.mbag0_2-0");
            assertNotNull(res);
            assertEquals("ark:/88434/edi00hw91c", res.optString("@id"));
            JSONArray comps = res.getJSONArray("components");
            assertEquals(5, comps.length());
        }
    }
}
