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
 * @author: Deoyani Nandrekar-Heinis
 */
package gov.nist.oar.distrib.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import java.util.List;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import org.springframework.util.FileSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.StreamHandle;
import gov.nist.oar.distrib.FileDescription;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.service.PreservationBagService;
import gov.nist.oar.distrib.service.DefaultPreservationBagService;
import gov.nist.oar.distrib.ResourceNotFoundException;


public class FromBagFileDownloadServiceTest {

    FilesystemLongTermStorage lts = null;
    FromBagFileDownloadService svc = null;
    
    static Path testdir = null;

    @BeforeClass
    public static void setUpClass() throws IOException {
        // create a test directory where we can write stuff
        Path indir = Paths.get(System.getProperty("user.dir"));
        if (indir.toFile().canWrite())
            testdir = Files.createTempDirectory(indir, "_unittest");
        else
            testdir = Files.createTempDirectory("_unittest");

        byte[] buf = new byte[100000];
        int len;
        String[] zips = { "mds1491.mbag0_2-0.zip", "mds1491.1_1_0.mbag0_4-1.zip" };
        for (String file : zips) {
            try (InputStream is = FromBagFileDownloadServiceTest.class.getResourceAsStream("/"+file)) {
                try (FileOutputStream os =
                        new FileOutputStream(new File(testdir.toFile(), file)))
                {
                    while ((len = is.read(buf)) != -1) {
                        os.write(buf, 0, len);
                    }
                }
            }
        }
    }

    @Before
    public void setUp() throws IOException {
        lts = new FilesystemLongTermStorage(testdir.toString());
        svc = new FromBagFileDownloadService(lts);
    }

    @AfterClass
    public static void tearDownClass() {
        FileSystemUtils.deleteRecursively(testdir.toFile());
        testdir = null;
    }

    @Test
    public void testCtor() {
        assertNotNull(svc.pres);
        svc = new FromBagFileDownloadService(new DefaultPreservationBagService(lts));
        assertNotNull(svc.pres);
    }

    @Test
    public void testListDataFiles() throws ResourceNotFoundException, DistributionException {
        List<String> files = svc.listDataFiles("mds1491", "0");
        assertTrue(files.contains("trial1.json"));
        assertTrue(files.contains("trial2.json"));
        assertTrue(files.contains("trial3/trial3a.json"));
        assertEquals(3, files.size());

        files = svc.listDataFiles("mds1491", null);
        assertTrue(files.contains("trial1.json"));
        assertTrue(files.contains("trial2.json"));
        assertTrue(files.contains("trial3/trial3a.json"));
        assertTrue(files.contains("trial3/trial3a.json"));
        assertTrue(files.contains("trial3/trial3a.json.sha256"));
        assertEquals(5, files.size());
    }

    @Test(expected = ResourceNotFoundException.class)
    public void testListDataFilesBadVer() throws ResourceNotFoundException, DistributionException {
        svc.listDataFiles("mds1491", "12.2");
    }

    @Test(expected = ResourceNotFoundException.class)
    public void testListDataFilesBadID() throws ResourceNotFoundException, DistributionException {
        svc.listDataFiles("goober", null);
    }

    @Test
    public void testGetDataFile()
        throws ResourceNotFoundException, DistributionException, FileNotFoundException, IOException
    {
        StreamHandle sh = svc.getDataFile("mds1491", "trial3/trial3a.json", "0");
        assertNotNull(sh.dataStream);
        assertEquals("trial3/trial3a.json", sh.getInfo().name);
        assertEquals(70, sh.getInfo().contentLength);

        BufferedReader rdr = new BufferedReader(new InputStreamReader(sh.dataStream));
        String line = rdr.readLine();
        assertTrue(line.startsWith("{"));
        rdr.close();

        sh = svc.getDataFile("mds1491", "trial3/trial3a.json", null);
        assertNotNull(sh.dataStream);
        assertEquals("trial3/trial3a.json", sh.getInfo().name);
        assertEquals(70, sh.getInfo().contentLength);

        rdr = new BufferedReader(new InputStreamReader(sh.dataStream));
        line = rdr.readLine();
        assertTrue(line.startsWith("{"));
        rdr.close();

        sh = svc.getDataFile("mds1491", "trial3/trial3a.json.sha256", null);
        assertNotNull(sh.dataStream);
        assertEquals("trial3/trial3a.json.sha256", sh.getInfo().name);
        assertEquals(65, sh.getInfo().contentLength);

        rdr = new BufferedReader(new InputStreamReader(sh.dataStream));
        line = rdr.readLine();
        assertTrue(line.startsWith("d155d99281"));
        rdr.close();
    }

    @Test(expected = FileNotFoundException.class)
    public void testGetDataFileBadFile()
        throws ResourceNotFoundException, DistributionException, FileNotFoundException
    {
        StreamHandle sh = svc.getDataFile("mds1491", "trial3/trial3a.json.sha256", "0");
    }

    @Test(expected = ResourceNotFoundException.class)
    public void testGetDataFileBadID()
        throws ResourceNotFoundException, DistributionException, FileNotFoundException
    {
        StreamHandle sh = svc.getDataFile("goober", "trial1.json", null);
    }

    @Test
    public void testGetDataFileInfo()
        throws ResourceNotFoundException, DistributionException, FileNotFoundException, IOException
    {
        FileDescription fd = svc.getDataFileInfo("mds1491", "trial3/trial3a.json", "0");
        assertEquals("trial3/trial3a.json", fd.name);
        assertEquals(70, fd.contentLength);
        assertNull(fd.checksum);
        assertEquals("application/json", fd.contentType);
    }

    @Test
    public void testGetDefaultContentType() {
        assertEquals("application/octet-stream", svc.getDefaultContentType("goober"));
        assertEquals("text/plain",               svc.getDefaultContentType("goober.txt"));
        assertEquals("image/jpeg",               svc.getDefaultContentType("goober.jpg"));
        assertEquals("application/pdf",          svc.getDefaultContentType("goober.pdf"));
        assertEquals("image/png",                svc.getDefaultContentType("goober.png"));
        assertEquals("application/json",         svc.getDefaultContentType("goober.json"));
    }
}
