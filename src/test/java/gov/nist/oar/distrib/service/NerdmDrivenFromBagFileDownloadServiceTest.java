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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileSystemUtils;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.FileDescription;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

class NerdmDrivenFromBagFileDownloadServiceTest {

    Logger logger = LoggerFactory.getLogger(getClass());
    FilesystemLongTermStorage lts = null;
    NerdmDrivenFromBagFileDownloadService svc = null;

    static Path testdir = null;

    @BeforeAll
    public static void setUpClass() throws IOException {
        // create a test directory where we can write stuff
        Path indir = Paths.get(System.getProperty("user.dir"));
        if (indir.toFile().canWrite())
            testdir = Files.createTempDirectory(indir, "_unittest");
        else
            testdir = Files.createTempDirectory("_unittest");

        byte[] buf = new byte[100000];
        int len;
        String[] zips = {"mds1491.mbag0_2-0.zip", "mds1491.1_1_0.mbag0_4-1.zip"};
        for (String file : zips) {
            try (InputStream is = NerdmDrivenFromBagFileDownloadServiceTest.class.getResourceAsStream("/" + file)) {
                try (FileOutputStream os =
                             new FileOutputStream(new File(testdir.toFile(), file))) {
                    while ((len = is.read(buf)) != -1) {
                        os.write(buf, 0, len);
                    }
                }
            }
        }
    }

    @BeforeEach
    public void setUp() throws IOException {
        lts = new FilesystemLongTermStorage(testdir.toString());
        svc = new NerdmDrivenFromBagFileDownloadService(lts);
    }

    @AfterAll
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
            throws ResourceNotFoundException, DistributionException, IOException {
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
        logger.info("cache contains: " + svc.compcache.idSet().toString());
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

    @Test
    public void testGetDataFileInfoNotFound() {
        assertThrows(FileNotFoundException.class, () -> {
            svc.getDataFileInfo("mds1491", "trial3/goober.json", null);
        });
    }

    @Test
    public void testGetDataFileInfoBadID() {
        assertThrows(ResourceNotFoundException.class, () -> {
            svc.getDataFileInfo("goober", "trial1.json", null);
        });
    }

    @Test
    public void testUrlPathEncoding() throws DistributionException {
        assertEquals("just/a/path", NerdmDrivenFromBagFileDownloadService.urlPathEncode("just/a/path"));
        assertEquals("justapath", NerdmDrivenFromBagFileDownloadService.urlPathEncode("justapath"));
        assertEquals("just%20a%20path", NerdmDrivenFromBagFileDownloadService.urlPathEncode("just a path"));
    }
}
