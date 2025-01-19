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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.FileSystemUtils;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.FileDescription;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StreamHandle;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

public class DefaultPreservationBagServiceTest {

    PreservationBagService pres;
    List<String> filenames = new ArrayList<>();

    static Path testdir = null;

    public static void populateStorage() throws IOException {
        // create a test directory where we can write stuff
        Path indir = Paths.get(System.getProperty("user.dir"));
        if (indir.toFile().canWrite()) {
            testdir = Files.createTempDirectory(indir, "_unittest");
        } else {
            testdir = Files.createTempDirectory("_unittest");
        }

        // setup a little repo
        String[] bases = {
            "mds013u4g.1_0_0.mbag0_4-", "mds013u4g.1_0_1.mbag0_4-", "mds013u4g.1_1.mbag0_4-",
            "mds088kd2.mbag0_3-", "mds088kd2.mbag0_3-", "mds088kd2.1_0_1.mbag0_4-",
            "6376FC675D0E1D77E0531A5706812BC21886.mbag0_3-"
        };
        Path f = null;
        int j = 0;
        for (String base : bases) {
            for (int i = 0; i < 3; i++) {
                String bag = base + Integer.toString(j++) + ((i > 1) ? ".7z" : ".zip");
                f = Paths.get(testdir.toString(), bag);
                if (!Files.exists(f)) {
                    Files.createFile(f);
                }
                try (FileWriter w = new FileWriter(f.toString() + ".sha256")) {
                    w.write("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
                    if (i > 1) {
                        w.write(" ");
                        w.write(bag);
                    }
                    w.write("\n");
                }
            }
        }
    }

    @BeforeAll
    public static void setUpClass() throws IOException {
        populateStorage();
    }

    @BeforeEach
    public void setUp() throws FileNotFoundException {
        pres = new DefaultPreservationBagService(new FilesystemLongTermStorage(testdir.toString()));
    }

    @AfterAll
    public static void tearDownClass() {
        FileSystemUtils.deleteRecursively(testdir.toFile());
        testdir = null;
    }

    @Test
    public void listBagsException() {
        assertThrows(ResourceNotFoundException.class, () -> {
            pres.listBags("6376FC675D0E1D77E0531A5706812BC00001");
        });
    }

    @Test
    public void listBags() throws DistributionException {
        List<String> fnames = pres.listBags("6376FC675D0E1D77E0531A5706812BC21886");
        assertEquals("6376FC675D0E1D77E0531A5706812BC21886.mbag0_3-18.zip", fnames.get(0));
        assertEquals("6376FC675D0E1D77E0531A5706812BC21886.mbag0_3-19.zip", fnames.get(1));
        assertEquals("6376FC675D0E1D77E0531A5706812BC21886.mbag0_3-20.7z", fnames.get(2));
        assertEquals(3, fnames.size());

        fnames = pres.listBags("mds088kd2");
        assertEquals(9, fnames.size());
        for (String name : fnames) {
            assertTrue(name.startsWith("mds088kd2."), "Unexpected bag name returned: " + name);
        }
    }

    @Test
    public void testListVersions() throws DistributionException {
        List<String> versions = pres.listVersions("mds013u4g");
        assertTrue(versions.contains("1.0.0"));
        assertTrue(versions.contains("1.0.1"));
        assertTrue(versions.contains("1.1"));
        assertEquals(3, versions.size());

        versions = pres.listVersions("mds088kd2");
        assertTrue(versions.contains("0"));
        assertTrue(versions.contains("1.0.1"));
        assertEquals(2, versions.size());

        versions = pres.listVersions("6376FC675D0E1D77E0531A5706812BC21886");
        assertTrue(versions.contains("0"));
        assertEquals(1, versions.size());
    }

    @Test
    public void getHeadBagName() throws DistributionException {
        String headbag = "6376FC675D0E1D77E0531A5706812BC21886.mbag0_3-20.7z";
        assertEquals(pres.getHeadBagName("6376FC675D0E1D77E0531A5706812BC21886"), headbag);

        assertEquals("mds013u4g.1_1.mbag0_4-8.7z", pres.getHeadBagName("mds013u4g"));
        assertEquals("mds088kd2.1_0_1.mbag0_4-17.7z", pres.getHeadBagName("mds088kd2"));

        assertEquals(headbag, pres.getHeadBagName("6376FC675D0E1D77E0531A5706812BC21886", ""));
        assertEquals(headbag, pres.getHeadBagName("6376FC675D0E1D77E0531A5706812BC21886", "0"));
        assertEquals(headbag, pres.getHeadBagName("6376FC675D0E1D77E0531A5706812BC21886", "1"));
        assertEquals("mds013u4g.1_1.mbag0_4-8.7z", pres.getHeadBagName("mds013u4g", "1.1"));
        assertEquals("mds013u4g.1_1.mbag0_4-8.7z", pres.getHeadBagName("mds013u4g", "1.1.0"));
        assertEquals("mds013u4g.1_0_0.mbag0_4-2.7z", pres.getHeadBagName("mds013u4g", "1.0.0"));
        assertEquals("mds013u4g.1_0_1.mbag0_4-5.7z", pres.getHeadBagName("mds013u4g", "1.0.1"));
        assertEquals("mds088kd2.mbag0_3-14.7z", pres.getHeadBagName("mds088kd2", "0"));
        assertEquals("mds088kd2.1_0_1.mbag0_4-17.7z", pres.getHeadBagName("mds088kd2", "1.0.1"));
    }

    @Test
    public void testGetHeadBagNameBadID() {
        assertThrows(ResourceNotFoundException.class, () -> {
            pres.getHeadBagName("goober");
        });
    }

    @Test
    public void testGetHeadBagNameBadVer() {
        assertThrows(ResourceNotFoundException.class, () -> {
            pres.getHeadBagName("mds013u4g", "2.1");
        });
    }

    @Test
    public void testGetBag() throws FileNotFoundException, DistributionException {
        StreamHandle sh = pres.getBag("mds013u4g.1_0_0.mbag0_4-2.7z");
        assertNotNull(sh.dataStream);
        assertTrue(sh.getInfo().checksum.hash.startsWith("e3b0c44298f"));
        assertEquals(0, sh.getInfo().contentLength);
        assertEquals("mds013u4g.1_0_0.mbag0_4-2.7z", sh.getInfo().name);
        assertEquals("application/x-7z-compressed", sh.getInfo().contentType);

        sh = pres.getBag("6376FC675D0E1D77E0531A5706812BC21886.mbag0_3-18.zip");
        assertNotNull(sh.dataStream);
        assertTrue(sh.getInfo().checksum.hash.startsWith("e3b0c44298f"));
        assertEquals(0, sh.getInfo().contentLength);
        assertEquals("6376FC675D0E1D77E0531A5706812BC21886.mbag0_3-18.zip", sh.getInfo().name);
        assertEquals("application/zip", sh.getInfo().contentType);
    }

    @Test
    public void testGetBagInfo() throws FileNotFoundException, DistributionException {
        FileDescription fd = pres.getInfo("mds013u4g.1_0_0.mbag0_4-2.7z");
        assertTrue(fd.checksum.hash.startsWith("e3b0c44298f"));
        assertEquals(0, fd.contentLength);
        assertEquals("mds013u4g.1_0_0.mbag0_4-2.7z", fd.name);
        assertEquals("application/x-7z-compressed", fd.contentType);

        fd = pres.getInfo("6376FC675D0E1D77E0531A5706812BC21886.mbag0_3-18.zip");
        assertTrue(fd.checksum.hash.startsWith("e3b0c44298f"));
        assertEquals(0, fd.contentLength);
        assertEquals("6376FC675D0E1D77E0531A5706812BC21886.mbag0_3-18.zip", fd.name);
        assertEquals("application/zip", fd.contentType);
    }
}
