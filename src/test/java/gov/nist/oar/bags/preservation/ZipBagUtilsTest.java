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

public class ZipBagUtilsTest {
    
    Logger logger = LoggerFactory.getLogger(ZipBagUtilsTest.class);
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
}
