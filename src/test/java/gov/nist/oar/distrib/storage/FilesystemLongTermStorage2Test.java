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

import static org.junit.Assert.*;

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
import gov.nist.oar.distrib.StorageStateException;
import gov.nist.oar.distrib.StorageVolumeException;

// This set of sets was brought in from a parallel implementation in support of the cache manager.
// It is replicated here to ensure behavioral compatibility of the LongTermStorage interface. 
public class FilesystemLongTermStorage2Test {
  
    Path testdir = null;
    String rightHash = "38acb15d02d5ac0f2a2789602e9df950c380d2799b4bdb59394e4eeabdd3a662";
    String wrongHash = "Ha!";
  
    @Before
    public void setUp() throws IOException {
        // create a test directory where we can write stuff
        Path indir = Paths.get(System.getProperty("user.dir"));
        if (indir.toFile().canWrite())
            testdir = Files.createTempDirectory(indir, "_unittest");
        else
            testdir = Files.createTempDirectory("_unittest");
        
        // setup a little repo
        String[] bases = { "data.7z", "missing.nosha" };
        Path f = null;
        int j = 0;
        for (String base : bases) {
            File out = new File(testdir.toFile(), base);
            try (FileWriter w = new FileWriter(out)) {
                w.write("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\n");
            }
            if (! out.getName().endsWith(".nosha")) {
                try (FileWriter w = new FileWriter(new File(testdir.toFile(), base+".sha256"))) {
                    w.write(wrongHash);
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
    public void testExists() throws StorageVolumeException, FileNotFoundException {
        LongTermStorage fStorage = new FilesystemLongTermStorage(testdir.toString());
        assertTrue(fStorage.exists("data.7z"));
        assertFalse(fStorage.exists("goober!"));
    }
        
    @Test
    public void testGetSize() throws StorageVolumeException, FileNotFoundException {
        LongTermStorage fStorage = new FilesystemLongTermStorage(testdir.toString());
        assertEquals(65, fStorage.getSize("data.7z"));
        try {
            fStorage.getSize("goober!");
            fail("Found non-existant file, goober!");
        } catch (FileNotFoundException ex) { }
    }

    @Test
    public void testGetChecksum() throws StorageVolumeException, FileNotFoundException {
        LongTermStorage fStorage = new FilesystemLongTermStorage(testdir.toString());
        assertEquals(wrongHash, fStorage.getChecksum("data.7z").hash);
        assertEquals(rightHash, fStorage.getChecksum("missing.nosha").hash);
    }
  
    //Need to update deatils to compare two file streams
    @Test
    public void testFileStream() throws FileNotFoundException, StorageVolumeException, IOException  {
        LongTermStorage fStorage = new FilesystemLongTermStorage(testdir.toString());

        InputStream is = fStorage.openFile("data.7z");
        byte[] buf = new byte[100];
        assertEquals(65, is.read(buf));
        assertEquals(-1, is.read());
        is.close();

        is = fStorage.openFile("data.7z.sha256");
        assertEquals(3, is.read(buf));
        assertEquals(-1, is.read());
        is.close();

        try {
            is = fStorage.openFile("goober-17.7z");
            fail("Failed to barf on missing file");
            is.close();
        } catch (FileNotFoundException ex) { }
    } 

}
