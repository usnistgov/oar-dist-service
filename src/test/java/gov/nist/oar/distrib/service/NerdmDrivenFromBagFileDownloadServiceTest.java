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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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


public class NerdmDrivenFromBagFileDownloadServiceTest {

    Logger logger = LoggerFactory.getLogger(getClass());
    FilesystemLongTermStorage lts = null;
    NerdmDrivenFromBagFileDownloadService svc = null;
    
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
            try (InputStream is = NerdmDrivenFromBagFileDownloadServiceTest.class.getResourceAsStream("/"+file)) {
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
        svc = new NerdmDrivenFromBagFileDownloadService(lts);
    }

    @AfterClass
    public static void tearDownClass() {
        FileSystemUtils.deleteRecursively(testdir.toFile());
        testdir = null;
    }

    @Test
    public void testCtor() {
        assertNotNull(svc.pres);
        svc = new NerdmDrivenFromBagFileDownloadService(new DefaultPreservationBagService(lts));
        assertNotNull(svc.pres);
        assertNotNull(svc.compcache);
        assertEquals(0, svc.compcache.size());
    }

    @Test
    public void testGetDataFileInfo()
        throws ResourceNotFoundException, DistributionException, FileNotFoundException, IOException
    {
        assertEquals(0, svc.compcache.size());

        FileDescription fd = svc.getDataFileInfo("mds1491", "trial3/trial3a.json", "0");
        assertEquals("trial3/trial3a.json", fd.name);
        assertEquals(70, fd.contentLength);
        assertNotNull(fd.checksum);
        assertEquals("application/json", fd.contentType);
        assertEquals(0, svc.compcache.size());

        fd = svc.getDataFileInfo("mds1491", "trial1.json.sha256", null);
        assertEquals("trial1.json.sha256", fd.name);
        assertEquals(90, fd.contentLength);
        assertEquals("application/octet-stream", fd.contentType);
        logger.info("cache contains: "+svc.compcache.idSet().toString());
        assertEquals(6, svc.compcache.size());

        fd = svc.getDataFileInfo("mds1491", "trial2.json", null);
        assertEquals("trial2.json", fd.name);
        assertEquals(69, fd.contentLength);
        assertEquals("application/json", fd.contentType);
        assertEquals(6, svc.compcache.size());

        fd = svc.getDataFileInfo("mds1491", "sim++.json", null);
        assertEquals("sim++.json", fd.name);
        assertEquals(2900000, fd.contentLength);
        assertNull(fd.checksum);
        assertEquals("application/json", fd.contentType);
        assertEquals(6, svc.compcache.size());
    }

    @Test(expected = FileNotFoundException.class)
    public void testGetDataFileInfoNotFound()
        throws ResourceNotFoundException, DistributionException, FileNotFoundException, IOException
    {
        FileDescription fd = svc.getDataFileInfo("mds1491", "trial3/goober.json", null);
    }

    @Test(expected = ResourceNotFoundException.class)
    public void testGetDataFileInfoBadID()
        throws ResourceNotFoundException, DistributionException, FileNotFoundException
    {
        FileDescription fd = svc.getDataFileInfo("goober", "trial1.json", null);
    }

    @Test
    public void testUrlPathEncoding() throws DistributionException {
        assertEquals("just/a/path", svc.urlPathEncode("just/a/path"));
        assertEquals("justapath", svc.urlPathEncode("justapath"));
        assertEquals("just%20a%20path", svc.urlPathEncode("just a path"));
    }
}
