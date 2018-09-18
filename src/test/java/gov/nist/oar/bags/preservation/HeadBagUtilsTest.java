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


import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import gov.nist.oar.bags.preservation.BagUtils;
import gov.nist.oar.ds.service.impl.DownloadServiceImpl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.List;

public class HeadBagUtilsTest {
    
    Logger logger = LoggerFactory.getLogger(HeadBagUtilsTest.class);
    public HeadBagUtilsTest() {}

    @Test
    public void testLookupFile() throws IOException {
        InputStream zis = getClass().getResourceAsStream("/mds1491.mbag0_2-0.zip");
        zis = ZipBagUtils.openFileLookup("0.2", zis, "mds1491.mbag0_2-0").stream;
        assertEquals("mds1491.mbag0_2-0",
                     HeadBagUtils.lookupFile("0.2", zis, "data/trial3/trial3a.json"));
        zis.close();

        zis = getClass().getResourceAsStream("/mds1491.mbag0_2-0.zip");
        zis = ZipBagUtils.openFileLookup("0.2", zis, "mds1491.mbag0_2-0").stream;
        assertEquals("mds1491.mbag0_2-0", HeadBagUtils.lookupFile("0.2", zis, "data/trial2.json"));
        zis.close();

        zis = getClass().getResourceAsStream("/mds1491.1_1_0.mbag0_4-1.zip");
        zis = ZipBagUtils.openFileLookup("0.4", zis, "mds1491.1_1_0.mbag0_4-1").stream;
        assertEquals("mds1491.1_1_0.mbag0_4-1",
                    HeadBagUtils.lookupFile("0.4", zis, "data/trial3/trial3a.json"));
        zis.close();

        zis = getClass().getResourceAsStream("/mds1491.1_1_0.mbag0_4-1.zip");
        zis = ZipBagUtils.openFileLookup("0.4", zis, "mds1491.1_1_0.mbag0_4-1").stream;
        assertEquals("mds1491.mbag0_2-0", HeadBagUtils.lookupFile("0.4", zis, "data/trial2.json"));
        zis.close();

        // test assuming the latest multibag version
        zis = getClass().getResourceAsStream("/mds1491.1_1_0.mbag0_4-1.zip");
        zis = ZipBagUtils.openFileLookup("0.4", zis, "mds1491.1_1_0.mbag0_4-1").stream;
        assertEquals("mds1491.1_1_0.mbag0_4-1",
                     HeadBagUtils.lookupFile(zis, "data/trial3/trial3a.json"));
        zis.close();

        zis = getClass().getResourceAsStream("/mds1491.1_1_0.mbag0_4-1.zip");
        zis = ZipBagUtils.openFileLookup("0.4", zis, "mds1491.1_1_0.mbag0_4-1").stream;
        assertEquals("mds1491.mbag0_2-0", HeadBagUtils.lookupFile(zis, "data/trial2.json"));
        zis.close();

        // non-existent request
        zis = getClass().getResourceAsStream("/mds1491.1_1_0.mbag0_4-1.zip");
        zis = ZipBagUtils.openFileLookup("0.4", zis, "mds1491.1_1_0.mbag0_4-1").stream;
        assertNull(HeadBagUtils.lookupFile(zis, "goober.json"));
        zis.close();

    }

    @Test
    public void testListMemberBags() throws IOException {
        InputStream zis = getClass().getResourceAsStream("/mds1491.mbag0_2-0.zip");
        zis = ZipBagUtils.openMemberBags("0.2", zis, "mds1491.mbag0_2-0").stream;
        List<String> bags = HeadBagUtils.listMemberBags("0.2", zis);
        assertEquals("mds1491.mbag0_2-0", bags.get(0));
        assertEquals(1, bags.size());

        zis = getClass().getResourceAsStream("/mds1491.1_1_0.mbag0_4-1.zip");
        zis = ZipBagUtils.openMemberBags("0.3", zis, "mds1491.1_1_0.mbag0_4-1").stream;
        bags = HeadBagUtils.listMemberBags("0.3", zis);
        assertEquals("mds1491.mbag0_2-0", bags.get(0));
        assertEquals("mds1491.1_1_0.mbag0_4-1", bags.get(1));
        assertEquals(2, bags.size());

        // assume latest multibag version
        zis = getClass().getResourceAsStream("/mds1491.1_1_0.mbag0_4-1.zip");
        zis = ZipBagUtils.openMemberBags("0.3", zis, "mds1491.1_1_0.mbag0_4-1").stream;
        bags = HeadBagUtils.listMemberBags(zis);
        assertEquals("mds1491.mbag0_2-0", bags.get(0));
        assertEquals("mds1491.1_1_0.mbag0_4-1", bags.get(1));
        assertEquals(2, bags.size());
    }

    @Test
    public void testListDataFiles() throws IOException {
        InputStream zis = getClass().getResourceAsStream("/mds1491.mbag0_2-0.zip");
        zis = ZipBagUtils.openFileLookup("0.2", zis, "mds1491.mbag0_2-0").stream;
        List<String> files = HeadBagUtils.listDataFiles("0.2", zis);
        assertTrue(files.contains("trial1.json"));
        assertTrue(files.contains("trial2.json"));
        assertTrue(files.contains("trial3/trial3a.json"));
        assertEquals(3, files.size());

        zis = getClass().getResourceAsStream("/mds1491.1_1_0.mbag0_4-1.zip");
        zis = ZipBagUtils.openFileLookup("0.3", zis, "mds1491.1_1_0.mbag0_4-1").stream;
        files = HeadBagUtils.listDataFiles("0.3", zis);
        assertTrue(files.contains("trial1.json"));
        assertTrue(files.contains("trial1.json.sha256"));
        assertTrue(files.contains("trial2.json"));
        assertTrue(files.contains("trial3/trial3a.json"));
        assertTrue(files.contains("trial3/trial3a.json.sha256"));
        assertEquals(5, files.size());

        // assume latest multibag version
        zis = getClass().getResourceAsStream("/mds1491.1_1_0.mbag0_4-1.zip");
        zis = ZipBagUtils.openFileLookup("0.3", zis, "mds1491.1_1_0.mbag0_4-1").stream;
        files = HeadBagUtils.listDataFiles(zis);
        assertTrue(files.contains("trial1.json"));
        assertTrue(files.contains("trial2.json"));
        assertTrue(files.contains("trial3/trial3a.json"));
        assertEquals(5, files.size());

    }
}

