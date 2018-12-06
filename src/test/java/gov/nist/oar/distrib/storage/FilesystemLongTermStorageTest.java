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
package gov.nist.oar.distrib.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import org.springframework.util.FileSystemUtils;

import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.ResourceNotFoundException;

public class FilesystemLongTermStorageTest {
  
    Path testdir = null;
  
    @Before
    public void setUp() throws IOException {
        // create a test directory where we can write stuff
        Path indir = Paths.get(System.getProperty("user.dir"));
        if (indir.toFile().canWrite())
            testdir = Files.createTempDirectory(indir, "_unittest");
        else
            testdir = Files.createTempDirectory("_unittest");
        
        // setup a little repo
        String[] bases = {
            "mds013u4g.1_0_0.mbag0_4-", "mds013u4g.1_0_1.mbag0_4-", "mds013u4g.1_1.mbag0_4-",
            "mds088kd2.mbag0_3-", "mds088kd2.mbag0_3-", "mds088kd2.1_0_1.mbag0_4-"
        };
        Path f = null;
        int j = 0;
        for (String base : bases) {
            for(int i=0; i < 3; i++) {
                String bag = base + Integer.toString(j++) + ((i > 1) ? ".7z" : ".zip");
                f = Paths.get(testdir.toString(), bag);
                if (! Files.exists(f)) 
                    Files.createFile(f);
                try (FileWriter w = new FileWriter(f.toString()+".sha256")) {
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
  
    @After
    public void tearDown(){
        FileSystemUtils.deleteRecursively(testdir.toFile());
        testdir = null;
    }
  
    @Test
    public void testCtor() throws FileNotFoundException {
        FilesystemLongTermStorage stor = new FilesystemLongTermStorage(testdir.toString());
        assertEquals(stor.rootdir.toString(), testdir.toString());

        assert(! (new File("/goober")).exists());
        try {
            stor = new FilesystemLongTermStorage("/goober");
            fail("Failed to detected nonexistent directory");
        } catch (FileNotFoundException ex) { }
    }
  
    @Test
    public void testFindBagsFor() throws DistributionException, FileNotFoundException {
        LongTermStorage stor = new FilesystemLongTermStorage(testdir.toString());

        List<String> filenames = new ArrayList<String>();
        filenames.add("mds013u4g.1_0_0.mbag0_4-0.zip");
        filenames.add("mds013u4g.1_0_0.mbag0_4-1.zip");
        filenames.add("mds013u4g.1_0_0.mbag0_4-2.7z");
        filenames.add("mds013u4g.1_0_1.mbag0_4-3.zip");
        filenames.add("mds013u4g.1_0_1.mbag0_4-4.zip");
        filenames.add("mds013u4g.1_0_1.mbag0_4-5.7z");
        filenames.add("mds013u4g.1_1.mbag0_4-6.zip");
        filenames.add("mds013u4g.1_1.mbag0_4-7.zip");
        filenames.add("mds013u4g.1_1.mbag0_4-8.7z");
 
        assertEquals(filenames, stor.findBagsFor("mds013u4g"));

        try {
            filenames = stor.findBagsFor("mds013u4g9");
            fail("Failed to raise ResourceNotFoundException; returned "+filenames.toString());
        } catch (ResourceNotFoundException ex) { }

        filenames =  new ArrayList<String>();
        filenames.add("mds088kd2.mbag0_3-9.zip");
        filenames.add("mds088kd2.mbag0_3-10.zip");
        filenames.add("mds088kd2.mbag0_3-11.7z");
        filenames.add("mds088kd2.mbag0_3-12.zip");
        filenames.add("mds088kd2.mbag0_3-13.zip");
        filenames.add("mds088kd2.mbag0_3-14.7z");
        filenames.add("mds088kd2.1_0_1.mbag0_4-15.zip");
        filenames.add("mds088kd2.1_0_1.mbag0_4-16.zip");
        filenames.add("mds088kd2.1_0_1.mbag0_4-17.7z");
 
        assertEquals(filenames, stor.findBagsFor("mds088kd2"));
    }

    @Test
    public void testFileChecksum() throws FileNotFoundException, DistributionException  {
        LongTermStorage fStorage = new FilesystemLongTermStorage(testdir.toString());

        String hash="e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        String getChecksumHash = fStorage.getChecksum("mds088kd2.mbag0_3-10.zip").hash;
        assertEquals(getChecksumHash.trim(),hash.trim());

        hash="38acb15d02d5ac0f2a2789602e9df950c380d2799b4bdb59394e4eeabdd3a662";
        getChecksumHash = fStorage.getChecksum("mds088kd2.mbag0_3-10.zip.sha256").hash;
        assertEquals(getChecksumHash.trim(),hash.trim());
    }

    @Test
    public void testFileSize() throws FileNotFoundException, DistributionException  {
        LongTermStorage fStorage = new FilesystemLongTermStorage(testdir.toString());

        long filelength = fStorage.getSize("mds088kd2.1_0_1.mbag0_4-17.7z");
        assertEquals(0, filelength);

        filelength = fStorage.getSize("mds088kd2.1_0_1.mbag0_4-17.7z.sha256");
        assertEquals(95, filelength);
    } 

    //Need to update deatils to compare two file streams
    @Test
    public void testFileStream() throws FileNotFoundException, DistributionException, IOException  {
        LongTermStorage fStorage = new FilesystemLongTermStorage(testdir.toString());

        InputStream is = fStorage.openFile("mds088kd2.1_0_1.mbag0_4-17.7z");
        // the test file happens to be empty
        assertEquals(-1, is.read());
        is.close();

        is = fStorage.openFile("mds088kd2.1_0_1.mbag0_4-17.7z.sha256");
        byte[] buf = new byte[100];
        assertEquals(95, is.read(buf));
        assertEquals(-1, is.read());
        is.close();

        try {
            is = fStorage.openFile("goober-17.7z");
            fail("Failed to barf on missing file");
            is.close();
        } catch (FileNotFoundException ex) { }
    } 

    @Test
    public void testFileHeadbag() throws FileNotFoundException, DistributionException {
        LongTermStorage fStorage = new FilesystemLongTermStorage(testdir.toString());

        assertEquals("mds088kd2.1_0_1.mbag0_4-17.7z", fStorage.findHeadBagFor("mds088kd2")); 
        assertEquals("mds013u4g.1_1.mbag0_4-8.7z",    fStorage.findHeadBagFor("mds013u4g"));

        assertEquals("mds013u4g.1_1.mbag0_4-8.7z",    fStorage.findHeadBagFor("mds013u4g", "1.1"));
        assertEquals("mds013u4g.1_0_1.mbag0_4-5.7z",  fStorage.findHeadBagFor("mds013u4g", "1.0.1"));
        assertEquals("mds013u4g.1_0_0.mbag0_4-2.7z",  fStorage.findHeadBagFor("mds013u4g", "1.0.0"));

        assertEquals("mds088kd2.1_0_1.mbag0_4-17.7z", fStorage.findHeadBagFor("mds088kd2", "1.0.1")); 
        assertEquals("mds088kd2.mbag0_3-14.7z", fStorage.findHeadBagFor("mds088kd2", "0")); 
        assertEquals("mds088kd2.mbag0_3-14.7z", fStorage.findHeadBagFor("mds088kd2", "1")); 

        try {
            String bagname = fStorage.findHeadBagFor("mds013u4g9");
            fail("Failed to raise ResourceNotFoundException; returned "+bagname.toString());
        } catch (ResourceNotFoundException ex) { }

    }
}
